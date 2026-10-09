package com.budgetmanager.util;

import java.time.LocalDate;

/** Petits outils pour construire des requêtes SQL sûres. */
public final class SqlUtil {

    /** Caractère d'échappement utilisé avec « LIKE ? ESCAPE '|' ». */
    public static final char ECHAPPEMENT = '|';

    private SqlUtil() {}

    /** Échappe %, _ et | pour qu'une recherche « 50% » cherche vraiment « 50% » et non « 50 + n'importe quoi ». */
    public static String echapperLike(String texte) {
        return texte.replace("|", "||").replace("%", "|%").replace("_", "|_");
    }

    /**
     * Condition « opération réalisée » : la date est aujourd'hui ou avant. Les opérations datées dans
     * le futur sont prévues mais pas encore réalisées : elles ne comptent pas dans les totaux ni le solde.
     * La date vient de Java (jamais de l'utilisateur) : pas de risque d'injection.
     */
    public static String jusquAujourdhui(String colonneDate) {
        return " AND " + colonneDate + " <= '" + LocalDate.now() + "' ";
    }
}
