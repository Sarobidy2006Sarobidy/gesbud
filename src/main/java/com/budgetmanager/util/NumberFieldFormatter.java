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

    /** Borne de DECIMAL(15,2) : 13 chiffres avant la virgule. */
    public static final double MONTANT_MAX = 9_999_999_999_999.99;

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
        String brut = texte.trim();
        if (brut.isEmpty()) return null;
        // Un collage « -abc12e5,5.5 » n'est pas un montant : on refuse, on ne nettoie pas en silence.
        if (!brut.matches("[\\d\\s\u00a0]+([.,]\\d{1,2})?")) return null;
        String nettoye = brut.replace("\u00a0", "").replace(" ", "").replace(",", ".");
        try {
            double valeur = Double.parseDouble(nettoye);
            if (valeur <= 0 || valeur > MONTANT_MAX) return null;
            return valeur;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Message pour un champ montant refusé par {@link #lire}. */
    public static String messageErreur(String texte) {
        if (texte == null || texte.isBlank()) {
            return "Indiquez un montant supérieur à 0.";
        }
        String brut = texte.trim();
        if (!brut.matches("[\\d\\s\u00a0]+([.,]\\d{1,2})?")) {
            return "Le montant n'a pas été modifié en silence : ne tapez que des chiffres "
                    + "(exemple : 12500 ou 12500,50).";
        }
        String nettoye = brut.replace("\u00a0", "").replace(" ", "").replace(",", ".");
        try {
            double valeur = Double.parseDouble(nettoye);
            if (valeur <= 0) return "Le montant doit être supérieur à 0.";
            if (valeur > MONTANT_MAX) {
                return "Ce montant est trop grand. Le maximum est 9 999 999 999 999,99.";
            }
        } catch (NumberFormatException ignored) {
            return "Veuillez saisir un montant numérique valide.";
        }
        return "Veuillez saisir un montant numérique valide.";
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
        // 13 chiffres avant la virgule (DECIMAL 15,2), max 2 décimales.
        Pattern pattern = Pattern.compile("^\\d{0,13}([.,]\\d{0,2})?$");

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
