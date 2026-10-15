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
import com.budgetmanager.util.VideEtat;
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
    @FXML private TableColumn<Category, Double> colonneBudget;

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
        colonneBudget.setCellValueFactory(cellData -> cellData.getValue().budgetProperty());

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
        colonneBudget.setCellFactory(colonne -> new TextFieldTableCell<>(convertisseurPlafond()) {
            @Override
            public void updateItem(Double plafond, boolean vide) {
                super.updateItem(plafond, vide);
                // Le montant se lit en colonne : aligné à droite comme les autres.
                setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            }
        });

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

        // La puce compte les catégories enregistrées, hors ligne de saisie —
        // celle-ci quitte les résultats dès qu'un filtre est actif.
        pastilleNombre.textProperty().bind(Bindings.createStringBinding(
                () -> String.valueOf(lignesFiltrees.stream().filter(c -> c != ligneVide).count()),
                lignesFiltrees));

        VideEtat.surTable(tableCategories, Icons.tag(26, VideEtat.couleurIcone()),
                "Aucune catégorie trouvée",
                "Effacez la recherche (Échap) pour retrouver la liste, ou laissez la recherche "
                        + "vide et saisissez un nom dans la dernière ligne du tableau.");

        colonneNom.setOnEditCommit(evenement -> enregistrerModification(evenement.getRowValue()));
        colonneType.setOnEditCommit(evenement -> enregistrerModification(evenement.getRowValue()));
        colonneBudget.setOnEditCommit(evenement -> enregistrerModification(evenement.getRowValue()));

        installerSuppressionParClavierEtMenuContextuel();
        installerRecherche();

        javafx.application.Platform.runLater(() -> champRecherche.requestFocus());

        chargerCategories();
    }

    /**
     * Plafond mensuel : la cellule affiche et attend un nombre brut, sans symbole de
     * devise — c'est ce qu'elle doit pouvoir relire après saisie. Vide = aucun plafond.
     */
    private static javafx.util.StringConverter<Double> convertisseurPlafond() {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(Double plafond) {
                return plafond == null ? "" : com.budgetmanager.util.NumberFieldFormatter.ecrire(plafond);
            }

            @Override
            public Double fromString(String texte) {
                return com.budgetmanager.util.NumberFieldFormatter.lire(texte);
            }
        };
    }

    /** Touche Suppr et clic droit sur une ligne : la suppression passe par le tableau. */
    private void installerSuppressionParClavierEtMenuContextuel() {        tableCategories.setOnKeyPressed(evenement -> {
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
            // La ligne de saisie disparaît dès qu'un filtre est actif : sans elle,
            // une recherche sans résultat montre l'état vide au lieu d'une unique
            // ligne « Nouvelle catégorie… » qui ferait croire à un bug.
            lignesFiltrees.setPredicate(terme.isEmpty()
                    ? categorie -> true
                    : categorie -> categorie != ligneVide
                            && categorie.getNom() != null
                            && categorie.getNom().toLowerCase().contains(terme));
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
            // La ligne de saisie se valide cellule par cellule : Tab, Entrée ou un
            // clic atteint la colonne Type avant que le type soit choisi. Un nom seul
            // n'est pas une faute, et recharger la liste effacerait ce qui est tapé.
            if (categorie == ligneVide) return;
            AlertUtil.afficherErreur("Champs manquants",
                    "Une catégorie a besoin d'un nom et d'un type.");
            chargerCategories();
            return;
        }

        // La colonne nom est un VARCHAR(50) : au-delà, la requête échoue et
        // l'utilisateur ne voit qu'une erreur générique. On borne ici, avant.
        if (nom.length() > 50) {
            AlertUtil.afficherErreur("Nom trop long",
                    "Un nom de catégorie ne dépasse pas 50 caractères.");
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

        // Un plafond se dépasse : il n'a de sens que sur une catégorie de dépenses.
        Double plafond = categorie.getBudget();
        if (plafond != null && type == CategoryType.REVENU) {
            AlertUtil.afficherErreur("Plafond impossible",
                    "Un plafond mensuel se fixe sur une catégorie de dépenses, pas sur un revenu.");
            chargerCategories();
            return;
        }
        if (plafond != null && plafond > 999_999_999_999.99) {
            AlertUtil.afficherErreur("Plafond trop grand",
                    "Un plafond mensuel s'écrit en dessous de 1 000 milliards.");
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
        ligneVide.setBudget(null);
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
        // Une recherche active masque la ligne de saisie : Ctrl+N la ramène d'abord.
        champRecherche.clear();
        int index = tableCategories.getItems().size() - 1;
        if (index < 0) return;
        tableCategories.scrollTo(index);
        tableCategories.edit(index, colonneNom);
    }
}
