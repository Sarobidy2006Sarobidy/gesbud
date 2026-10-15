package com.budgetmanager;

import com.budgetmanager.controller.AuthHeroController;
import com.budgetmanager.dao.UserDAO;
import com.budgetmanager.util.Hexagon;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.RaccourcisClavier;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;

/**
 * Point d'entrée de l'application "Gestionnaire de Budget Personnel".
 */
public class Main extends Application {

    private static Stage stagePrincipal;
    /** Dernière taille demandée : sert à savoir si l'on reste dans la même « famille » d'écrans. */
    private static int largeurDemandee, hauteurDemandee;

    /** Taille commune des écrans d'authentification (connexion, inscription, mot de passe oublié). */
    public static final int LARGEUR_AUTH = 1040;
    public static final int HAUTEUR_AUTH = 720;

    /** Taille commune des écrans internes, choisis pour laisser la barre latérale respirer. */
    public static final int LARGEUR_APP = 1220;
    public static final int HAUTEUR_APP = 760;

    private static final String DOSSIER_POLICES = "fonts/";
    private static final String[] POLICES_A_CHARGER = {
            "Poppins-Regular.ttf",
            "Poppins-Medium.ttf",
            "Poppins-Bold.ttf",
            "Inter-Regular.otf",
            "Inter-Medium.otf",
            "Inter-SemiBold.otf"
    };

    /** Clé sous laquelle Main range le contrôleur d'une vue dans les properties de sa racine. */
    private static final String CLE_CONTROLEUR = "budget.controleur";

    /** Tailles d'icône demandées : la barre des tâches et la barre de titre prennent celle qui colle. */
    private static final double[] TAILLES_ICONE_FENETRE = {16, 24, 32, 48, 128};

    /**
     * Icône de la fenêtre : le badge hexagonal du logo, rasterisé à la volée.
     * L'application ne contient aucun fichier image, elle dessine tout ; l'icône
     * suit la même règle. Plusieurs tailles sont fournies pour que Windows choisisse
     * un rendu net au lieu d'étirer une seule image sur le quart de sa taille.
     */
    private static void definirIcone(Stage stage) {
        for (double taille : TAILLES_ICONE_FENETRE) {
            stage.getIcons().add(rasteriser(Hexagon.badge(taille, Icons::wallet, false), taille));
        }
    }

    /**
     * Rasterise un noeud hors scène, puis le recopie centré dans une image carrée.
     * Le hexagone déborde de son gabarit à cause de son contour : une icône plus
     * haute que large est refusée par Windows, qui retombe alors sur l'icône
     * JavaFX par défaut sans prévenir.
     */
    private static Image rasteriser(Node badge, double taille) {
        Region zone = (Region) badge;
        zone.setPrefSize(taille, taille);
        zone.applyCss();
        zone.layout();
        Image brut = zone.snapshot(null, null);
        int cote = (int) Math.ceil(Math.max(brut.getWidth(), brut.getHeight()));
        WritableImage carree = new WritableImage(cote, cote);
        PixelReader lecteur = brut.getPixelReader();
        PixelWriter scribeur = carree.getPixelWriter();
        int decalageX = (cote - (int) brut.getWidth()) / 2;
        int decalageY = (cote - (int) brut.getHeight()) / 2;
        for (int x = 0; x < brut.getWidth(); x++) {
            for (int y = 0; y < brut.getHeight(); y++) {
                scribeur.setArgb(x + decalageX, y + decalageY, lecteur.getArgb(x, y));
            }
        }
        return carree;
    }

    @Override
    public void stop() {
        // Attendre que les tâches DB en cours se terminent avant de fermer le pool.
        com.budgetmanager.util.DbExecutor.arreter();
        com.budgetmanager.config.DatabaseConnection.closeConnection();
    }

