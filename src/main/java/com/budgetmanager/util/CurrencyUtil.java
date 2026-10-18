package com.budgetmanager.util;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;

/**
 * Formatage centralisé des montants monétaires.
 *
 * <p>Utilise {@link java.text.NumberFormat} pour un formatage
 * natif conforme aux conventions locales de chaque devise.
 * Changer de devise = une seule modification ici, répercutée
 * partout dans l'application (tableaux, labels, graphiques).
 */
public final class CurrencyUtil {

    private CurrencyUtil() {}

    /**
     * Devise de l'utilisateur connecté, lue au moment de l'appel.
     * À préférer à une variable capturée au {@code initialize()} : un changement
     * de devise dans Paramètres doit être visible immédiatement, sinon le total
     * d'en-tête et les cellules du tableau n'affichent plus la même monnaie.
     */
    public static String deviseCourante() {
        com.budgetmanager.model.User utilisateur = SessionManager.getUtilisateurConnecte();
        return (utilisateur != null && utilisateur.getDevise() != null)
                ? utilisateur.getDevise() : "Ar";
    }

    /** Propriété « hors session » : sert de dépendance inerte quand aucun utilisateur n'est connecté. */
    private static final javafx.beans.property.StringProperty DEVISE_CONSTANTE =
            new javafx.beans.property.SimpleStringProperty("Ar");

    /**
     * Dépendance à passer aux bindings qui affichent un montant : elle invalide
     * le binding dès que l'utilisateur change de devise dans Paramètres.
     */
    public static javafx.beans.Observable sourceDevise() {
        com.budgetmanager.model.User utilisateur = SessionManager.getUtilisateurConnecte();
        return utilisateur != null ? utilisateur.deviseProperty() : DEVISE_CONSTANTE;
    }

    /**
     * Codes ISO 4217 supportés → Locale d'affichage.
     * Ar (Ariary malgache) n'a pas de code ISO standard,
     * on le gère manuellement.
     */
    private static final Map<String, Locale> LOCALES = Map.of(
            "EUR", Locale.FRANCE,
            "USD", Locale.US,
            "GBP", Locale.UK
    );

    /**
     * Locale.FRANCE sépare les milliers par un espace insécable ÉTROIT (U+202F).
     * Les polices de l'application ne lui donnent aucune largeur en petits corps :
     * « 2 050 000,00 Ar » se peignait « 2050000,00 Ar ». Un insécable ordinaire se
     * rend partout et garde les montants groupés sous les yeux.
     */
    private static String groupement(String montant) {
        return montant.replace('\u202f', '\u00a0');
    }

    /**
     * Formate un montant avec le symbole et les séparateurs corrects.
     *
     * <p>Exemples :
     * <ul>
     *   <li>formater(1_200_000.5, "Ar")  → "1 200 000,50 Ar"</li>
     *   <li>formater(1_200.5,     "EUR") → "1 200,50 €"</li>
     *   <li>formater(1_200.5,     "USD") → "$1,200.50"</li>
     * </ul>
     */
    public static String formater(double montant, String devise) {
        if ("Ar".equals(devise)) {
            // Ariary : pas de code ISO, format manuel avec séparateur français.
            return groupement(String.format(Locale.FRANCE, "%,.2f Ar", montant));
        }

        Locale locale = LOCALES.getOrDefault(devise, Locale.FRANCE);
        try {
            NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
            nf.setCurrency(Currency.getInstance(devise));
            return groupement(nf.format(montant));
        } catch (IllegalArgumentException e) {
            // Devise inconnue : affichage brut avec code.
            return groupement(String.format(Locale.FRANCE, "%,.2f %s", montant, devise));
        }
    }

    /**
     * Formate un montant sans symbole de devise (pour les champs de saisie).
     * Exemple : formaterSansSymbole(1200.5) → "1 200,50"
     */
    public static String formaterSansSymbole(double montant) {
        return groupement(String.format(Locale.FRANCE, "%,.2f", montant));
    }

    /**
     * Convertisseur pour les axes de graphiques : « 1 250 000 » y est illisible,
     * on l'affiche en milliers ou millions (« 750 k », « 1 M »).
     */
    public static javafx.util.StringConverter<Number> compact() {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(Number valeur) {
                if (valeur == null) return "";
                double v = valeur.doubleValue();
                if (Math.abs(v) >= 1_000_000) {
                    return String.format(Locale.FRANCE, "%.1f M", v / 1_000_000)
                            .replace(",0 M", " M");
                }
                if (Math.abs(v) >= 1_000) {
                    return String.format(Locale.FRANCE, "%.0f k", v / 1_000);
                }
                return String.valueOf(valeur.intValue());
            }

            @Override
            public Number fromString(String texte) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
