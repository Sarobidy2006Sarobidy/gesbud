package com.budgetmanager;

import com.budgetmanager.controller.AuthHeroController;
import com.budgetmanager.dao.UserDAO;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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

    private static final String DOSSIER_POLICES = "fonts/";
    private static final String[] POLICES_A_CHARGER = {
            "Poppins-Regular.ttf",
            "Poppins-Medium.ttf",
            "Poppins-Bold.ttf",
            "Inter-Regular.otf",
            "Inter-Medium.otf",
            "Inter-SemiBold.otf"
    };

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
        stage.setMinWidth(920);
        stage.setMinHeight(640);

        // Tester la connexion DB AVANT de charger login.fxml.
        // Si MySQL est éteint, on affiche database-error.fxml avec un bouton "Réessayer".
        boolean dbOk = false;
        String erreurDb = null;
        String urlDb = "";
        try (java.sql.Connection conn = com.budgetmanager.config.DatabaseConnection.getConnection()) {
            dbOk = conn.isValid(3);
            urlDb = conn.getMetaData().getURL();
            System.setProperty("budget.db.url.tentee", urlDb);
        } catch (java.sql.SQLException e) {
            erreurDb = com.budgetmanager.util.DbHeartbeat.extraireMessagePublic(e);
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

        changerScene("view/login.fxml", LARGEUR_AUTH, HAUTEUR_AUTH);

        // Suspension des animations quand la fenêtre est minimisée — zéro CPU inutile.
        stage.iconifiedProperty().addListener((obs, ancien, minimise) -> {
            if (minimise) {
                AuthHeroController.arreterAnimations();
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

        stagePrincipal.setScene(scene);
        if (!conserver && !stagePrincipal.isMaximized() && !stagePrincipal.isFullScreen()) {
            stagePrincipal.sizeToScene();
            stagePrincipal.centerOnScreen();
        }
        largeurDemandee = largeur;
        hauteurDemandee = hauteur;

        FadeTransition fondu = new FadeTransition(DUREE_FONDU, racine);
        fondu.setFromValue(0);
        fondu.setToValue(1);
        fondu.play();
    }

    public static Stage getStagePrincipal() {
        return stagePrincipal;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