    @Override
    public void start(Stage stage) throws IOException {
        chargerPolices();

        stagePrincipal = stage;
        stage.setTitle("Gestionnaire de Budget Personnel");
        definirIcone(stage);
        stage.setMinWidth(920);
        stage.setMinHeight(640);
        // Fenêtre maximisée : elle couvre l'écran de travail tout en gardant la barre
        // de titre natif (réduire / agrandir / fermer). Le plein écran JavaFX avait été
        // essayé pour « 100 % de l'écran » mais supprime cette barre, donc retiré.
        stage.setMaximized(true);

        // Tester la connexion DB AVANT de charger login.fxml.
        // Si MySQL est éteint, on affiche database-error.fxml avec un bouton "Réessayer".
        boolean dbOk = false;
        String erreurDb = null;
        // L'URL configurée d'abord : si la connexion échoue avant d'être établie,
        // l'écran d'erreur a quand même le bon port à montrer.
        String urlDb = com.budgetmanager.config.DatabaseConnection.urlTentee();
        try (java.sql.Connection conn = com.budgetmanager.config.DatabaseConnection.getConnection()) {
            dbOk = conn.isValid(3);
            urlDb = conn.getMetaData().getURL();
            System.setProperty("budget.db.url.tentee", urlDb);
        } catch (java.sql.SQLException e) {
            erreurDb = com.budgetmanager.util.DbHeartbeat.extraireMessagePublic(e);
        } catch (RuntimeException e) {
            // Ex. clé de déchiffrement absente (ConfigCrypto) ou pool HikariCP
            // impossible à initialiser : message clair plutôt que plantage brut.
            erreurDb = e.getMessage() != null ? e.getMessage() : e.toString();
        }

        if (!dbOk) {
            com.budgetmanager.controller.DatabaseErrorController.definirErreur(erreurDb, urlDb);
            changerScene("view/database-error.fxml", LARGEUR_AUTH, HAUTEUR_AUTH);
            stage.show();
            return;
        }

        new UserDAO().migrerSiNecessaire();
        new com.budgetmanager.dao.IncomeDAO().migrerSiNecessaire();
        new com.budgetmanager.dao.ExpenseDAO().migrerSiNecessaire();
        new com.budgetmanager.dao.SavingDAO().migrerSiNecessaire();
        new com.budgetmanager.dao.CategoryDAO().migrerSiNecessaire();
        new com.budgetmanager.dao.CarnetDAO().migrerSiNecessaire();

        changerScene("view/login.fxml", LARGEUR_AUTH, HAUTEUR_AUTH);

        // Suspension des animations quand la fenêtre est minimisée — zéro CPU inutile.
        // On suspend (pause) au lieu d'arrêter : les badges reprennent exactement où
        // ils en étaient quand l'utilisateur restaure la fenêtre.
        stage.iconifiedProperty().addListener((obs, ancien, minimise) -> {
            if (minimise) {
                AuthHeroController.suspendreAnimations();
            } else {
                AuthHeroController.reprendreAnimations();
            }
        });

        // Intercepter la fermeture : bloquer si une tâche DB est en cours.
        stage.setOnCloseRequest(event -> {
            if (com.budgetmanager.util.DbExecutor.aTachesActives()) {
                event.consume(); // Empêche la fermeture immédiate.
                javafx.scene.control.Alert alerte = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION);
                alerte.setTitle("Enregistrement en cours");
                alerte.setHeaderText("Veuillez patienter");
                alerte.setContentText("Une opération est en cours d'enregistrement dans la base de données.\nL'application se fermera automatiquement dès qu'elle sera terminée.");
                alerte.initOwner(stage);
                alerte.show();
                // Surveille la fin des tâches pour fermer automatiquement.
                new Thread(() -> {
                    while (com.budgetmanager.util.DbExecutor.aTachesActives()) {
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    javafx.application.Platform.runLater(() -> stage.fireEvent(
                            new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
                }, "close-watcher").start();
            }
        });

        stage.show();
    }

    private void chargerPolices() {
        for (String fichier : POLICES_A_CHARGER) {
            Font.loadFont(Main.class.getResourceAsStream(DOSSIER_POLICES + fichier), 12);
        }
    }

    private static final Duration DUREE_FONDU = Duration.millis(220);

    /**
     * Change la scène affichée en chargeant un nouveau fichier FXML.
     *
     * <p><b>Lazy Loading (Point 6)</b> : chaque vue n'est chargée qu'au moment
     * où l'utilisateur navigue vers elle. Aucune vue n'est pré-chargée au
     * démarrage — le login s'affiche en moins de 200ms.</p>
     *
     * <p>Un {@code setControllerFactory} est installé pour l'injection propre.</p>
     * <p>La nouvelle page apparaît en fondu (opacité 0 → 1).</p>
     */
    public static void changerScene(String fxml, int largeur, int hauteur) throws IOException {
        AuthHeroController.arreterAnimations();

        // Cache des vues : chaque vue principale est chargée une seule fois.
        // Les vues auth (login, register...) sont toujours rechargées fraîches.
        final boolean depuisCache = com.budgetmanager.util.ViewCache.estEnCache(fxml);
        Parent racine;

        if (depuisCache) {
            // Vue déjà chargée : récupération instantanée — zéro fuite mémoire.
            racine = com.budgetmanager.util.ViewCache.obtenir(fxml, Main.class);
            detacherDeSonAncienneScene(racine);
            rafraichirSiCache(racine);
        } else {
            // Première visite : chargement avec factory d'injection.
            FXMLLoader loader = new FXMLLoader(Main.class.getResource(fxml));
            loader.setControllerFactory(controllerClass -> {
                try {
                    return controllerClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Impossible d'instancier le contrôleur : "
                            + controllerClass.getName(), e);
                }
            });
            racine = loader.load();
            // Le contrôleur voyage avec sa racine : les accélérateurs clavier
            // peuvent ainsi le retrouver, même quand la vue revient du cache.
            racine.getProperties().put(CLE_CONTROLEUR, loader.getController());
            com.budgetmanager.util.ViewCache.mettreEnCache(fxml, racine);
        }

        // Fondu seulement pour les nouvelles vues — les vues en cache sont déjà opaques.
        if (!depuisCache) {
            racine.setOpacity(0);
        }

        Scene ancienne = stagePrincipal.getScene();
        boolean conserver = ancienne != null && stagePrincipal.isShowing()
                && largeur == largeurDemandee && hauteur == hauteurDemandee;
        Scene scene = conserver
                ? new Scene(racine, ancienne.getWidth(), ancienne.getHeight())
                : new Scene(racine, largeur, hauteur);

        // Chargement CSS via getResource() — chemin relatif au package, fonctionne
        // aussi bien en développement qu'une fois packagé en .jar.
        scene.getStylesheets().add(Main.class.getResource("css/style.css").toExternalForm());

        installerRaccourcis(scene);
        stagePrincipal.setScene(scene);
        if (!conserver && !stagePrincipal.isMaximized()) {
            stagePrincipal.sizeToScene();
            stagePrincipal.centerOnScreen();
        }
        largeurDemandee = largeur;
        hauteurDemandee = hauteur;

        FadeTransition fondu = new FadeTransition(DUREE_FONDU, racine);
        fondu.setFromValue(0);
        fondu.setToValue(1);
        fondu.play();

        // Seule une page inédite se joue en cascade ; un retour depuis le cache
        // doit être instantané, sans re-jouer l'entrée.
        if (!depuisCache) {
            com.budgetmanager.util.AnimationEntree.enCascade(racine);
        }
    }

    /**
     * Une vue rendue du cache est encore déclarée racine de la Scene qui l'a portée :
     * JavaFX refuse de la repartager (IllegalArgumentException « already set as root of
     * another scene »), ce qui rendait les clics de navigation sans effet. On la détache
     * de son ancienne Scene avant de la réafficher.
     */
    private static void detacherDeSonAncienneScene(Parent racine) {
        if (racine == null) return;
        Scene ancienne = racine.getScene();
        if (ancienne != null && ancienne.getRoot() == racine) {
            ancienne.setRoot(new javafx.scene.layout.Pane());
        }
    }

    /**
     * Une vue qui revient du cache ne repasse pas par {@code initialize()} :
     * on redemande ses données aux contrôleurs qui le proposent, sinon elles
     * resteraient celles de la première visite.
     */
    private static void rafraichirSiCache(Parent racine) {
        if (racine.getProperties().get(CLE_CONTROLEUR)
                instanceof com.budgetmanager.util.Rafraichissable rafraichissable) {
            rafraichissable.rafraichirDonnees();
        }
    }

    public static Stage getStagePrincipal() {
        return stagePrincipal;
    }

    /**
     * Installe les raccourcis globaux (Ctrl+N ajouter, Ctrl+S enregistrer) via
     * l'API officielle des accélérateurs, rattachés à la Scene — jamais via des
     * écouteurs clavier posés sur les composants. SHORTCUT_DOWN exige le
     * modificateur exact (Ctrl sous Windows, Cmd sous Mac) : la saisie des
     * lettres et des accents dans les TextField n'est jamais interceptée.
     * Chaque nouvelle Scene repart de zéro : aucun conflit entre écrans.
     */
    private static void installerRaccourcis(Scene scene) {
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
                () -> actionRaccourci(scene, RaccourcisClavier::auRaccourciAjouter));
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN),
                () -> actionRaccourci(scene, RaccourcisClavier::auRaccourciEnregistrer));
    }

    /** Exécute l'action si — et seulement si — le contrôleur de la vue l'implémente. */
    private static void actionRaccourci(Scene scene, java.util.function.Consumer<RaccourcisClavier> action) {
        Object controleur = scene.getRoot().getProperties().get(CLE_CONTROLEUR);
        if (controleur instanceof RaccourcisClavier raccourcis) {
            action.accept(raccourcis);
        }
    }

    /**
     * Mode de secours graphique : si les pilotes de la carte graphique font
     * crasher Prism (écran noir, démarrage impossible), forcer le rendu
     * logiciel (CPU). Activation au choix :
     * <ul>
     *   <li>argument de lancement : {@code --rendu-logiciel}</li>
     *   <li>propriété système : {@code -Dbudget.rendu=logiciel}</li>
     *   <li>variable d'environnement : {@code BUDGET_RENDU=LOGICIEL}</li>
     * </ul>
     * Les propriétés Prism doivent être posées AVANT launch() : c'est le
     * démarrage du toolkit JavaFX qui choisit le pipeline (DirectX/OpenGL/sw).
     */
    public static void main(String[] args) {
        String rendu = System.getProperty("budget.rendu",
                System.getenv().getOrDefault("BUDGET_RENDU", ""));
        boolean renduLogiciel = "logiciel".equalsIgnoreCase(rendu.trim());
        for (String arg : args) {
            if ("--rendu-logiciel".equalsIgnoreCase(arg)) renduLogiciel = true;
        }
        if (renduLogiciel) {
            System.setProperty("prism.order", "sw");
            System.setProperty("prism.verbose", "true");
        }
        launch(args);
    }
}
