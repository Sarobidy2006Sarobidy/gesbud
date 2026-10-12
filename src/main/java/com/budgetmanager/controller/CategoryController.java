package com.budgetmanager.controller;

import com.budgetmanager.dao.CategoryDAO;
import com.budgetmanager.model.Category;
import com.budgetmanager.model.CategoryType;
import com.budgetmanager.model.User;
import com.budgetmanager.util.AlertUtil;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.RaccourcisClavier;
import com.budgetmanager.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CategoryController implements RaccourcisClavier {

    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);

    @FXML private TextField champNom;
    @FXML private Label pastilleNombre;
    @FXML private ComboBox<CategoryType> comboType;
    @FXML private TableView<Category> tableCategories;
    @FXML private TableColumn<Category, String> colonneNom;
    @FXML private TableColumn<Category, CategoryType> colonneType;

    @FXML private Button boutonAjouter;
    @FXML private Button boutonModifier;
    @FXML private Button boutonSupprimer;
    @FXML private TextField champRecherche;
    @FXML private Button boutonFiltrer;
    @FXML private Button boutonReinitialiser;

    /** Contrôleur de la barre latérale incluse (fx:id="sidebar" -> champ "sidebarController"). */
    @FXML private SidebarController sidebarController;

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ObservableList<Category> donnees = FXCollections.observableArrayList();
    private Category categorieSelectionnee;

    @FXML
    public void initialize() {
        sidebarController.setActive("categories");

        boutonAjouter.setGraphic(Icons.plusCircle(15, Color.WHITE));
        boutonModifier.setGraphic(Icons.edit(15, Color.web("#374151")));
        boutonSupprimer.setGraphic(Icons.trash(15, Color.web("#b91c1c")));

        comboType.getItems().addAll(CategoryType.REVENU, CategoryType.DEPENSE);
        colonneNom.setCellValueFactory(cellData -> cellData.getValue().nomProperty());
        colonneType.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
        tableCategories.setItems(donnees);

        // La puce de l'en-tête compte les catégories réellement affichées.
        pastilleNombre.textProperty().bind(
                javafx.beans.binding.Bindings.size(tableCategories.getItems()).asString());

        tableCategories.getSelectionModel().selectedItemProperty().addListener((obs, ancien, nouveau) -> {
            categorieSelectionnee = nouveau;
            if (nouveau != null) {
                champNom.setText(nouveau.getNom());
                comboType.setValue(nouveau.getType());
            }
        });

        // Initialisation des boutons de filtre
        boutonFiltrer.setGraphic(Icons.filter(15, Color.WHITE));
        boutonReinitialiser.setGraphic(Icons.rotateCcw(15, Color.web("#374151")));

        // Focus automatique sur le premier champ
        javafx.application.Platform.runLater(() -> champNom.requestFocus());

        chargerCategories();
    }

    private void chargerCategories() {
        User utilisateur = SessionManager.getUtilisateurConnecte();
        Task<java.util.List<Category>> tache = new Task<>() {
            @Override
            protected java.util.List<Category> call() {
                return categoryDAO.listerParUser(utilisateur.getIdUser());
            }
        };
        tache.setOnSucceeded(evt -> donnees.setAll(tache.getValue()));
        tache.setOnFailed(evt -> log.error("Chargement catégories échoué", tache.getException()));
        com.budgetmanager.util.DbExecutor.soumettre(tache);
    }

    @FXML
    public void ajouterCategorie() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        String nom = champNom.getText().trim();
        CategoryType type = comboType.getValue();

        if (nom.isEmpty() || type == null) {
            AlertUtil.afficherErreur("Champs manquants", "Veuillez renseigner le nom et le type.");
            return;
        }

        User utilisateur = SessionManager.getUtilisateurConnecte();
        Category categorie = new Category(utilisateur.getIdUser(), nom, type);
        if (categoryDAO.creer(categorie)) {
            chargerCategories();
            viderFormulaire();
        } else {
            AlertUtil.afficherErreur("Erreur", "Impossible d'ajouter la catégorie.");
        }
    }

    /** Ctrl+N : même action que le bouton « Ajouter ». */
    @Override
    public void auRaccourciAjouter() { ajouterCategorie(); }

    @FXML
    public void modifierCategorie() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        if (categorieSelectionnee == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner une catégorie à modifier.");
            return;
        }
        categorieSelectionnee.setNom(champNom.getText().trim());
        categorieSelectionnee.setType(comboType.getValue());

        if (categoryDAO.modifier(categorieSelectionnee)) {
            chargerCategories();
            viderFormulaire();
        } else {
            AlertUtil.afficherErreur("Erreur", "Impossible de modifier la catégorie.");
        }
    }

    @FXML
    public void supprimerCategorie() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        if (categorieSelectionnee == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner une catégorie à supprimer.");
            return;
        }
        int utilisations = categoryDAO.nombreUtilisations(categorieSelectionnee.getIdCategorie());
        if (utilisations > 0) {
            AlertUtil.afficherErreur("Catégorie utilisée", "Cette catégorie est utilisée par " + utilisations
                    + (utilisations > 1 ? " opérations" : " opération")
                    + ". Supprimez ou modifiez d'abord ces opérations, puis réessayez.");
            return;
        }
        if (AlertUtil.confirmer("Confirmation", "Supprimer cette catégorie ?")) {
            if (categoryDAO.supprimer(categorieSelectionnee.getIdCategorie())) {
                chargerCategories();
                viderFormulaire();
            } else {
                AlertUtil.afficherErreur("Erreur", "Impossible de supprimer : des opérations utilisent peut-être encore cette catégorie.");
            }
        }
    }

    private void viderFormulaire() {
        champNom.clear();
        comboType.setValue(null);
        categorieSelectionnee = null;
        tableCategories.getSelectionModel().clearSelection();
    }

    @FXML
    public void rechercherEtFiltrer() {
        String terme = champRecherche.getText().trim().toLowerCase();
        if (terme.isEmpty()) {
            chargerCategories();
            return;
        }
        // Filtre en mémoire sur les catégories chargées
        donnees.removeIf(c -> !c.getNom().toLowerCase().contains(terme));
    }

    @FXML
    public void reinitialiserFiltres() {
        champRecherche.clear();
        chargerCategories();
    }

}
