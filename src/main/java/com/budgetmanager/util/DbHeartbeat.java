package com.budgetmanager.util;

import com.budgetmanager.Main;
import com.budgetmanager.config.DatabaseConnection;
import com.budgetmanager.controller.DatabaseErrorController;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Vérifie que la connexion MySQL est toujours active avant une transaction.
 * En cas d'échec, redirige vers l'écran d'erreur DB sur l'UI thread.
 */
public final class DbHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(DbHeartbeat.class);

    private DbHeartbeat() {}

    /**
     * Teste la connexion MySQL.
     * @return true si la base répond, false sinon (et redirige vers database-error.fxml).
     */
    public static boolean ok() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            String message = extraireMessagePublic(e);
            Platform.runLater(() -> afficherEcranErreur(message));
            return false;
        }
    }

    /**
     * Version silencieuse : teste sans rediriger.
     */
    public static boolean silencieux() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /** Extrait un message lisible depuis une SQLException MySQL. Public pour Main.java. */
    public static String extraireMessagePublic(SQLException e) {
        return extraireMessage(e);
    }

    private static void afficherEcranErreur(String message) {
        try {
            String url = System.getProperty("budget.db.url.tentee", "");
            DatabaseErrorController.definirErreur(message, url);
            Main.changerScene("view/database-error.fxml", Main.LARGEUR_AUTH, Main.HAUTEUR_AUTH);
        } catch (Exception ex) {
            log.error("DbHeartbeat — impossible d'afficher l'écran d'erreur", ex);
        }
    }

    private static String extraireMessage(SQLException e) {
        String msg = e.getMessage();
        if (msg == null) return "Connexion MySQL perdue.";
        if (msg.contains("Communications link failure") || msg.contains("Connection refused")) {
            return "MySQL ne répond pas. Vérifiez que XAMPP/WampServer est démarré.";
        }
        if (msg.contains("Access denied")) {
            return "Accès refusé : vérifiez le nom d'utilisateur et le mot de passe dans database.properties.";
        }
        if (msg.contains("Unknown database")) {
            return "La base « gestionnaire_budget » n'existe pas. Exécutez le script schema.sql.";
        }
        return msg.length() > 120 ? msg.substring(0, 120) + "…" : msg;
    }
}
