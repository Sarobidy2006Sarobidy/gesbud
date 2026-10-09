package com.budgetmanager.dao;

import com.budgetmanager.config.DatabaseConnection;
import com.budgetmanager.model.Frequency;
import com.budgetmanager.model.Income;
import com.budgetmanager.util.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès aux données de la table `incomes` (revenus).
 * Fournit création, modification, suppression, recherche et filtres.
 */
public class IncomeDAO {

    private static final Logger log = LoggerFactory.getLogger(IncomeDAO.class);

    private static final String SELECT_BASE =
            "SELECT r.*, c.nom AS nom_categorie FROM incomes r " +
            "JOIN categories c ON r.id_categorie = c.id_categorie ";

    public boolean creer(Income revenu) {
        String sql = "INSERT INTO incomes (id_user, id_categorie, montant, description, date_revenu, " +
                "frequence, date_fin, derniere_generation) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparerAvecCleAuto(conn, sql)) {

            stmt.setInt(1, revenu.getIdUser());
            stmt.setInt(2, revenu.getIdCategorie());
            stmt.setDouble(3, revenu.getMontant());
            stmt.setString(4, revenu.getDescription());
            stmt.setDate(5, Date.valueOf(revenu.getDateRevenu()));
            stmt.setString(6, revenu.getFrequence().name());
            stmt.setDate(7, revenu.getDateFin() == null ? null : Date.valueOf(revenu.getDateFin()));
            LocalDate derniere = revenu.getFrequence().estRecurrent() ? revenu.getDateRevenu() : null;
            stmt.setDate(8, derniere == null ? null : Date.valueOf(derniere));

            int lignes = stmt.executeUpdate();
            if (lignes > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) revenu.setIdRevenu(rs.getInt(1));
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            log.error("IncomeDAO.creer() — {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean modifier(Income revenu) {
        String sql = "UPDATE incomes SET id_categorie = ?, montant = ?, description = ?, date_revenu = ?, " +
                "frequence = ?, date_fin = ?, derniere_generation = COALESCE(?, derniere_generation) WHERE id_revenu = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {

            stmt.setInt(1, revenu.getIdCategorie());
            stmt.setDouble(2, revenu.getMontant());
            stmt.setString(3, revenu.getDescription());
            stmt.setDate(4, Date.valueOf(revenu.getDateRevenu()));
            stmt.setString(5, revenu.getFrequence().name());
            stmt.setDate(6, revenu.getDateFin() == null ? null : Date.valueOf(revenu.getDateFin()));
            stmt.setDate(7, revenu.getDerniereGeneration() == null ? null : Date.valueOf(revenu.getDerniereGeneration()));
            stmt.setInt(8, revenu.getIdRevenu());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("IncomeDAO.modifier() — {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Clôture la récurrence {@code source} et démarre {@code suite} avec le nouveau montant,
     * de façon atomique : les deux écritures passent ensemble ou aucune ne passe.
     */
    public boolean changerMontantRecurrent(Income source, Income suite) {
        String majSql = "UPDATE incomes SET id_categorie = ?, montant = ?, description = ?, date_revenu = ?, " +
                "frequence = ?, date_fin = ?, derniere_generation = COALESCE(?, derniere_generation) WHERE id_revenu = ?";
        String insertionSql = "INSERT INTO incomes (id_user, id_categorie, montant, description, date_revenu, " +
                "frequence, date_fin, derniere_generation) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement maj = DatabaseConnection.preparer(conn, majSql);
                 PreparedStatement insertion = DatabaseConnection.preparerAvecCleAuto(conn, insertionSql)) {

                maj.setInt(1, source.getIdCategorie());
                maj.setDouble(2, source.getMontant());
                maj.setString(3, source.getDescription());
                maj.setDate(4, Date.valueOf(source.getDateRevenu()));
                maj.setString(5, source.getFrequence().name());
                maj.setDate(6, source.getDateFin() == null ? null : Date.valueOf(source.getDateFin()));
                maj.setDate(7, source.getDerniereGeneration() == null ? null : Date.valueOf(source.getDerniereGeneration()));
                maj.setInt(8, source.getIdRevenu());

                insertion.setInt(1, suite.getIdUser());
                insertion.setInt(2, suite.getIdCategorie());
                insertion.setDouble(3, suite.getMontant());
                insertion.setString(4, suite.getDescription());
                insertion.setDate(5, Date.valueOf(suite.getDateRevenu()));
                insertion.setString(6, suite.getFrequence().name());
                insertion.setDate(7, suite.getDateFin() == null ? null : Date.valueOf(suite.getDateFin()));
                LocalDate derniere = suite.getFrequence().estRecurrent() ? suite.getDateRevenu() : null;
                insertion.setDate(8, derniere == null ? null : Date.valueOf(derniere));

                if (maj.executeUpdate() == 0 || insertion.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
                try (ResultSet rs = insertion.getGeneratedKeys()) {
                    if (rs.next()) suite.setIdRevenu(rs.getInt(1));
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
            log.error("IncomeDAO.changerMontantRecurrent() — {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean supprimer(int idRevenu) {
        String sql = "DELETE FROM incomes WHERE id_revenu = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idRevenu);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("IncomeDAO.supprimer() — {}", e.getMessage(), e);
            return false;
        }
    }

    public List<Income> listerParUser(int idUser) {
        String sql = SELECT_BASE + "WHERE r.id_user = ? ORDER BY r.date_revenu DESC";
        return executer(sql, idUser);
    }

    public List<Income> rechercher(int idUser, String motCle, Integer idCategorie,
                                    LocalDate dateDebut, LocalDate dateFin) {
        StringBuilder sql = new StringBuilder(SELECT_BASE + "WHERE r.id_user = ? ");
        List<Object> params = new ArrayList<>();
        params.add(idUser);

        if (motCle != null && !motCle.isBlank()) {
            sql.append("AND r.description LIKE ? ESCAPE '|' ");
            params.add("%" + SqlUtil.echapperLike(motCle.trim()) + "%");
        }
        if (idCategorie != null) {
            sql.append("AND r.id_categorie = ? ");
            params.add(idCategorie);
        }
        if (dateDebut != null) {
            sql.append("AND r.date_revenu >= ? ");
            params.add(Date.valueOf(dateDebut));
        }
        if (dateFin != null) {
            sql.append("AND r.date_revenu <= ? ");
            params.add(Date.valueOf(dateFin));
        }
        sql.append("ORDER BY r.date_revenu DESC");

        List<Income> resultats = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql.toString())) {
            for (int i = 0; i < params.size(); i++) stmt.setObject(i + 1, params.get(i));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) resultats.add(mapper(rs));
            }
        } catch (SQLException e) {
            log.error("IncomeDAO.rechercher() — {}", e.getMessage(), e);
        }
        return resultats;
    }

    public double totalParUser(int idUser) {
        String sql = "SELECT COALESCE(SUM(montant), 0) AS total FROM incomes WHERE id_user = ?"
                + SqlUtil.jusquAujourdhui("date_revenu");
        return total(sql, idUser);
    }

    public int nombreParUser(int idUser) {
        String sql = "SELECT COUNT(*) AS nb FROM incomes WHERE id_user = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("nb");
            }
        } catch (SQLException e) {
            log.error("IncomeDAO.nombreParUser() — {}", e.getMessage(), e);
        }
        return 0;
    }

    public double totalAvantAnnee(int idUser, int annee) {
        String sql = "SELECT COALESCE(SUM(montant), 0) AS total FROM incomes WHERE id_user = ? AND YEAR(date_revenu) < ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            stmt.setInt(2, annee);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("total");
            }
        } catch (SQLException e) {
            log.error("IncomeDAO.totalAvantAnnee() — {}", e.getMessage(), e);
        }
        return 0;
    }

    public double totalParMois(int idUser, YearMonth mois) {
        String sql = "SELECT COALESCE(SUM(montant), 0) AS total FROM incomes " +
                "WHERE id_user = ? AND YEAR(date_revenu) = ? AND MONTH(date_revenu) = ?"
                + SqlUtil.jusquAujourdhui("date_revenu");
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            stmt.setInt(2, mois.getYear());
            stmt.setInt(3, mois.getMonthValue());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("total");
            }
        } catch (SQLException e) {
            log.error("IncomeDAO.totalParMois() — {}", e.getMessage(), e);
        }
        return 0;
    }

    public List<Object[]> repartitionParCategorie(int idUser) {
        String sql = "SELECT c.nom, COALESCE(SUM(r.montant), 0) AS total FROM categories c " +
                "LEFT JOIN incomes r ON r.id_categorie = c.id_categorie"
                + SqlUtil.jusquAujourdhui("r.date_revenu") +
                "WHERE c.id_user = ? AND c.type = 'REVENU' GROUP BY c.id_categorie, c.nom";
        List<Object[]> resultats = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) resultats.add(new Object[]{rs.getString("nom"), rs.getDouble("total")});
            }
        } catch (SQLException e) {
            log.error("IncomeDAO.repartitionParCategorie() — {}", e.getMessage(), e);
        }
        return resultats;
    }

    public int genererRecurrences(int idUser) {
        LocalDate aujourdhui = LocalDate.now();
        String selection = SELECT_BASE + "WHERE r.id_user = ? AND r.frequence <> 'PONCTUEL' AND r.id_source IS NULL";
        String insertion = "INSERT INTO incomes (id_user, id_categorie, montant, description, date_revenu, " +
                "frequence, id_source) VALUES (?, ?, ?, ?, ?, 'PONCTUEL', ?)";
        String miseAJour = "UPDATE incomes SET derniere_generation = ? WHERE id_revenu = ?";

        int crees = 0;
        try (Connection conn = DatabaseConnection.getConnection()) {
            List<Income> sources = new ArrayList<>();
            try (PreparedStatement stmt = DatabaseConnection.preparer(conn, selection)) {
                stmt.setInt(1, idUser);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) sources.add(mapper(rs));
                }
            }
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ins = DatabaseConnection.preparer(conn, insertion);
                 PreparedStatement maj = DatabaseConnection.preparer(conn, miseAJour)) {
                for (Income source : sources) {
                    LocalDate limite = source.getDateFin() != null && source.getDateFin().isBefore(aujourdhui)
                            ? source.getDateFin() : aujourdhui;
                    LocalDate apres = source.getDerniereGeneration() != null
                            ? source.getDerniereGeneration() : source.getDateRevenu();
                    List<LocalDate> dates = source.getFrequence()
                            .occurrencesEntre(source.getDateRevenu(), apres, limite);
                    if (dates.isEmpty()) continue;
                    for (LocalDate date : dates) {
                        ins.setInt(1, source.getIdUser());
                        ins.setInt(2, source.getIdCategorie());
                        ins.setDouble(3, source.getMontant());
                        ins.setString(4, source.getDescription());
                        ins.setDate(5, Date.valueOf(date));
                        ins.setInt(6, source.getIdRevenu());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                    maj.setDate(1, Date.valueOf(dates.get(dates.size() - 1)));
                    maj.setInt(2, source.getIdRevenu());
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
            log.error("IncomeDAO.genererRecurrences() — {}", e.getMessage(), e);
        }
        return crees;
    }

    public void migrerSiNecessaire() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            if (!colonneExiste(meta, conn.getCatalog(), "frequence")) {
                try (Statement st = DatabaseConnection.creerStatement(conn, DatabaseConnection.TIMEOUT_MIGRATION)) {
                    st.executeUpdate("ALTER TABLE incomes " +
                            "ADD COLUMN frequence ENUM('PONCTUEL','QUOTIDIEN','HEBDOMADAIRE','MENSUEL') NOT NULL DEFAULT 'PONCTUEL', " +
                            "ADD COLUMN date_fin DATE NULL, " +
                            "ADD COLUMN derniere_generation DATE NULL, " +
                            "ADD COLUMN id_source INT NULL");
                    st.executeUpdate("ALTER TABLE incomes ADD CONSTRAINT fk_revenu_source " +
                            "FOREIGN KEY (id_source) REFERENCES incomes(id_revenu) ON DELETE CASCADE");
                }
            }
        } catch (SQLException e) {
            log.warn("Migration incomes ignorée : {}", e.getMessage());
        }
    }

    private boolean colonneExiste(DatabaseMetaData meta, String base, String colonne) throws SQLException {
        try (ResultSet rs = meta.getColumns(base, null, "incomes", colonne)) {
            return rs.next();
        }
    }

    private double total(String sql, int idUser) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("total");
            }
        } catch (SQLException e) {
            log.error("IncomeDAO.total() — {}", e.getMessage(), e);
        }
        return 0;
    }

    private List<Income> executer(String sql, int idUser) {
        List<Income> resultats = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) resultats.add(mapper(rs));
            }
        } catch (SQLException e) {
            log.error("IncomeDAO.executer() — {}", e.getMessage(), e);
        }
        return resultats;
    }

    private Income mapper(ResultSet rs) throws SQLException {
        Income r = new Income();
        r.setIdRevenu(rs.getInt("id_revenu"));
        r.setIdUser(rs.getInt("id_user"));
        r.setIdCategorie(rs.getInt("id_categorie"));
        r.setNomCategorie(rs.getString("nom_categorie"));
        r.setMontant(rs.getDouble("montant"));
        r.setDescription(rs.getString("description"));
        r.setDateRevenu(rs.getDate("date_revenu").toLocalDate());
        Timestamp ts = rs.getTimestamp("date_creation");
        if (ts != null) r.setDateCreation(ts.toLocalDateTime());
        r.setFrequence(Frequency.depuisBase(rs.getString("frequence")));
        Date fin = rs.getDate("date_fin");
        if (fin != null) r.setDateFin(fin.toLocalDate());
        Date derniere = rs.getDate("derniere_generation");
        if (derniere != null) r.setDerniereGeneration(derniere.toLocalDate());
        int source = rs.getInt("id_source");
        if (!rs.wasNull()) r.setIdSource(source);
        return r;
    }
}
