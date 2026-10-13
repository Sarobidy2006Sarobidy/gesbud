package com.budgetmanager.controller;

import com.budgetmanager.Main;
import com.budgetmanager.config.DatabaseConnection;
import com.budgetmanager.util.Icons;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.io.IOException;

/**
 * Écran affiché quand MySQL (XAMPP/WampServer) est inaccessible au démarrage
 * ou pendant l'utilisation.
 *
 * L'utilisateur voit un message clair avec :
 * - L'URL tentée et le port configuré
 * - Un bouton "Réessayer" qui retente la connexion en arrière-plan
 * - Un bouton "Quitter"
 */
public class DatabaseErrorController {

    @FXML private Label labelTitre;
    @FXML private Label labelDetail;
    @FXML private Label labelPort;
    @FXML private Label labelStatut;
    @FXML private Button boutonReessayer;
    @FXML private Button boutonQuitter;
    @FXML private StackPane iconeErreur;

    /** Message d'erreur technique transmis depuis Main ou un DAO. */
    private static String messageErreur = "";
    /** URL tentée, pour aider l'utilisateur à identifier le bon port. */
    private static String urlTentee    = "";

    public static void definirErreur(String message, String url) {
        messageErreur = message != null ? message : "";
        urlTentee     = url     != null ? url     : "";
    }

    @FXML
    public void initialize() {
        iconeErreur.getChildren().add(Icons.alertCircle(38, Color.web("#ff8080")));

        labelDetail.setText(messageErreur.isEmpty()
                ? "MySQL ne répond pas. Vérifiez que XAMPP/WampServer est démarré."
                : messageErreur);

        // Extraire et afficher le port depuis l'URL pour guider l'utilisateur
        String port = extrairePort(urlTentee);
        labelPort.setText("Port configuré : " + port
                + " — si XAMPP utilise le port 3307, modifiez database.properties");

        boutonReessayer.setGraphic(Icons.rotateCcw(15, Color.WHITE));
        boutonQuitter.setGraphic(Icons.logOut(15, Color.web("#ff9c9c")));
    }

    /** Ligne d'état sous le guide : masquée tant qu'elle est vide. */
    private void afficherStatut(String texte) {
        labelStatut.setText(texte);
        labelStatut.setVisible(!texte.isEmpty());
        labelStatut.setManaged(!texte.isEmpty());
    }

    @FXML
    public void reessayer() {
        afficherStatut("Tentative de connexion…");
        boutonReessayer.setDisable(true);

        Task<Boolean> tache = new Task<>() {
            @Override
            protected Boolean call() {
                try {
                    // Force la réinitialisation du pool et teste une connexion réelle.
                    DatabaseConnection.closeConnection();
                    try (var conn = DatabaseConnection.getConnection()) {
                        return conn.isValid(3);
                    }
                } catch (Exception e) {
                    return false;
                }
            }
        };

        tache.setOnSucceeded(evt -> {
            if (tache.getValue()) {
                // Connexion rétablie : charger l'application normalement.
                try {
                    Main.changerScene("view/login.fxml", Main.LARGEUR_AUTH, Main.HAUTEUR_AUTH);
                } catch (IOException e) {
                    afficherStatut("Erreur inattendue : " + e.getMessage());
                    boutonReessayer.setDisable(false);
                }
            } else {
                afficherStatut("Toujours inaccessible. Vérifiez que MySQL est bien démarré.");
                boutonReessayer.setDisable(false);
            }
        });

        tache.setOnFailed(evt -> {
            afficherStatut("Échec de la tentative. Vérifiez votre configuration.");
            boutonReessayer.setDisable(false);
        });

        Thread t = new Thread(tache);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void quitter() {
        Platform.exit();
    }

    private static String extrairePort(String url) {
        // Extrait le port depuis jdbc:mysql://localhost:3306/...
        try {
            int debut = url.lastIndexOf(':') + 1;
            int fin   = url.indexOf('/', debut);
            if (debut > 0 && fin > debut) return url.substring(debut, fin);
        } catch (Exception ignored) {}
        return "3306 (défaut)";
    }
}
