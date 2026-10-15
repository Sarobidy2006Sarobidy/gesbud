package com.budgetmanager.dao;

import com.budgetmanager.config.DatabaseConnection;
import com.budgetmanager.model.Carnet;
import com.budgetmanager.model.SensCarnet;
import com.budgetmanager.model.StatutCarnet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès aux données de la table `carnet`. Le carnet est un bloc-notes : aucune
 * de ses colonnes n'est lue par les revenus, les dépenses ou les économies.
 */
public class CarnetDAO {

    private static final Logger log = LoggerFactory.getLogger(CarnetDAO.class);

    public boolean creer(Carnet ecriture) {
        String sql = "INSERT INTO carnet (id_user, sens, personne, motif, montant, date_debut, date_fin, statut) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparerAvecCleAuto(conn, sql)) {

            stmt.setInt(1, ecriture.getIdUser());
            stmt.setString(2, ecriture.getSens().name());
            stmt.setString(3, ecriture.getPersonne());
            stmt.setString(4, ecriture.getMotif());
            stmt.setDouble(5, ecriture.getMontant());
            stmt.setDate(6, Date.valueOf(ecriture.getDateDebut()));
            stmt.setDate(7, ecriture.getDateFin() == null ? null : Date.valueOf(ecriture.getDateFin()));
            stmt.setString(8, ecriture.getStatut().name());

            if (stmt.executeUpdate() > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) ecriture.setIdCarnet(rs.getInt(1));
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            log.error("CarnetDAO.creer() — {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean modifier(Carnet ecriture) {
        String sql = "UPDATE carnet SET sens = ?, personne = ?, motif = ?, montant = ?, "
                + "date_debut = ?, date_fin = ?, statut = ? WHERE id_carnet = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {

            stmt.setString(1, ecriture.getSens().name());
            stmt.setString(2, ecriture.getPersonne());
            stmt.setString(3, ecriture.getMotif());
            stmt.setDouble(4, ecriture.getMontant());
            stmt.setDate(5, Date.valueOf(ecriture.getDateDebut()));
            stmt.setDate(6, ecriture.getDateFin() == null ? null : Date.valueOf(ecriture.getDateFin()));
            stmt.setString(7, ecriture.getStatut().name());
            stmt.setInt(8, ecriture.getIdCarnet());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("CarnetDAO.modifier() — {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean supprimer(int idCarnet) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn,
                     "DELETE FROM carnet WHERE id_carnet = ?")) {
            stmt.setInt(1, idCarnet);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("CarnetDAO.supprimer() — {}", e.getMessage(), e);
            return false;
        }
    }

    public List<Carnet> listerParUser(int idUser) {
        List<Carnet> ecritures = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn,
                     "SELECT * FROM carnet WHERE id_user = ? ORDER BY date_debut DESC, id_carnet DESC")) {
            stmt.setInt(1, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) ecritures.add(mapper(rs));
            }
        } catch (SQLException e) {
            log.error("CarnetDAO.listerParUser() — {}", e.getMessage(), e);
        }
        return ecritures;
    }

    public void migrerSiNecessaire() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = DatabaseConnection.creerStatement(conn, DatabaseConnection.TIMEOUT_MIGRATION)) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS carnet (" +
                    "id_carnet INT AUTO_INCREMENT PRIMARY KEY, " +
                    "id_user INT NOT NULL, " +
                    "sens ENUM('EMPRUNT','PRET') NOT NULL, " +
                    "personne VARCHAR(80) NOT NULL, " +
                    "motif VARCHAR(255), " +
                    "montant DECIMAL(15,2) NOT NULL CHECK (montant >= 0), " +
                    "date_debut DATE NOT NULL, " +
                    "date_fin DATE NULL, " +
                    "statut ENUM('EN_COURS','REGLE') NOT NULL DEFAULT 'EN_COURS', " +
                    "date_creation DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "CONSTRAINT fk_carnet_user FOREIGN KEY (id_user) REFERENCES users(id_user) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB");
        } catch (SQLException e) {
            log.warn("Migration carnet ignorée : {}", e.getMessage());
        }
    }

    private Carnet mapper(ResultSet rs) throws SQLException {
        Carnet e = new Carnet();
        e.setIdCarnet(rs.getInt("id_carnet"));
        e.setIdUser(rs.getInt("id_user"));
        e.setSens(SensCarnet.depuisBase(rs.getString("sens")));
        e.setPersonne(rs.getString("personne"));
        e.setMotif(rs.getString("motif"));
        e.setMontant(rs.getDouble("montant"));
        e.setDateDebut(rs.getDate("date_debut").toLocalDate());
        Date fin = rs.getDate("date_fin");
        if (fin != null) e.setDateFin(fin.toLocalDate());
        e.setStatut(StatutCarnet.depuisBase(rs.getString("statut")));
        Timestamp ts = rs.getTimestamp("date_creation");
        if (ts != null) e.setDateCreation(ts.toLocalDateTime());
        return e;
    }
}
