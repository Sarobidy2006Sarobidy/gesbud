package com.budgetmanager.util;

import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * TextFormatter pour TextField qui n'accepte que des nombres décimaux.
 *
 * <p>Utilisez-le pour les champs montant :
 * <pre>
 *   champMontant.setTextFormatter(NumberFieldFormatter.creer());
 * </pre>
 *
 * <p>Fonctionnalités :
 * - N'accepte que chiffres, point et virgule
 * - Limite à 2 décimales
 * - Remplace automatiquement virgule par point
 * - Bloque les lettres et caractères spéciaux
 */
public final class NumberFieldFormatter {

    private NumberFieldFormatter() {}

    /**
     * Crée un TextFormatter pour nombres décimaux (max 2 décimales).
     * Compatible avec séparateur virgule (converti en point automatiquement).
     */
    public static TextFormatter<Double> creer() {
        // Pattern : chiffres, point ou virgule, max 2 décimales
        Pattern pattern = Pattern.compile("^\\d*[.,]?\\d{0,2}$");

        UnaryOperator<TextFormatter.Change> filtre = change -> {
            String nouveauTexte = change.getControlNewText();

            // Vide autorisé (pour pouvoir effacer)
            if (nouveauTexte.isEmpty()) {
                return change;
            }

            // Remplacer virgule par point
            if (change.getText().equals(",")) {
                change.setText(".");
            }

            // Vérifier le pattern
            if (pattern.matcher(nouveauTexte).matches()) {
                return change;
            }

            // Rejeter la modification
            return null;
        };

        StringConverter<Double> convertisseur = new StringConverter<>() {
            @Override
            public String toString(Double valeur) {
                return valeur == null || valeur == 0.0 ? "" : String.valueOf(valeur);
            }

            @Override
            public Double fromString(String texte) {
                if (texte == null || texte.trim().isEmpty()) {
                    return 0.0;
                }
                try {
                    return Double.parseDouble(texte.replace(',', '.'));
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            }
        };

        return new TextFormatter<>(convertisseur, 0.0, filtre);
    }

    /**
     * Version alternative pour les entiers (pas de décimales).
     * Utile pour les champs quantité, âge, etc.
     */
    public static TextFormatter<Integer> creerEntier() {
        Pattern pattern = Pattern.compile("^\\d*$");

        UnaryOperator<TextFormatter.Change> filtre = change -> {
            String nouveauTexte = change.getControlNewText();
            if (nouveauTexte.isEmpty() || pattern.matcher(nouveauTexte).matches()) {
                return change;
            }
            return null;
        };

        StringConverter<Integer> convertisseur = new StringConverter<>() {
            @Override
            public String toString(Integer valeur) {
                return valeur == null || valeur == 0 ? "" : String.valueOf(valeur);
            }

            @Override
            public Integer fromString(String texte) {
                if (texte == null || texte.trim().isEmpty()) {
                    return 0;
                }
                try {
                    return Integer.parseInt(texte);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        };

        return new TextFormatter<>(convertisseur, 0, filtre);
    }
}
