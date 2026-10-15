package com.budgetmanager.util;

import com.budgetmanager.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    /**
     * Dates déjà générées pour chaque source de récurrence de l'utilisateur.
     * Le calcul des occurrences se ré-ancre sur la date de début : changer cette
     * date ferait réinsérer des lignes déjà présentes (deux salaires le même
     * mois). Les occurrences sont donc filtrées sur ce qui existe déjà en base.
     *
     * @param table       nom de la table, et {@code colonneDate} sa colonne de date —
     *                    tous deux fournis par le code, jamais par la saisie
     */
    public static Map<Integer, Set<LocalDate>> datesGenerees(Connection conn, String table,
                                                             String colonneDate, int idUser) throws SQLException {
        Map<Integer, Set<LocalDate>> genrees = new HashMap<>();
        String requete = "SELECT id_source, " + colonneDate + " FROM " + table
                + " WHERE id_user = ? AND id_source IS NOT NULL";
        try (PreparedStatement stmt = DatabaseConnection.preparer(conn, requete)) {
            stmt.setInt(1, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    genrees.computeIfAbsent(rs.getInt("id_source"), cle -> new HashSet<>())
                            .add(rs.getDate(colonneDate).toLocalDate());
                }
            }
        }
        return genrees;
    }
}
