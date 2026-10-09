package com.budgetmanager.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Gère un pool de connexions HikariCP vers la base de données MySQL.
 *
 * Pourquoi HikariCP et non un simple singleton Connection ?
 * - Une seule connexion statique peut se fermer silencieusement après un
 *   délai réseau ou une coupure MySQL ; HikariCP détecte et remplace les
 *   connexions mortes automatiquement.
 * - Les opérations async (Task) lancées sur des threads non-UI peuvent
 *   toutes obtenir leur propre connexion sans se bloquer mutuellement.
 * - Les DAO utilisent try-with-resources : chaque connexion est libérée
 *   dans le pool dès que la méthode DAO se termine, aucune fuite possible.
 *
 * Configuration lue (par ordre de priorité) :
 *   1. Variables d'environnement BUDGET_DB_URL / BUDGET_DB_USER / BUDGET_DB_PASSWORD
 *   2. Fichier database.properties à la racine (db.url / db.user / db.password)
 *   3. Valeurs par défaut WampServer (root / vide / localhost)
 */
public class DatabaseConnection {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);

    private static final String URL_DEFAUT =
            "jdbc:mysql://127.0.0.1:3306/gestionnaire_budget?useSSL=false&serverTimezone=UTC&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true";
    private static final String USER_DEFAUT   = "root";
    private static final String PASSWORD_DEFAUT = "";

    private static final Properties CONFIG = charger();
    private static volatile HikariDataSource pool;

    private DatabaseConnection() {}

    // ---------------------------------------------------------------------------
    // Lecture de la configuration (inchangée par rapport à l'ancienne version)
    // ---------------------------------------------------------------------------

    private static Properties charger() {
        Properties p = new Properties();
        java.io.File fichier = new java.io.File("database.properties");
        if (fichier.isFile()) {
            try (java.io.InputStream in = new java.io.FileInputStream(fichier)) {
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                log.warn("Lecture de database.properties impossible : {}", e.getMessage());
            }
        }
        return p;
    }

    private static String valeur(String variableEnv, String cle, String defaut) {
        String env = System.getenv(variableEnv);
        if (env != null && !env.isEmpty()) return env;
        return CONFIG.getProperty(cle, defaut);
    }

    // ---------------------------------------------------------------------------
    // Pool HikariCP (initialisation paresseuse, thread-safe)
    // ---------------------------------------------------------------------------

    private static HikariDataSource creerPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(valeur("BUDGET_DB_URL",      "db.url",      URL_DEFAUT));
        config.setUsername(valeur("BUDGET_DB_USER",    "db.user",     USER_DEFAUT));
        config.setPassword(valeur("BUDGET_DB_PASSWORD","db.password", PASSWORD_DEFAUT));

        // Taille du pool : 2 connexions minimum, 10 maximum.
        // Pour une application Desktop mono-utilisateur c'est largement suffisant
        // tout en n'épuisant pas les ressources du serveur MySQL.
        config.setMinimumIdle(2);
        config.setMaximumPoolSize(10);

        // Délai d'expiration d'une connexion inactive avant qu'elle soit fermée (ms).
        config.setIdleTimeout(60_000);

        // Durée de vie maximale d'une connexion dans le pool (ms).
        // Doit être inférieure au wait_timeout de MySQL (par défaut 8 h = 28 800 s).
        config.setMaxLifetime(1_800_000); // 30 minutes

        // Délai maximal pour obtenir une connexion du pool avant de lever une exception.
        config.setConnectionTimeout(10_000);

        // Requête envoyée à MySQL pour vérifier qu'une connexion est toujours vivante.
        config.setConnectionTestQuery("SELECT 1");

        config.setPoolName("BudgetPool");
        return new HikariDataSource(config);
    }

    /**
     * Retourne une connexion du pool.
     * À utiliser exclusivement dans un bloc try-with-resources :
     * <pre>
     *   try (Connection conn = DatabaseConnection.getConnection()) { ... }
     * </pre>
     * La connexion est automatiquement rendue au pool à la sortie du bloc.
     */
    public static Connection getConnection() throws SQLException {
        if (pool == null) {
            synchronized (DatabaseConnection.class) {
                if (pool == null) {
                    pool = creerPool();
                }
            }
        }
        return pool.getConnection();
    }

    /**
     * Ferme le pool proprement à l'arrêt de l'application (appelé depuis Main.stop()).
     * Sans effet si le pool n'a jamais été initialisé.
     */
    public static void closeConnection() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }
}
