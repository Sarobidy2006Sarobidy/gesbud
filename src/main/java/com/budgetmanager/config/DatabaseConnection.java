package com.budgetmanager.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
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
 *   2. Fichier database.properties à la racine (db.url / db.user / db.password) ;
 *      chaque valeur peut être chiffrée (préfixe « aes: », voir {@link ConfigCrypto})
 *   3. Valeurs par défaut WampServer (root / vide / localhost)
 */
public class DatabaseConnection {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);

    private static final String URL_DEFAUT =
            "jdbc:mysql://127.0.0.1:3306/gestionnaire_budget?useSSL=false&serverTimezone=UTC&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true";
    private static final String USER_DEFAUT   = "root";
    private static final String PASSWORD_DEFAUT = "";

    /** Délai maximum d'une requête SQL avant annulation automatique (secondes). */
    public static final int TIMEOUT_REQUETE = 3;

    /** Délai des requêtes de migration (ALTER/CREATE), légitimement plus lentes. */
    public static final int TIMEOUT_MIGRATION = 30;

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
        // Les valeurs « aes:... » sont déchiffrées ici, en mémoire, à l'init du pool.
        return ConfigCrypto.dechiffrer(CONFIG.getProperty(cle, defaut));
    }

    // ---------------------------------------------------------------------------
    // Pool HikariCP (initialisation paresseuse, thread-safe)
    // ---------------------------------------------------------------------------

    private static HikariDataSource creerPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(avecTimeoutsReseau(valeur("BUDGET_DB_URL", "db.url", URL_DEFAUT)));
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

    // ---------------------------------------------------------------------------
    // Préparation des requêtes avec délai d'expiration
    // ---------------------------------------------------------------------------

    /**
     * Prépare une requête annulée automatiquement après
     * {@value #TIMEOUT_REQUETE} secondes. Si MySQL ne répond pas (XAMPP
     * planté, coupure réseau en pleine lecture socket), le pilote lève une
     * SQLException propre au lieu de bloquer le thread db-worker à l'infini —
     * la Task correspondante échoue et l'utilisateur reçoit une erreur claire.
     */
    public static PreparedStatement preparer(Connection conn, String sql) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setQueryTimeout(TIMEOUT_REQUETE);
        return stmt;
    }

    /** Comme {@link #preparer}, mais l'INSERT retourne aussi sa clé générée. */
    public static PreparedStatement preparerAvecCleAuto(Connection conn, String sql) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        stmt.setQueryTimeout(TIMEOUT_REQUETE);
        return stmt;
    }

    /**
     * Statement brut avec délai d'expiration personnalisé. Réservé aux
     * migrations (ALTER TABLE, CREATE) qui peuvent dépasser les 3 secondes du
     * timeout standard sur une grosse base.
     */
    public static Statement creerStatement(Connection conn, int timeoutSecondes) throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.setQueryTimeout(timeoutSecondes);
        return stmt;
    }

    /**
     * Garantit que toute URL MySQL porte ses délais réseau : sans
     * socketTimeout, un serveur MySQL mort en pleine lecture peut laisser le
     * pilote JDBC bloqué sur le socket pour toujours — un thread perdu à
     * chaque clic, jusqu'au plantage de la JVM. Le socketTimeout (10 s,
     * filet de sécurité) reste supérieur au timeout de requête (3 s) pour que
     * les erreurs normales viennent de setQueryTimeout avec un message clair.
     */
    private static String avecTimeoutsReseau(String url) {
        if (!url.startsWith("jdbc:mysql")) return url;
        StringBuilder u = new StringBuilder(url);
        if (!url.contains("connectTimeout=")) {
            u.append(u.indexOf("?") >= 0 ? "&" : "?").append("connectTimeout=5000");
        }
        if (!url.contains("socketTimeout=")) {
            u.append(u.indexOf("?") >= 0 ? "&" : "?").append("socketTimeout=10000");
        }
        return u.toString();
    }

    /**
     * Ferme le pool et l'oublie : le prochain {@link #getConnection()} en recrée
     * un neuf. Sans la remise à null, HikariCP resterait fermé pour toute la vie
     * du processus et le bouton « Réessayer » de l'écran d'erreur ne pourrait
     * jamais se reconnecter après une panne MySQL.
     * Est aussi appelé depuis Main.stop() à l'arrêt de l'application.
     */
    public static void closeConnection() {
        synchronized (DatabaseConnection.class) {
            if (pool != null) {
                pool.close();
                pool = null;
            }
        }
    }
}
