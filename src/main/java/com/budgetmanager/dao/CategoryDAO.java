package com.budgetmanager.dao;

import com.budgetmanager.config.DatabaseConnection;
import com.budgetmanager.model.Category;
import com.budgetmanager.model.CategoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {

    private static final Logger log = LoggerFactory.getLogger(CategoryDAO.class);

    public boolean creer(Category categorie) {
        String sql = "INSERT INTO categories (id_user, nom, type) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, categorie.getIdUser());
            stmt.setString(2, categorie.getNom());
            stmt.setString(3, categorie.getType().name());

            int lignes = stmt.executeUpdate();
            if (lignes > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) categorie.setIdCategorie(rs.getInt(1));
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            log.error("CategoryDAO.creer() — {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean modifier(Category categorie) {
        String sql = "UPDATE categories SET nom = ?, type = ? WHERE id_categorie = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, categorie.getNom());
            stmt.setString(2, categorie.getType().name());
            stmt.setInt(3, categorie.getIdCategorie());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("CategoryDAO.modifier() — {}", e.getMessage(), e);
            return false;
        }
    }

    public int nombreUtilisations(int idCategorie) {
        String sql = "SELECT (SELECT COUNT(*) FROM incomes WHERE id_categorie = ?) "
                + "+ (SELECT COUNT(*) FROM expenses WHERE id_categorie = ?) AS nb";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idCategorie);
            stmt.setInt(2, idCategorie);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt("nb");
            }
        } catch (SQLException e) {
            log.error("CategoryDAO.nombreUtilisations() — {}", e.getMessage(), e);
        }
        return 0;
    }

    public boolean supprimer(int idCategorie) {
        String sql = "DELETE FROM categories WHERE id_categorie = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idCategorie);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("CategoryDAO.supprimer() — {}", e.getMessage(), e);
            return false;
        }
    }

    public List<Category> listerParUser(int idUser) {
        return lister("SELECT * FROM categories WHERE id_user = ? ORDER BY nom", idUser, null);
    }

    public List<Category> listerParUserEtType(int idUser, CategoryType type) {
        return lister("SELECT * FROM categories WHERE id_user = ? AND type = ? ORDER BY nom", idUser, type);
    }

    private List<Category> lister(String sql, int idUser, CategoryType type) {
        List<Category> categories = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, idUser);
            if (type != null) stmt.setString(2, type.name());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) categories.add(mapper(rs));
            }
        } catch (SQLException e) {
            log.error("CategoryDAO.lister() — {}", e.getMessage(), e);
        }
        return categories;
    }

    private Category mapper(ResultSet rs) throws SQLException {
        Category c = new Category();
        c.setIdCategorie(rs.getInt("id_categorie"));
        c.setIdUser(rs.getInt("id_user"));
        c.setNom(rs.getString("nom"));
        c.setType(CategoryType.valueOf(rs.getString("type")));
        Timestamp ts = rs.getTimestamp("date_creation");
        if (ts != null) c.setDateCreation(ts.toLocalDateTime());
        return c;
    }
}
