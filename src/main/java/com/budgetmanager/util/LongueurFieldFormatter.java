package com.budgetmanager.util;

import javafx.scene.control.TextFormatter;

import java.util.function.UnaryOperator;

/**
 * TextFormatter qui borne le nombre de caractères d'un champ de saisie, à la
 * longueur de la colonne qui recevra le texte. Sans lui, une description trop
 * longue passe la validation, part en base, et revient en « erreur inattendue »
 * alors que l'utilisateur a simplement écrit trop long.
 */
public final class LongueurFieldFormatter {

    private LongueurFieldFormatter() {}

    /**
     * Coupe l'apport au-delà de « maximum » plutôt que de rejeter le collage en
     * bloc : un texte refusé en entier laisse l'utilisateur devant un champ vide
     * sans comprendre pourquoi.
     */
    public static TextFormatter<String> creer(int maximum) {
        UnaryOperator<TextFormatter.Change> filtre = changement -> {
            String nouveau = changement.getControlNewText();
            if (nouveau.length() <= maximum) {
                return changement;
            }
            int place = maximum - changement.getControlText().length()
                    + (changement.getRangeEnd() - changement.getRangeStart());
            if (place <= 0) {
                return null;
            }
            String garde = changement.getText().substring(0, place);
            changement.setText(garde);
            return changement;
        };
        return new TextFormatter<>(filtre);
    }
}
