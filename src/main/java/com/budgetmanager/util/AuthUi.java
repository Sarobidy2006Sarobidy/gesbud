package com.budgetmanager.util;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilitaires partagés par les écrans d'authentification
 * (connexion, inscription, mot de passe oublié).
 */
public final class AuthUi {

    private static final Color ICONE_CHAMP = Color.web("#9dc8ff");

    private AuthUi() {}

    /** Couleur des icônes de champ (bleu clair, sur carte glassmorphism). */
    public static Color couleurIconeChamp() {
        return ICONE_CHAMP;
    }

    /**
     * Active la lueur bleue au focus sur les conteneurs {@code .auth-input-premium} :
     * JavaFX ne possède pas de ":focus-within", on recopie manuellement l'état
     * focus du champ interne vers la ligne parente.
     *
     * <p>Les cartes d'authentification vivent dans un ScrollPane : leurs champs
     * ne sont ajoutés à l'arbre que par la skin, une fois le CSS appliqué. Au
     * moment de {@code initialize()} ni {@code lookupAll} ni un parcours manuel
     * ne les voient — on attend donc que la racine rejoigne une scène.</p>
     */
    public static void activerFocusSurLignesPremium(Node racine) {
        if (racine.getScene() != null) {
            brancherFocusSurLignes(racine);
            return;
        }
        racine.sceneProperty().addListener(new ChangeListener<Scene>() {
            @Override
            public void changed(ObservableValue<? extends Scene> observable, Scene avant, Scene apres) {
                if (apres == null) return;
                racine.sceneProperty().removeListener(this);
                brancherFocusSurLignes(racine);
            }
        });
    }

    private static void brancherFocusSurLignes(Node racine) {
        racine.applyCss();
        for (Node ligne : racine.lookupAll(".auth-input-premium")) {
            if (!(ligne instanceof Pane)) continue;
            for (Node champ : saisiesDe((Pane) ligne)) {
                champ.focusedProperty().addListener((obs, avant, apres) -> {
                    if (apres) {
                        ligne.setStyle(
                            "-fx-background-color: rgba(255,255,255,0.16);" +
                            "-fx-border-color: rgba(93,170,255,0.75);" +
                            "-fx-effect: dropshadow(gaussian, rgba(93,170,255,0.55), 18, 0.08, 0, 0);");
                    } else {
                        ligne.setStyle("");
                    }
                });
            }
        }
    }

    /**
     * Champs de saisie d'une ligne, y compris ceux enfermés dans un conteneur
     * intermédiaire (le StackPane ajouté par {@link OeilMotDePasse} autour du
     * mot de passe).
     */
    private static List<Node> saisiesDe(Pane conteneur) {
        List<Node> saisies = new ArrayList<>();
        collecterSaisies(conteneur, saisies);
        return saisies;
    }

    private static void collecterSaisies(Pane conteneur, List<Node> saisies) {
        for (Node enfant : conteneur.getChildren()) {
            if (enfant instanceof TextInputControl || enfant instanceof ComboBox) saisies.add(enfant);
            if (enfant instanceof Pane) collecterSaisies((Pane) enfant, saisies);
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

    /* ──────────────────── Bandeau de message en ligne ──────────────────── */

    public static void erreur(Label bandeau, String texte) {
        bandeau.setGraphic(Icons.alertCircle(16, Color.web("#ff8080")));
        afficher(bandeau, texte, "auth-message-error");
    }

    public static void succes(Label bandeau, String texte) {
        bandeau.setGraphic(Icons.checkCircle(16, Color.web("#5fffaa")));
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
