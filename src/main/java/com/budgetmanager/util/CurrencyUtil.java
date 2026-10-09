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
            return String.format(Locale.FRANCE, "%,.2f Ar", montant);
        }

        Locale locale = LOCALES.getOrDefault(devise, Locale.FRANCE);
        try {
            NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
            nf.setCurrency(Currency.getInstance(devise));
            return nf.format(montant);
        } catch (IllegalArgumentException e) {
            // Devise inconnue : affichage brut avec code.
            return String.format(Locale.FRANCE, "%,.2f %s", montant, devise);
        }
    }

    /**
     * Formate un montant sans symbole de devise (pour les champs de saisie).
     * Exemple : formaterSansSymbole(1200.5) → "1 200,50"
     */
    public static String formaterSansSymbole(double montant) {
        return String.format(Locale.FRANCE, "%,.2f", montant);
    }
}
