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

        // Le message brut du pilote n'est pas parlant pour un non-initié : on le traduit,
        // en gardant le texte d'origine dans l'infobulle pour pouvoir le reporter.
        String brut = messageErreur.isEmpty()
                ? "L'application n'a pas pu joindre votre base de données. Le plus souvent, "
                  + "c'est MySQL qui n'est pas encore démarré."
                : messageErreur;
        String simple = motsSimples(brut);
        labelDetail.setText(simple);
        if (!simple.equals(brut)) {
            labelDetail.setTooltip(new javafx.scene.control.Tooltip(brut));
        }

        // Le numéro dont l'utilisateur a vraiment besoin : celui que XAMPP affiche
        // à côté de MySQL. Le dire en une phrase, sans jargon de configuration.
        String port = extrairePort(urlTentee);
        labelPort.setText("L'application cherche la base sur le port " + port + ". "
                + "XAMPP et WampServer utilisent souvent le port 3307 à la place du 3306 : "
                + "regardez le numéro affiché à côté de « MySQL » dans leur panneau.");

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
        afficherStatut("Je retente la connexion…");
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
                    afficherStatut("Connecté, mais la page de connexion ne s'ouvre pas. "
                            + "Redémarrez l'application.");
                    boutonReessayer.setDisable(false);
                }
            } else {
                afficherStatut("Toujours rien. Démarrez MySQL dans XAMPP ou WampServer, "
                        + "puis cliquez de nouveau sur « Réessayer ».");
                boutonReessayer.setDisable(false);
            }
        });

        tache.setOnFailed(evt -> {
            afficherStatut("La tentative n'a pas abouti. Attendez une minute et réessayez.");
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

    /** Traduit les erreurs JDBC les plus courantes en français ; le reste passe tel quel. */
    private static String motsSimples(String brut) {
        String e = brut.toLowerCase();
        if (e.contains("communications link failure") || e.contains("connection refused")
                || e.contains("could not connect") || e.contains("connect timed out")
                || e.contains("no data received")) {
            return "MySQL ne répond pas. Ouvrez XAMPP ou WampServer et cliquez sur « Start » en face de MySQL.";
        }
        if (e.contains("unknown database")) {
            return "Le serveur répond, mais la base que l'application cherche n'existe pas encore sur ce serveur.";
        }
        if (e.contains("access denied")) {
            return "Le serveur a refusé ce utilisateur ou ce mot de passe. Vérifiez db.user et db.password "
                    + "dans le fichier database.properties.";
        }
        if (e.contains("no suitable driver")) {
            return "Le pilote MySQL manque à l'application : elle ne peut pas parler au serveur.";
        }
        return brut;
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
