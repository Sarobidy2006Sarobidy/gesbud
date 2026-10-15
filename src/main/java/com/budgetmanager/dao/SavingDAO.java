package com.budgetmanager.dao;

import com.budgetmanager.config.DatabaseConnection;
import com.budgetmanager.model.Frequency;
import com.budgetmanager.model.Saving;
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

public class SavingDAO {

    private static final Logger log = LoggerFactory.getLogger(SavingDAO.class);

    public boolean creer(Saving economie) {
        String sql = "INSERT INTO savings (id_user, montant, description, date_economie, " +
                "frequence, date_fin, derniere_generation) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparerAvecCleAuto(conn, sql)) {

            stmt.setInt(1, economie.getIdUser());
            stmt.setDouble(2, economie.getMontant());
            stmt.setString(3, economie.getDescription());
            stmt.setDate(4, Date.valueOf(economie.getDateEconomie()));
            stmt.setString(5, economie.getFrequence().name());
            stmt.setDate(6, economie.getDateFin() == null ? null : Date.valueOf(economie.getDateFin()));
            LocalDate derniere = economie.getFrequence().estRecurrent() ? economie.getDateEconomie() : null;
            stmt.setDate(7, derniere == null ? null : Date.valueOf(derniere));

            if (stmt.executeUpdate() > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) economie.setIdEconomie(rs.getInt(1));
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            log.error("SavingDAO.creer() — {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean modifier(Saving economie) {
        String sql = "UPDATE savings SET montant = ?, description = ?, date_economie = ?, " +
                "frequence = ?, date_fin = ?, derniere_generation = COALESCE(?, derniere_generation) WHERE id_economie = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {

            stmt.setDouble(1, economie.getMontant());
            stmt.setString(2, economie.getDescription());
            stmt.setDate(3, Date.valueOf(economie.getDateEconomie()));
            stmt.setString(4, economie.getFrequence().name());
            stmt.setDate(5, economie.getDateFin() == null ? null : Date.valueOf(economie.getDateFin()));
            stmt.setDate(6, economie.getDerniereGeneration() == null ? null : Date.valueOf(economie.getDerniereGeneration()));
            stmt.setInt(7, economie.getIdEconomie());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("SavingDAO.modifier() — {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Clôture la récurrence {@code source} et démarre {@code suite} avec le nouveau montant,
     * de façon atomique : les deux écritures passent ensemble ou aucune ne passe.
     */
    public boolean changerMontantRecurrent(Saving source, Saving suite) {
        String majSql = "UPDATE savings SET montant = ?, description = ?, date_economie = ?, " +
                "frequence = ?, date_fin = ?, derniere_generation = COALESCE(?, derniere_generation) WHERE id_economie = ?";
        String insertionSql = "INSERT INTO savings (id_user, montant, description, date_economie, " +
                "frequence, date_fin, derniere_generation) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement maj = DatabaseConnection.preparer(conn, majSql);
                 PreparedStatement insertion = DatabaseConnection.preparerAvecCleAuto(conn, insertionSql)) {

                maj.setDouble(1, source.getMontant());
                maj.setString(2, source.getDescription());
                maj.setDate(3, Date.valueOf(source.getDateEconomie()));
                maj.setString(4, source.getFrequence().name());
                maj.setDate(5, source.getDateFin() == null ? null : Date.valueOf(source.getDateFin()));
                maj.setDate(6, source.getDerniereGeneration() == null ? null : Date.valueOf(source.getDerniereGeneration()));
                maj.setInt(7, source.getIdEconomie());

                insertion.setInt(1, suite.getIdUser());
                insertion.setDouble(2, suite.getMontant());
                insertion.setString(3, suite.getDescription());
                insertion.setDate(4, Date.valueOf(suite.getDateEconomie()));
                insertion.setString(5, suite.getFrequence().name());
                insertion.setDate(6, suite.getDateFin() == null ? null : Date.valueOf(suite.getDateFin()));
                LocalDate derniere = suite.getFrequence().estRecurrent() ? suite.getDateEconomie() : null;
                insertion.setDate(7, derniere == null ? null : Date.valueOf(derniere));

                if (maj.executeUpdate() == 0 || insertion.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
                try (ResultSet rs = insertion.getGeneratedKeys()) {
                    if (rs.next()) suite.setIdEconomie(rs.getInt(1));
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
            log.error("SavingDAO.changerMontantRecurrent() — {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean supprimer(int idEconomie) {
        String sql = "DELETE FROM savings WHERE id_economie = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idEconomie);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("SavingDAO.supprimer() — {}", e.getMessage(), e);
            return false;
        }
    }

    public List<Saving> listerParUser(int idUser) {
        String sql = "SELECT * FROM savings WHERE id_user = ? ORDER BY date_economie DESC, id_economie DESC";
        List<Saving> resultats = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) resultats.add(mapper(rs));
            }
        } catch (SQLException e) {
            log.error("SavingDAO.listerParUser() — {}", e.getMessage(), e);
        }
        return resultats;
    }

    public List<Saving> rechercher(int idUser, String motCle, LocalDate dateDebut, LocalDate dateFin) {
        StringBuilder sql = new StringBuilder("SELECT * FROM savings WHERE id_user = ? ");
        List<Object> params = new ArrayList<>();
        params.add(idUser);

        if (motCle != null && !motCle.isBlank()) {
            sql.append("AND description LIKE ? ESCAPE '|' ");
            params.add("%" + SqlUtil.echapperLike(motCle.trim()) + "%");
        }
        if (dateDebut != null) {
            sql.append("AND date_economie >= ? ");
            params.add(Date.valueOf(dateDebut));
        }
        if (dateFin != null) {
            sql.append("AND date_economie <= ? ");
            params.add(Date.valueOf(dateFin));
        }
        sql.append("ORDER BY date_economie DESC, id_economie DESC");

        List<Saving> resultats = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql.toString())) {
            for (int i = 0; i < params.size(); i++) stmt.setObject(i + 1, params.get(i));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) resultats.add(mapper(rs));
            }
        } catch (SQLException e) {
            log.error("SavingDAO.rechercher() — {}", e.getMessage(), e);
        }
        return resultats;
    }

    public double totalParUser(int idUser) {
        String sql = "SELECT COALESCE(SUM(montant), 0) AS total FROM savings WHERE id_user = ?"
                + SqlUtil.jusquAujourdhui("date_economie");
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("total");
            }
        } catch (SQLException e) {
            log.error("SavingDAO.totalParUser() — {}", e.getMessage(), e);
        }
        return 0;
    }

    public double totalAvantAnnee(int idUser, int annee) {
        String sql = "SELECT COALESCE(SUM(montant), 0) AS total FROM savings WHERE id_user = ? AND YEAR(date_economie) < ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            stmt.setInt(2, annee);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("total");
            }
        } catch (SQLException e) {
            log.error("SavingDAO.totalAvantAnnee() — {}", e.getMessage(), e);
        }
        return 0;
    }

    public int nombreParUser(int idUser) {
        String sql = "SELECT COUNT(*) AS nb FROM savings WHERE id_user = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("nb");
            }
        } catch (SQLException e) {
            log.error("SavingDAO.nombreParUser() — {}", e.getMessage(), e);
        }
        return 0;
    }

    public double totalParMois(int idUser, YearMonth mois) {
        String sql = "SELECT COALESCE(SUM(montant), 0) AS total FROM savings " +
                "WHERE id_user = ? AND YEAR(date_economie) = ? AND MONTH(date_economie) = ?"
                + SqlUtil.jusquAujourdhui("date_economie");
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            stmt.setInt(2, mois.getYear());
            stmt.setInt(3, mois.getMonthValue());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("total");
            }
        } catch (SQLException e) {
            log.error("SavingDAO.totalParMois() — {}", e.getMessage(), e);
        }
        return 0;
    }

    public int genererRecurrences(int idUser) {
        LocalDate aujourdhui = LocalDate.now();
        String selection = "SELECT * FROM savings WHERE id_user = ? AND frequence <> 'PONCTUEL' AND id_source IS NULL";
        String insertion = "INSERT INTO savings (id_user, montant, description, date_economie, " +
                "frequence, id_source) VALUES (?, ?, ?, ?, 'PONCTUEL', ?)";
        String miseAJour = "UPDATE savings SET derniere_generation = ? WHERE id_economie = ?";

        int crees = 0;
        try (Connection conn = DatabaseConnection.getConnection()) {
            List<Saving> sources = new ArrayList<>();
            try (PreparedStatement stmt = DatabaseConnection.preparer(conn, selection)) {
                stmt.setInt(1, idUser);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) sources.add(mapper(rs));
                }
            }
            // Le calcul des occurrences se ré-ancre sur la date de début : sans ce
            // filtre, changer cette date réinsérerait des lignes déjà en base.
            Map<Integer, Set<LocalDate>> genrees = SqlUtil.datesGenerees(conn, "savings", "date_economie", idUser);
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ins = DatabaseConnection.preparer(conn, insertion);
                 PreparedStatement maj = DatabaseConnection.preparer(conn, miseAJour)) {
                for (Saving source : sources) {
                    LocalDate limite = source.getDateFin() != null && source.getDateFin().isBefore(aujourdhui)
                            ? source.getDateFin() : aujourdhui;
                    LocalDate apres = source.getDerniereGeneration() != null
                            ? source.getDerniereGeneration() : source.getDateEconomie();
                    List<LocalDate> dates = source.getFrequence()
                            .occurrencesEntre(source.getDateEconomie(), apres, limite);
                    dates.removeAll(genrees.getOrDefault(source.getIdEconomie(), Set.of()));
                    if (dates.isEmpty()) continue;
                    for (LocalDate date : dates) {
                        ins.setInt(1, source.getIdUser());
                        ins.setDouble(2, source.getMontant());
                        ins.setString(3, source.getDescription());
                        ins.setDate(4, Date.valueOf(date));
                        ins.setInt(5, source.getIdEconomie());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                    maj.setDate(1, Date.valueOf(dates.get(dates.size() - 1)));
                    maj.setInt(2, source.getIdEconomie());
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
            log.error("SavingDAO.genererRecurrences() — {}", e.getMessage(), e);
        }
        return crees;
    }

    public void migrerSiNecessaire() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (Statement st = DatabaseConnection.creerStatement(conn, DatabaseConnection.TIMEOUT_MIGRATION)) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS savings (" +
                        "id_economie INT AUTO_INCREMENT PRIMARY KEY, " +
                        "id_user INT NOT NULL, " +
                        "montant DECIMAL(15,2) NOT NULL CHECK (montant >= 0), " +
                        "description VARCHAR(255), " +
                        "date_economie DATE NOT NULL, " +
                        "frequence ENUM('PONCTUEL','QUOTIDIEN','HEBDOMADAIRE','MENSUEL') NOT NULL DEFAULT 'PONCTUEL', " +
                        "date_fin DATE NULL, " +
                        "derniere_generation DATE NULL, " +
                        "id_source INT NULL, " +
                        "date_creation DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "CONSTRAINT fk_economie_user FOREIGN KEY (id_user) REFERENCES users(id_user) ON DELETE CASCADE, " +
                        "CONSTRAINT fk_economie_source FOREIGN KEY (id_source) REFERENCES savings(id_economie) ON DELETE CASCADE" +
                        ") ENGINE=InnoDB");
            }
        } catch (SQLException e) {
            log.warn("Migration savings ignorée : {}", e.getMessage());
        }
    }

    private Saving mapper(ResultSet rs) throws SQLException {
        Saving e = new Saving();
        e.setIdEconomie(rs.getInt("id_economie"));
        e.setIdUser(rs.getInt("id_user"));
        e.setMontant(rs.getDouble("montant"));
        e.setDescription(rs.getString("description"));
        e.setDateEconomie(rs.getDate("date_economie").toLocalDate());
        Timestamp ts = rs.getTimestamp("date_creation");
        if (ts != null) e.setDateCreation(ts.toLocalDateTime());
        e.setFrequence(Frequency.depuisBase(rs.getString("frequence")));
        Date fin = rs.getDate("date_fin");
        if (fin != null) e.setDateFin(fin.toLocalDate());
        Date derniere = rs.getDate("derniere_generation");
        if (derniere != null) e.setDerniereGeneration(derniere.toLocalDate());
        int source = rs.getInt("id_source");
        if (!rs.wasNull()) e.setIdSource(source);
        return e;
    }
}
