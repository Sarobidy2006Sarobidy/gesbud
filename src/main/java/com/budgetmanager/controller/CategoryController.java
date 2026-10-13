package com.budgetmanager.controller;

import com.budgetmanager.dao.CategoryDAO;
import com.budgetmanager.model.Category;
import com.budgetmanager.model.CategoryType;
import com.budgetmanager.model.User;
import com.budgetmanager.util.AlertUtil;
import com.budgetmanager.util.DbHeartbeat;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.RaccourcisClavier;
import com.budgetmanager.util.SessionManager;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catégories : le tableau est le seul point de saisie. Double-clic sur une cellule
 * pour modifier, saisie dans la dernière ligne vide pour créer, touche Suppr (ou
 * clic droit) pour retirer — aucun bouton Ajouter / Modifier / Supprimer.
 */
public class CategoryController implements RaccourcisClavier, com.budgetmanager.util.Rafraichissable {

    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);

    @FXML private Label pastilleNombre;
    @FXML private TextField champRecherche;
    @FXML private TableView<Category> tableCategories;
    @FXML private TableColumn<Category, String> colonneNom;
    @FXML private TableColumn<Category, CategoryType> colonneType;

    /** Contrôleur de la barre latérale incluse (fx:id="sidebar" -> champ "sidebarController"). */
    @FXML private SidebarController sidebarController;

    private final CategoryDAO categoryDAO = new CategoryDAO();
    /** Catégories chargées, suivies de la ligne de saisie toujours en dernière position. */
    private final ObservableList<Category> donnees = FXCollections.observableArrayList();
    /** Types tels qu'en base : sert à annuler un changement refusé. */
    private final Map<Integer, CategoryType> typesEnBase = new HashMap<>();

    private FilteredList<Category> lignesFiltrees;
    /** Ligne de saisie affichée en bas du tableau, jamais présente en base. */
    private Category ligneVide;

    @FXML
    public void initialize() {
        sidebarController.setActive("categories");

        User utilisateur = SessionManager.getUtilisateurConnecte();
        ligneVide = new Category(utilisateur.getIdUser(), "", null);

        colonneNom.setCellValueFactory(cellData -> cellData.getValue().nomProperty());
        colonneType.setCellValueFactory(cellData -> cellData.getValue().typeProperty());

        colonneNom.setCellFactory(colonne -> new TextFieldTableCell<>() {
            @Override
            public void updateItem(String nom, boolean vide) {
                super.updateItem(nom, vide);
                TableRow<Category> ligne = getTableRow();
                if (ligne != null && ligne.getItem() == ligneVide) {
                    setText(nom == null || nom.isEmpty() ? "Nouvelle catégorie…" : nom);
                }
            }
        });
        colonneType.setCellFactory(ComboBoxTableCell.forTableColumn(
                CategoryType.REVENU, CategoryType.DEPENSE));

        lignesFiltrees = new FilteredList<>(donnees, categorie -> true);
        SortedList<Category> triees = new SortedList<>(lignesFiltrees);
        // Tri réellement appliqué, la ligne de saisie restant épinglée en dernier
        // quelle que soit la colonne triée.
        triees.comparatorProperty().bind(Bindings.createObjectBinding(
                () -> {
                    Comparator<Category> triColonnes = tableCategories.getComparator();
                    Comparator<Category> avecLigneDeSaisie = (a, b) -> {
                        if (a == b) return 0;
                        if (a == ligneVide) return 1;
                        if (b == ligneVide) return -1;
                        return triColonnes == null ? 0 : triColonnes.compare(a, b);
                    };
                    return avecLigneDeSaisie;
                },
                tableCategories.comparatorProperty()));
        tableCategories.setItems(triees);
        tableCategories.setEditable(true);

        // La puce compte les catégories enregistrées, hors ligne de saisie.
        pastilleNombre.textProperty().bind(Bindings.createStringBinding(
                () -> String.valueOf(Math.max(0, lignesFiltrees.size() - 1)), lignesFiltrees));

        colonneNom.setOnEditCommit(evenement -> enregistrerModification(evenement.getRowValue()));
        colonneType.setOnEditCommit(evenement -> enregistrerModification(evenement.getRowValue()));

        installerSuppressionParClavierEtMenuContextuel();
        installerRecherche();

        javafx.application.Platform.runLater(() -> champRecherche.requestFocus());

        chargerCategories();
    }

    /** Touche Suppr et clic droit sur une ligne : la suppression passe par le tableau. */
    private void installerSuppressionParClavierEtMenuContextuel() {
        tableCategories.setOnKeyPressed(evenement -> {
            if (evenement.getCode() == KeyCode.DELETE) {
                evenement.consume();
                supprimer(tableCategories.getSelectionModel().getSelectedItem());
            }
        });

        tableCategories.setRowFactory(table -> new TableRow<>() {
            {
                MenuItem retirer = new MenuItem("Supprimer la catégorie");
                retirer.setGraphic(Icons.trash(14, Color.web("#b91c1c")));
                retirer.setOnAction(evenement -> supprimer(getItem()));
                ContextMenu menu = new ContextMenu(retirer);
                setOnContextMenuRequested(evenement -> {
                    if (getItem() == null || getItem() == ligneVide) return;
                    tableCategories.getSelectionModel().select(getIndex());
                    menu.show(this, evenement.getScreenX(), evenement.getScreenY());
                });
            }

            @Override
            protected void updateItem(Category categorie, boolean vide) {
                super.updateItem(categorie, vide);
                getStyleClass().remove("ligne-saisie-categorie");
                if (categorie == ligneVide) getStyleClass().add("ligne-saisie-categorie");
            }
        });
    }

    /** La recherche filtre en mémoire, sans jamais retirer une ligne du modèle. */
    private void installerRecherche() {
        champRecherche.textProperty().addListener((obs, avant, texte) -> {
            String terme = texte == null ? "" : texte.trim().toLowerCase();
            lignesFiltrees.setPredicate(terme.isEmpty()
                    ? categorie -> true
                    : categorie -> categorie == ligneVide
                            || (categorie.getNom() != null
                                    && categorie.getNom().toLowerCase().contains(terme)));
        });
        champRecherche.setOnKeyPressed(evenement -> {
            if (evenement.getCode() == KeyCode.ESCAPE) champRecherche.clear();
        });
    }

    /** Redemandé par la navigation quand la vue revient du cache. */
    @Override
    public void rafraichirDonnees() {
        chargerCategories();
    }

    private void chargerCategories() {
        User utilisateur = SessionManager.getUtilisateurConnecte();
        Task<List<Category>> tache = new Task<>() {
            @Override
            protected List<Category> call() {
                return categoryDAO.listerParUser(utilisateur.getIdUser());
            }
        };
        tache.setOnSucceeded(evenement -> {
            List<Category> chargees = tache.getValue();
            donnees.setAll(chargees);
            donnees.add(ligneVide);
            typesEnBase.clear();
            for (Category categorie : chargees) {
                typesEnBase.put(categorie.getIdCategorie(), categorie.getType());
            }
        });
        tache.setOnFailed(evenement -> log.error("Chargement catégories échoué", tache.getException()));
        com.budgetmanager.util.DbExecutor.soumettre(tache);
    }

    /**
     * Écrit en base la ligne validée dans le tableau : création si c'est la ligne
     * de saisie, mise à jour sinon. Tout refus recharge la liste depuis MySQL, la
     * cellule retrouve donc d'elle-même sa valeur précédente.
     */
    private void enregistrerModification(Category categorie) {
        if (categorie == null) return;
        String nom = categorie.getNom() == null ? "" : categorie.getNom().trim();
        CategoryType type = categorie.getType();

        if (nom.isEmpty() && type == null) return;   // ligne de saisie abandonnée

        if (!DbHeartbeat.ok()) { chargerCategories(); return; }

        if (nom.isEmpty() || type == null) {
            AlertUtil.afficherErreur("Champs manquants",
                    "Une catégorie a besoin d'un nom et d'un type.");
            chargerCategories();
            return;
        }

        // Le type décide du volet (revenus ou dépenses) où apparaît la catégorie.
        // Le changer alors que des opérations la référencent déjà laisserait les
        // totaux et les graphiques en désaccord.
        CategoryType typeEnBase = typesEnBase.get(categorie.getIdCategorie());
        if (categorie.getIdCategorie() != 0 && typeEnBase != null && typeEnBase != type
                && categoryDAO.nombreUtilisations(categorie.getIdCategorie()) > 0) {
            AlertUtil.afficherErreur("Catégorie utilisée",
                    "Impossible de changer le type : des opérations utilisent déjà cette catégorie. "
                  + "Réaffectez-les ou supprimez-les d'abord.");
            chargerCategories();
            return;
        }

        categorie.setNom(nom);
        boolean enregistree = categorie.getIdCategorie() == 0
                ? categoryDAO.creer(categorie)
                : categoryDAO.modifier(categorie);

        if (!enregistree) {
            AlertUtil.afficherErreur("Erreur", "Impossible d'enregistrer la catégorie.");
        }
        // creer() attribue une clé à la ligne de saisie : on la remet à vide, elle
        // doit rester disponible pour l'entrée suivante.
        ligneVide.setIdCategorie(0);
        ligneVide.setNom("");
        ligneVide.setType(null);
        chargerCategories();
    }

    private void supprimer(Category categorie) {
        if (categorie == null || categorie == ligneVide) return;
        if (!DbHeartbeat.ok()) return;

        int utilisations = categoryDAO.nombreUtilisations(categorie.getIdCategorie());
        if (utilisations > 0) {
            AlertUtil.afficherErreur("Catégorie utilisée", "Cette catégorie est utilisée par " + utilisations
                    + (utilisations > 1 ? " opérations" : " opération")
                    + ". Supprimez ou modifiez d'abord ces opérations, puis réessayez.");
            return;
        }
        if (AlertUtil.confirmer("Confirmation", "Supprimer « " + categorie.getNom() + " » ?")) {
            if (!categoryDAO.supprimer(categorie.getIdCategorie())) {
                AlertUtil.afficherErreur("Erreur",
                        "Impossible de supprimer : des opérations utilisent peut-être encore cette catégorie.");
            }
            chargerCategories();
        }
    }

    /** Ctrl+N : place le curseur d'édition dans la ligne de saisie du bas du tableau. */
    @Override
    public void auRaccourciAjouter() {
        int index = tableCategories.getItems().size() - 1;
        if (index < 0) return;
        tableCategories.scrollTo(index);
        tableCategories.edit(index, colonneNom);
    }
}
