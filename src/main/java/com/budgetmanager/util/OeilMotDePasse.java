package com.budgetmanager.util;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

/**
 * Bouton « œil » (afficher / masquer le mot de passe) posé sur un PasswordField.
 *
 * <p>Un {@code PasswordField} ne sait pas afficher son texte en clair : on installe
 * donc un {@code TextField} jumeau — lié au même texte — dans un {@code StackPane},
 * et on n'en montre un seul des deux à la fois. L'œil ouvert signale le mot de passe
 * visible, l'œil barré signale le masque.</p>
 *
 * <p>Les contrôleurs ignorent ce mécanisme : ils continuent de lire
 * {@code champMotDePasse.getText()}, le champ masqué conservant toujours le texte.</p>
 */
public final class OeilMotDePasse {

    private static final Color COULEUR_THEME_SOMBRE = Color.web("#96b9e6");
    private static final Color COULEUR_THEME_CLAIR = Color.web("#9bb0d3");
    private static final double TAILLE_ICONE = 17;

    private OeilMotDePasse() {}

    /** Ajoute le bouton œil à un champ de mot de passe déjà inséré dans sa contenance. */
    public static void installer(PasswordField champ) {
        if (!(champ.getParent() instanceof Pane parent)) return;

        champ.getStyleClass().add("champ-avec-oeil");

        TextField champClair = new TextField();
        // Reprise des classes de style du champ, sans celles imposées par JavaFX
        // (« password-field » notamment), que le TextField jumeau possède déjà.
        for (String classe : champ.getStyleClass()) {
            if (!classe.equals("text-input") && !classe.equals("text-field")
                    && !classe.equals("password-field")) {
                champClair.getStyleClass().add(classe);
            }
        }
        champClair.setPromptText(champ.getPromptText());
        champClair.textProperty().bindBidirectional(champ.textProperty());
        // Entrée validée en mode visible : déclenche le même handler qu'en mode masqué.
        champClair.setOnAction(evenement -> {
            EventHandler<ActionEvent> action = champ.getOnAction();
            if (action != null) action.handle(evenement);
        });
        champClair.setVisible(false);
        champClair.setManaged(false);
        champ.setMaxWidth(Double.MAX_VALUE);
        champClair.setMaxWidth(Double.MAX_VALUE);

        Color couleur = champ.getStyleClass().contains("auth-textfield-premium")
                ? COULEUR_THEME_SOMBRE : COULEUR_THEME_CLAIR;

        Label oeil = new Label();
        oeil.getStyleClass().add("oeil-mot-de-passe");
        oeil.setFocusTraversable(false);
        oeil.setPadding(new Insets(0, 12, 0, 0));
        oeil.setGraphic(Icons.eyeOff(TAILLE_ICONE, couleur));
        oeil.setTooltip(new Tooltip("Afficher le mot de passe"));
        oeil.setOnMouseClicked(evenement -> basculer(champ, champClair, oeil, couleur));

        // Le champ quitte son parent avant d'être remplacé par le conteneur, sinon
        // JavaFX refuserait de le rattacher (un nœud n'a qu'un seul parent).
        int index = parent.getChildren().indexOf(champ);
        Priority croissance = HBox.getHgrow(champ);
        parent.getChildren().remove(champ);

        StackPane conteneur = new StackPane();
        conteneur.setAlignment(Pos.CENTER_LEFT);
        conteneur.getChildren().addAll(champ, champClair, oeil);
        StackPane.setAlignment(oeil, Pos.CENTER_RIGHT);
        HBox.setHgrow(conteneur, croissance);

        parent.getChildren().add(index, conteneur);
    }

    private static void basculer(PasswordField champ, TextField champClair, Label oeil, Color couleur) {
        boolean versClair = !champClair.isVisible();

        champ.setVisible(!versClair);
        champ.setManaged(!versClair);
        champClair.setVisible(versClair);
        champClair.setManaged(versClair);

        oeil.setGraphic(versClair ? Icons.eye(TAILLE_ICONE, couleur)
                                  : Icons.eyeOff(TAILLE_ICONE, couleur));
        oeil.getTooltip().setText(versClair ? "Masquer le mot de passe" : "Afficher le mot de passe");

        if (versClair) {
            if (champ.isFocused()) champClair.requestFocus();
            champClair.positionCaret(champClair.getText().length());
        } else if (champClair.isFocused()) {
            champ.requestFocus();
        }
    }
}
