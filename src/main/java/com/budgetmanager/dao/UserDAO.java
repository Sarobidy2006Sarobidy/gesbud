package com.budgetmanager.dao;

import com.budgetmanager.config.DatabaseConnection;
import com.budgetmanager.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Accès aux données de la table `users`.
 * Gère l'inscription, la connexion et la mise à jour du profil.
 */
public class UserDAO {

    private static final Logger log = LoggerFactory.getLogger(UserDAO.class);

    public boolean creer(User user) {
        String sql = "INSERT INTO users (nom, prenom, email, pseudo, mot_de_passe, devise, question_securite, reponse_securite) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparerAvecCleAuto(conn, sql)) {

            stmt.setString(1, user.getNom());
            stmt.setString(2, user.getPrenom());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getPseudo());
            stmt.setString(5, user.getMotDePasse());
            stmt.setString(6, user.getDevise());
            stmt.setString(7, user.getQuestionSecurite());
            stmt.setString(8, user.getReponseSecurite());

            int lignes = stmt.executeUpdate();
            if (lignes > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) user.setIdUser(rs.getInt(1));
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            log.error("UserDAO.creer() — {}", e.getMessage(), e);
            return false;
        }
    }

    /** Les comptes anciens ont un mot de passe en clair : on les convertit tous une fois pour toutes. */
    private void hacherLesMotsDePasseEnClair(Connection conn) throws SQLException {
        java.util.Map<Integer, String> enClair = new java.util.LinkedHashMap<>();
        try (PreparedStatement st = DatabaseConnection.preparer(conn,
                     "SELECT id_user, mot_de_passe FROM users WHERE mot_de_passe NOT LIKE 'pbkdf2$%'");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) enClair.put(rs.getInt("id_user"), rs.getString("mot_de_passe"));
        }
        for (java.util.Map.Entry<Integer, String> e : enClair.entrySet()) {
            changerMotDePasse(e.getKey(), com.budgetmanager.util.PasswordUtil.hacher(e.getValue()));
        }
    }

    /** Vrai si la base de données répond. */
    public boolean baseAccessible() {
        try {
            return DatabaseConnection.getConnection().isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    public Optional<User> trouverParIdentifiant(String identifiant) {
        String sql = "SELECT * FROM users WHERE email = ? OR pseudo = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setString(1, identifiant);
            stmt.setString(2, identifiant);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapper(rs));
            }
        } catch (SQLException e) {
            log.error("UserDAO.trouverParIdentifiant() — {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public boolean pseudoDejaUtilise(String pseudo, int idUserIgnore) {
        String sql = "SELECT 1 FROM users WHERE pseudo = ? AND id_user <> ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setString(1, pseudo);
            stmt.setInt(2, idUserIgnore);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("UserDAO.pseudoDejaUtilise() — {}", e.getMessage(), e);
            return false;
        }
    }

    public Optional<User> trouverParEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapper(rs));
            }
        } catch (SQLException e) {
            log.error("UserDAO.trouverParEmail() — {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<User> trouverParId(int idUser) {
        String sql = "SELECT * FROM users WHERE id_user = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setInt(1, idUser);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapper(rs));
            }
        } catch (SQLException e) {
            log.error("UserDAO.trouverParId() — {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    public boolean mettreAJour(User user) {
        String sql = "UPDATE users SET nom = ?, prenom = ?, email = ?, pseudo = ?, devise = ? WHERE id_user = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setString(1, user.getNom());
            stmt.setString(2, user.getPrenom());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getPseudo());
            stmt.setString(5, user.getDevise());
            stmt.setInt(6, user.getIdUser());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("UserDAO.mettreAJour() — {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean changerMotDePasse(int idUser, String nouveauMotDePasseHache) {
        String sql = "UPDATE users SET mot_de_passe = ? WHERE id_user = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setString(1, nouveauMotDePasseHache);
            stmt.setInt(2, idUser);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("UserDAO.changerMotDePasse() — {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean definirQuestionSecurite(int idUser, String question, String reponseHachee) {
        String sql = "UPDATE users SET question_securite = ?, reponse_securite = ? WHERE id_user = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.preparer(conn, sql)) {
            stmt.setString(1, question);
            stmt.setString(2, reponseHachee);
            stmt.setInt(3, idUser);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("UserDAO.definirQuestionSecurite() — {}", e.getMessage(), e);
            return false;
        }
    }

    public void migrerSiNecessaire() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            if (!colonneExiste(meta, conn.getCatalog(), "question_securite")) {
                try (Statement st = DatabaseConnection.creerStatement(conn, DatabaseConnection.TIMEOUT_MIGRATION)) {
                    st.executeUpdate("ALTER TABLE users ADD COLUMN question_securite VARCHAR(150) NULL");
                }
            }
            if (!colonneExiste(meta, conn.getCatalog(), "pseudo")) {
                try (Statement st = DatabaseConnection.creerStatement(conn, DatabaseConnection.TIMEOUT_MIGRATION)) {
                    st.executeUpdate("ALTER TABLE users ADD COLUMN pseudo VARCHAR(30) NULL, "
                            + "ADD UNIQUE INDEX uq_users_pseudo (pseudo)");
                }
            }
            if (!colonneExiste(meta, conn.getCatalog(), "reponse_securite")) {
                try (Statement st = DatabaseConnection.creerStatement(conn, DatabaseConnection.TIMEOUT_MIGRATION)) {
                    st.executeUpdate("ALTER TABLE users ADD COLUMN reponse_securite VARCHAR(255) NULL");
                }
            }
            hacherLesMotsDePasseEnClair(conn);
        } catch (SQLException e) {
            log.warn("Migration users ignorée : {}", e.getMessage());
        }
    }

    private boolean colonneExiste(DatabaseMetaData meta, String base, String colonne) throws SQLException {
        try (ResultSet rs = meta.getColumns(base, null, "users", colonne)) {
            return rs.next();
        }
    }

    private User mapper(ResultSet rs) throws SQLException {
        User user = new User();
        user.setIdUser(rs.getInt("id_user"));
        user.setNom(rs.getString("nom"));
        user.setPrenom(rs.getString("prenom"));
        user.setEmail(rs.getString("email"));
        user.setPseudo(rs.getString("pseudo"));
        user.setMotDePasse(rs.getString("mot_de_passe"));
        user.setDevise(rs.getString("devise"));
        user.setQuestionSecurite(rs.getString("question_securite"));
        user.setReponseSecurite(rs.getString("reponse_securite"));
        Timestamp ts = rs.getTimestamp("date_creation");
        if (ts != null) user.setDateCreation(ts.toLocalDateTime());
        return user;
    }
}
