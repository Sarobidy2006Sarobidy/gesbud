package com.budgetmanager.dao;

import com.budgetmanager.config.DatabaseConnection;
import com.budgetmanager.model.Expense;
import com.budgetmanager.model.Frequency;
import com.budgetmanager.util.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExpenseDAO {

    private static final Logger log = LoggerFactory.getLogger(ExpenseDAO.class);

    private static final String SELECT_BASE =
            "SELECT d.*, c.nom AS nom_categorie FROM expenses d " +
            "JOIN categories c ON d.id_categorie = c.id_categorie ";

    public boolean creer(Expense depense) {
        String sql = "INSERT INTO expenses (id_user, id_categorie, montant, description, date_depense, " +
                "frequence, date_fin, derniere_generation) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparerAvecCleAuto(conn, sql)) {

            stmt.setInt(1, depense.getIdUser());
            stmt.setInt(2, depense.getIdCategorie());
            stmt.setDouble(3, depense.getMontant());
            stmt.setString(4, depense.getDescription());
            stmt.setDate(5, Date.valueOf(depense.getDateDepense()));
            stmt.setString(6, depense.getFrequence().name());
            stmt.setDate(7, depense.getDateFin() == null ? null : Date.valueOf(depense.getDateFin()));
            LocalDate derniere = depense.getFrequence().estRecurrent() ? depense.getDateDepense() : null;
            stmt.setDate(8, derniere == null ? null : Date.valueOf(derniere));

            int lignes = stmt.executeUpdate();
            if (lignes > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) depense.setIdDepense(rs.getInt(1));
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            log.error("ExpenseDAO.creer() — {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean modifier(Expense depense) {
        String sql = "UPDATE expenses SET id_categorie = ?, montant = ?, description = ?, date_depense = ?, " +
                "frequence = ?, date_fin = ?, derniere_generation = COALESCE(?, derniere_generation) WHERE id_depense = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {

            stmt.setInt(1, depense.getIdCategorie());
            stmt.setDouble(2, depense.getMontant());
            stmt.setString(3, depense.getDescription());
            stmt.setDate(4, Date.valueOf(depense.getDateDepense()));
            stmt.setString(5, depense.getFrequence().name());
            stmt.setDate(6, depense.getDateFin() == null ? null : Date.valueOf(depense.getDateFin()));
            stmt.setDate(7, depense.getDerniereGeneration() == null ? null : Date.valueOf(depense.getDerniereGeneration()));
            stmt.setInt(8, depense.getIdDepense());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("ExpenseDAO.modifier() — {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Clôture la récurrence {@code source} et démarre {@code suite} avec le nouveau montant,
     * de façon atomique : les deux écritures passent ensemble ou aucune ne passe.
     */
    public boolean changerMontantRecurrent(Expense source, Expense suite) {
        String majSql = "UPDATE expenses SET id_categorie = ?, montant = ?, description = ?, date_depense = ?, " +
                "frequence = ?, date_fin = ?, derniere_generation = COALESCE(?, derniere_generation) WHERE id_depense = ?";
        String insertionSql = "INSERT INTO expenses (id_user, id_categorie, montant, description, date_depense, " +
                "frequence, date_fin, derniere_generation) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement maj = DatabaseConnection.preparer(conn, majSql);
                 PreparedStatement insertion = DatabaseConnection.preparerAvecCleAuto(conn, insertionSql)) {

                maj.setInt(1, source.getIdCategorie());
                maj.setDouble(2, source.getMontant());
                maj.setString(3, source.getDescription());
                maj.setDate(4, Date.valueOf(source.getDateDepense()));
                maj.setString(5, source.getFrequence().name());
                maj.setDate(6, source.getDateFin() == null ? null : Date.valueOf(source.getDateFin()));
                maj.setDate(7, source.getDerniereGeneration() == null ? null : Date.valueOf(source.getDerniereGeneration()));
                maj.setInt(8, source.getIdDepense());

                insertion.setInt(1, suite.getIdUser());
                insertion.setInt(2, suite.getIdCategorie());
                insertion.setDouble(3, suite.getMontant());
                insertion.setString(4, suite.getDescription());
                insertion.setDate(5, Date.valueOf(suite.getDateDepense()));
                insertion.setString(6, suite.getFrequence().name());
                insertion.setDate(7, suite.getDateFin() == null ? null : Date.valueOf(suite.getDateFin()));
                LocalDate derniere = suite.getFrequence().estRecurrent() ? suite.getDateDepense() : null;
                insertion.setDate(8, derniere == null ? null : Date.valueOf(derniere));

                if (maj.executeUpdate() == 0 || insertion.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
                try (ResultSet rs = insertion.getGeneratedKeys()) {
                    if (rs.next()) suite.setIdDepense(rs.getInt(1));
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            log.error("ExpenseDAO.changerMontantRecurrent() — {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean supprimer(int idDepense) {
        String sql = "DELETE FROM expenses WHERE id_depense = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idDepense);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("ExpenseDAO.supprimer() — {}", e.getMessage(), e);
            return false;
        }
    }

    public List<Expense> listerParUser(int idUser) {
        String sql = SELECT_BASE + "WHERE d.id_user = ? ORDER BY d.date_depense DESC";
        List<Expense> resultats = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) resultats.add(mapper(rs));
            }
        } catch (SQLException e) {
            log.error("ExpenseDAO.listerParUser() — {}", e.getMessage(), e);
        }
        return resultats;
    }

    public List<Expense> rechercher(int idUser, String motCle, Integer idCategorie,
                                     LocalDate dateDebut, LocalDate dateFin) {
        StringBuilder sql = new StringBuilder(SELECT_BASE + "WHERE d.id_user = ? ");
        List<Object> params = new ArrayList<>();
        params.add(idUser);

        if (motCle != null && !motCle.isBlank()) {
            sql.append("AND d.description LIKE ? ESCAPE '|' ");
            params.add("%" + SqlUtil.echapperLike(motCle.trim()) + "%");
        }
        if (idCategorie != null) {
            sql.append("AND d.id_categorie = ? ");
            params.add(idCategorie);
        }
        if (dateDebut != null) {
            sql.append("AND d.date_depense >= ? ");
            params.add(Date.valueOf(dateDebut));
        }
        if (dateFin != null) {
            sql.append("AND d.date_depense <= ? ");
            params.add(Date.valueOf(dateFin));
        }
        sql.append("ORDER BY d.date_depense DESC");

        List<Expense> resultats = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql.toString())) {
            for (int i = 0; i < params.size(); i++) stmt.setObject(i + 1, params.get(i));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) resultats.add(mapper(rs));
            }
        } catch (SQLException e) {
            log.error("ExpenseDAO.rechercher() — {}", e.getMessage(), e);
        }
        return resultats;
    }

    public double totalParUser(int idUser) {
        String sql = "SELECT COALESCE(SUM(montant), 0) AS total FROM expenses WHERE id_user = ?"
                + SqlUtil.jusquAujourdhui("date_depense");
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("total");
            }
        } catch (SQLException e) {
            log.error("ExpenseDAO.totalParUser() — {}", e.getMessage(), e);
        }
        return 0;
    }

    public int nombreParUser(int idUser) {
        String sql = "SELECT COUNT(*) AS nb FROM expenses WHERE id_user = ?"
                + SqlUtil.jusquAujourdhui("date_depense");
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("nb");
            }
        } catch (SQLException e) {
            log.error("ExpenseDAO.nombreParUser() — {}", e.getMessage(), e);
        }
        return 0;
    }

    public double totalAvantAnnee(int idUser, int annee) {
        String sql = "SELECT COALESCE(SUM(montant), 0) AS total FROM expenses WHERE id_user = ? AND YEAR(date_depense) < ?"
                + SqlUtil.jusquAujourdhui("date_depense");
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            stmt.setInt(2, annee);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("total");
            }
        } catch (SQLException e) {
            log.error("ExpenseDAO.totalAvantAnnee() — {}", e.getMessage(), e);
        }
        return 0;
    }

    public double totalParMois(int idUser, YearMonth mois) {
        String sql = "SELECT COALESCE(SUM(montant), 0) AS total FROM expenses " +
                "WHERE id_user = ? AND YEAR(date_depense) = ? AND MONTH(date_depense) = ?"
                + SqlUtil.jusquAujourdhui("date_depense");
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            stmt.setInt(2, mois.getYear());
            stmt.setInt(3, mois.getMonthValue());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("total");
            }
        } catch (SQLException e) {
            log.error("ExpenseDAO.totalParMois() — {}", e.getMessage(), e);
        }
        return 0;
    }

    /**
     * Répartition par catégorie. {@code mois} null = depuis l'origine ;
     * sinon un seul mois, pour que le camembert suive le sélecteur de période.
     */
    public List<Object[]> repartitionParCategorie(int idUser, YearMonth mois) {
        String sql = "SELECT c.nom, COALESCE(SUM(d.montant), 0) AS total FROM categories c " +
                "LEFT JOIN expenses d ON d.id_categorie = c.id_categorie"
                + SqlUtil.jusquAujourdhui("d.date_depense")
                + (mois == null ? "" : "AND YEAR(d.date_depense) = ? AND MONTH(d.date_depense) = ? ") +
                "WHERE c.id_user = ? AND c.type = 'DEPENSE' GROUP BY c.id_categorie, c.nom";
        List<Object[]> resultats = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            int parametre = 1;
            if (mois != null) {
                stmt.setInt(parametre++, mois.getYear());
                stmt.setInt(parametre++, mois.getMonthValue());
            }
            stmt.setInt(parametre, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) resultats.add(new Object[]{rs.getString("nom"), rs.getDouble("total")});
            }
        } catch (SQLException e) {
            log.error("ExpenseDAO.repartitionParCategorie() — {}", e.getMessage(), e);
        }
        return resultats;
    }

    /**
     * Catégories de dépenses dont le plafond mensuel est dépassé sur le mois donné :
     * {@code [nom, dépensé, plafond]}, les plus dépassées d'abord. Une catégorie sans
     * plafond (budget NULL) ne peut jamais apparaître ici — rien n'est limite par défaut.
     */
    public List<Object[]> plafondsDepasses(int idUser, YearMonth mois) {
        String sql = "SELECT c.nom, SUM(d.montant) AS total, c.budget FROM categories c " +
                "JOIN expenses d ON d.id_categorie = c.id_categorie " +
                "WHERE c.id_user = ? AND c.type = 'DEPENSE' AND c.budget IS NOT NULL " +
                "AND YEAR(d.date_depense) = ? AND MONTH(d.date_depense) = ? "
                + SqlUtil.jusquAujourdhui("d.date_depense") +
                "GROUP BY c.id_categorie, c.nom, c.budget HAVING SUM(d.montant) > c.budget " +
                "ORDER BY SUM(d.montant) - c.budget DESC";
        List<Object[]> resultats = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            stmt.setInt(2, mois.getYear());
            stmt.setInt(3, mois.getMonthValue());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    resultats.add(new Object[]{rs.getString("nom"), rs.getDouble("total"), rs.getDouble("budget")});
                }
            }
        } catch (SQLException e) {
            log.error("ExpenseDAO.plafondsDepasses() — {}", e.getMessage(), e);
        }
        return resultats;
    }

    public int genererRecurrences(int idUser) {
        LocalDate aujourdhui = LocalDate.now();
        String selection = SELECT_BASE + "WHERE d.id_user = ? AND d.frequence <> 'PONCTUEL' AND d.id_source IS NULL";
        String insertion = "INSERT INTO expenses (id_user, id_categorie, montant, description, date_depense, " +
                "frequence, id_source) VALUES (?, ?, ?, ?, ?, 'PONCTUEL', ?)";
        String miseAJour = "UPDATE expenses SET derniere_generation = ? WHERE id_depense = ?";

        int crees = 0;
        try (Connection conn = DatabaseConnection.getConnection()) {
            List<Expense> sources = new ArrayList<>();
            try (PreparedStatement stmt = DatabaseConnection.preparer(conn, selection)) {
                stmt.setInt(1, idUser);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) sources.add(mapper(rs));
                }
            }
            // Le calcul des occurrences se ré-ancre sur la date de début : sans ce
            // filtre, changer cette date réinsérerait des lignes déjà en base.
            Map<Integer, Set<LocalDate>> genrees = SqlUtil.datesGenerees(conn, "expenses", "date_depense", idUser);
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ins = DatabaseConnection.preparer(conn, insertion);
                 PreparedStatement maj = DatabaseConnection.preparer(conn, miseAJour)) {
                for (Expense source : sources) {
                    LocalDate limite = source.getDateFin() != null && source.getDateFin().isBefore(aujourdhui)
                            ? source.getDateFin() : aujourdhui;
                    LocalDate apres = source.getDerniereGeneration() != null
                            ? source.getDerniereGeneration() : source.getDateDepense();
                    List<LocalDate> dates = source.getFrequence()
                            .occurrencesEntre(source.getDateDepense(), apres, limite);
                    dates.removeAll(genrees.getOrDefault(source.getIdDepense(), Set.of()));
                    if (dates.isEmpty()) continue;
                    for (LocalDate date : dates) {
                        ins.setInt(1, source.getIdUser());
                        ins.setInt(2, source.getIdCategorie());
                        ins.setDouble(3, source.getMontant());
                        ins.setString(4, source.getDescription());
                        ins.setDate(5, Date.valueOf(date));
                        ins.setInt(6, source.getIdDepense());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                    maj.setDate(1, Date.valueOf(dates.get(dates.size() - 1)));
                    maj.setInt(2, source.getIdDepense());
                    maj.executeUpdate();
                    crees += dates.size();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            log.error("ExpenseDAO.genererRecurrences() — {}", e.getMessage(), e);
        }
        return crees;
    }

    public void migrerSiNecessaire() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            if (!colonneExiste(meta, conn.getCatalog(), "frequence")) {
                try (Statement st = DatabaseConnection.creerStatement(conn, DatabaseConnection.TIMEOUT_MIGRATION)) {
                    st.executeUpdate("ALTER TABLE expenses " +
                            "ADD COLUMN frequence " + SqlUtil.ENUM_FREQUENCE + " NOT NULL DEFAULT 'PONCTUEL', " +
                            "ADD COLUMN date_fin DATE NULL, " +
                            "ADD COLUMN derniere_generation DATE NULL, " +
                            "ADD COLUMN id_source INT NULL");
                    st.executeUpdate("ALTER TABLE expenses ADD CONSTRAINT fk_depense_source " +
                            "FOREIGN KEY (id_source) REFERENCES expenses(id_depense) ON DELETE CASCADE");
                }
            } else if (!SqlUtil.frequenceAdmet(conn, "expenses", "ANNUEL")) {
                try (Statement st = DatabaseConnection.creerStatement(conn, DatabaseConnection.TIMEOUT_MIGRATION)) {
                    st.executeUpdate("ALTER TABLE expenses MODIFY COLUMN frequence "
                            + SqlUtil.ENUM_FREQUENCE + " NOT NULL DEFAULT 'PONCTUEL'");
                }
            }
        } catch (SQLException e) {
            log.warn("Migration expenses ignorée : {}", e.getMessage());
        }
    }

    private boolean colonneExiste(DatabaseMetaData meta, String base, String colonne) throws SQLException {
        try (ResultSet rs = meta.getColumns(base, null, "expenses", colonne)) {
            return rs.next();
        }
    }

    private Expense mapper(ResultSet rs) throws SQLException {
        Expense d = new Expense();
        d.setIdDepense(rs.getInt("id_depense"));
        d.setIdUser(rs.getInt("id_user"));
        d.setIdCategorie(rs.getInt("id_categorie"));
        d.setNomCategorie(rs.getString("nom_categorie"));
        d.setMontant(rs.getDouble("montant"));
        d.setDescription(rs.getString("description"));
        d.setDateDepense(rs.getDate("date_depense").toLocalDate());
        Timestamp ts = rs.getTimestamp("date_creation");
        if (ts != null) d.setDateCreation(ts.toLocalDateTime());
        d.setFrequence(Frequency.depuisBase(rs.getString("frequence")));
        Date fin = rs.getDate("date_fin");
        if (fin != null) d.setDateFin(fin.toLocalDate());
        Date derniere = rs.getDate("derniere_generation");
        if (derniere != null) d.setDerniereGeneration(derniere.toLocalDate());
        int source = rs.getInt("id_source");
        if (!rs.wasNull()) d.setIdSource(source);
        return d;
    }
}
