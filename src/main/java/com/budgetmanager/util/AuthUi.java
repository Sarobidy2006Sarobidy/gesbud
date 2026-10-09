package com.budgetmanager.util;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

/**
 * Petits utilitaires partagés par les écrans d'authentification
 * (connexion, inscription, mot de passe oublié).
 */
public final class AuthUi {

    private static final Color ICONE_CHAMP = Color.web("#6f8fc9");

    private AuthUi() {}

    /** Icône des champs : bleu-gris doux, lisible sur le fond blanc des champs. */
    public static Color couleurIconeChamp() {
        return ICONE_CHAMP;
    }

    /**
     * Les champs de l'écran d'authentification sont des lignes blanches
     * (icône + champ de saisie). JavaFX n'a pas de ":focus-within" : on
     * recopie donc l'état de focus du champ vers sa ligne (classe
     * {@code auth-input-focused}) pour afficher le liseré lumineux.
     */
    public static void activerFocusSurLignes(Node racine) {
        for (Node ligne : racine.lookupAll(".auth-input")) {
            if (!(ligne instanceof Pane)) continue;
            for (Node enfant : ((Pane) ligne).getChildren()) {
                if (enfant instanceof TextInputControl || enfant instanceof ComboBox) {
                    enfant.focusedProperty().addListener((obs, avant, apres) -> {
                        if (apres) ligne.getStyleClass().add("auth-input-focused");
                        else ligne.getStyleClass().remove("auth-input-focused");
                    });
                }
            }
        }
    }

    /**
     * Affiche le texte indicatif (promptText) d'une liste déroulante en gris clair,
     * comme celui des champs de saisie, tant qu'aucune valeur n'est choisie.
     */
    public static void styliserPrompt(ComboBox<String> combo) {
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("auth-combo-prompt");
                if (empty || item == null) {
                    setText(combo.getPromptText());
                    getStyleClass().add("auth-combo-prompt");
                } else {
                    setText(item);
                }
            }
        });
    }

    /* ----------------------- Bandeau de message en ligne ----------------------- */

    public static void erreur(Label bandeau, String texte) {
        bandeau.setGraphic(Icons.alertCircle(16, Color.web("#b42318")));
        afficher(bandeau, texte, "auth-message-error");
    }

    public static void succes(Label bandeau, String texte) {
        bandeau.setGraphic(Icons.checkCircle(16, Color.web("#0b6b45")));
        afficher(bandeau, texte, "auth-message-success");
    }

    public static void effacer(Label bandeau) {
        bandeau.setVisible(false);
        bandeau.setManaged(false);
        bandeau.setText("");
        bandeau.setGraphic(null);
    }

    private static void afficher(Label bandeau, String texte, String classe) {
        bandeau.getStyleClass().removeAll("auth-message-error", "auth-message-success");
        bandeau.getStyleClass().add(classe);
        bandeau.setText(texte);
        bandeau.setVisible(true);
        bandeau.setManaged(true);
    }
}
