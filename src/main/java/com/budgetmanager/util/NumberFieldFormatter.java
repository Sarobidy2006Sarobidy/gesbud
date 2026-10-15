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
     * Écrit un montant sans notation scientifique : {@code String.valueOf} donne
     * « 1.0E7 » à partir de dix millions, texte que le champ refuserait ensuite
     * de modifier et que personne ne relit.
     */
    public static String ecrire(double montant) {
        return new java.math.BigDecimal(String.valueOf(montant)).stripTrailingZeros().toPlainString();
    }

    /**
     * Lit un montant saisi : virgule ou point, espaces (y compris insécables) ignorés.
     * Renvoie {@code null} pour du vide, du non numérique ou du négatif — un plafond
     * mal tapé redevient « aucun plafond » plutôt qu'une valeur farfelue.
     */
    public static Double lire(String texte) {
        if (texte == null) return null;
        String nettoye = texte.trim().replace("\u00a0", "").replace(" ", "").replace(",", ".");
        if (nettoye.isEmpty()) return null;
        try {
            double valeur = Double.parseDouble(nettoye);
            return valeur > 0 ? valeur : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Crée un TextFormatter pour nombres décimaux (max 2 décimales).
     * Compatible avec séparateur virgule (converti en point automatiquement).
     *
     * <p>Le convertisseur est neutre : il rend le texte tel quel. Un convertisseur
     * numérique ferait repasser chaque saisie par un Double, et le champ se
     * réécrirait sous les yeux de l'utilisateur — « 90000 » devient « 90000.0 »,
     * « 1.50 » devient « 1.5 », « 10000000 » devient « 1.0E7 ». Seule la filtre
     * décide ce qui entre dans le champ.
     */
    public static TextFormatter<String> creer() {
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

        StringConverter<String> neutre = new StringConverter<>() {
            @Override
            public String toString(String texte) {
                return texte == null ? "" : texte;
            }

            @Override
            public String fromString(String texte) {
                return texte == null ? "" : texte;
            }
        };

        return new TextFormatter<>(neutre, "", filtre);
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
