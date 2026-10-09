package com.budgetmanager.util;

import com.budgetmanager.Main;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Boîtes de dialogue de l'application, habillées avec le thème bleu
 * (feuille de style, icône dessinée en JavaFX, boutons en français).
 */
public class AlertUtil {

    public static void afficherErreur(String titre, String message) {
        afficher(AlertType.ERROR, titre, message, "dialog-error",
                Icons.alertCircle(22, Color.web("#d92d20")));
    }

    public static void afficherSucces(String titre, String message) {
        afficher(AlertType.INFORMATION, titre, message, "dialog-success",
                Icons.checkCircle(22, Color.web("#12805c")));
    }

    public static boolean confirmer(String titre, String message) {
        Alert alert = new Alert(AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        habiller(alert, titre, "dialog-confirm", Icons.helpCircle(22, Color.web("#1565c0")));

        DialogPane pane = alert.getDialogPane();
        Button ok = (Button) pane.lookupButton(ButtonType.OK);
        ok.setText("Confirmer");
        ok.getStyleClass().add("button-primary");
        Button annuler = (Button) pane.lookupButton(ButtonType.CANCEL);
        annuler.setText("Annuler");
        annuler.getStyleClass().add("button-secondary");

        return alert.showAndWait()
                .filter(bouton -> bouton.getButtonData().isDefaultButton())
                .isPresent();
    }

    private static void afficher(AlertType type, String titre, String message, String classe, Node icone) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        habiller(alert, titre, classe, icone);
        Button ok = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
        ok.setText("Compris");
        ok.getStyleClass().add("button-primary");
        alert.showAndWait();
    }

    private static void habiller(Alert alert, String titre, String classe, Node icone) {
        alert.setTitle(titre);
        alert.setHeaderText(titre);

        StackPane pastille = new StackPane(icone);
        pastille.getStyleClass().addAll("dialog-icon", classe + "-icon");
        alert.setGraphic(pastille);

        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(AlertUtil.class.getResource("/com/budgetmanager/css/style.css").toExternalForm());
        pane.getStyleClass().addAll("app-dialog", classe);

        // Bloquer l'application principale tant que la boîte de dialogue est ouverte.
        // Empêche l'utilisateur de cliquer sur les boutons pendant qu'une alerte est affichée.
        if (Main.getStagePrincipal() != null) {
            alert.initOwner(Main.getStagePrincipal());
            alert.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        }
    }
}
