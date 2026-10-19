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
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catégories : le formulaire écrit, le tableau montre. Ajouter, Modifier, Supprimer
 * sont les trois mêmes gestes que sur les revenus et les dépenses ; le tableau reste
 * en lecture, avec la touche Suppr et le clic droit comme raccourcis.
 */
public class CategoryController implements RaccourcisClavier, com.budgetmanager.util.Rafraichissable {

    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);

    @FXML private Label pastilleNombre;
    @FXML private TextField champNom;
    @FXML private ComboBox<CategoryType> comboType;
    @FXML private TextField champPlafond;
    @FXML private Button boutonAjouter;
    @FXML private Button boutonModifier;
    @FXML private Button boutonSupprimer;
    @FXML private TextField champRecherche;
    @FXML private TableView<Category> tableCategories;
    @FXML private TableColumn<Category, String> colonneNom;
    @FXML private TableColumn<Category, CategoryType> colonneType;
    @FXML private TableColumn<Category, Double> colonneBudget;

    /** Contrôleur de la barre latérale incluse (fx:id="sidebar" -> champ "sidebarController"). */
    @FXML private SidebarController sidebarController;

    private final CategoryDAO categoryDAO = new CategoryDAO();
    /** Catégories chargées de MySQL, telles qu'en base. */
    private final ObservableList<Category> donnees = FXCollections.observableArrayList();
    /** Types tels qu'en base : sert à refuser un changement de volet déjà utilisé. */
    private final Map<Integer, CategoryType> typesEnBase = new HashMap<>();

    private FilteredList<Category> lignesFiltrees;
    private Category categorieSelectionnee;
    /** Ligne qu'un rechargement doit rendre choisie : la modification suit sans re-clic. */
    private Integer idARechoisir;

    private final javafx.beans.value.ChangeListener<Category> listenerSelection =
            (obs, avant, nouveau) -> {
                categorieSelectionnee = nouveau;
                if (nouveau != null) remplirFormulaire(nouveau);
            };

    @FXML
    public void initialize() {
        sidebarController.setActive("categories");

        boutonAjouter.setGraphic(Icons.plusCircle(15, Color.WHITE));
        boutonModifier.setGraphic(Icons.edit(15, Color.web("#374151")));
        boutonSupprimer.setGraphic(Icons.trash(15, Color.web("#b91c1c")));

        colonneNom.setCellValueFactory(cellData -> cellData.getValue().nomProperty());
        colonneType.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
        colonneBudget.setCellValueFactory(cellData -> cellData.getValue().budgetProperty());
        colonneBudget.setCellFactory(colonne -> new TableCell<>() {
            @Override
            protected void updateItem(Double plafond, boolean vide) {
                super.updateItem(plafond, vide);
                // Le montant se lit en colonne : aligné à droite, groupé et monnayé —
                // « 200000 » ne se relit pas et hors Ariary ne dit pas de quelle monnaie
                // il s'agit.
                setAlignment(Pos.CENTER_RIGHT);
                setText(vide || plafond == null ? null
                        : com.budgetmanager.util.CurrencyUtil.formater(plafond,
                                com.budgetmanager.util.CurrencyUtil.deviseCourante()));
            }
        });

        comboType.setItems(FXCollections.observableArrayList(
                CategoryType.REVENU, CategoryType.DEPENSE));
        // Le nom est un VARCHAR(50) : la borne se pose à la frappe, pas après coup.
        champNom.setTextFormatter(com.budgetmanager.util.LongueurFieldFormatter.creer(50));
        champPlafond.setTextFormatter(com.budgetmanager.util.NumberFieldFormatter.creer());

        lignesFiltrees = new FilteredList<>(donnees, categorie -> true);
        SortedList<Category> triees = new SortedList<>(lignesFiltrees);
        triees.comparatorProperty().bind(tableCategories.comparatorProperty());
        tableCategories.setItems(triees);

        // La puce répond à « combien de catégories ai-je », la recherche ne la fait
        // pas descendre : elle ne change que ce qui est visible.
        pastilleNombre.textProperty().bind(Bindings.createStringBinding(
                () -> String.valueOf(donnees.size()), donnees));

        VideEtat.surTable(tableCategories, Icons.tag(26, VideEtat.couleurIcone()),
                "Aucune catégorie",
                "Ajoutez-en une avec le formulaire, ou effacez la recherche avec Échap.");

        tableCategories.getSelectionModel().selectedItemProperty()
                .addListener(new javafx.beans.value.WeakChangeListener<>(listenerSelection));

        installerSuppressionParClavierEtMenuContextuel();
        installerRecherche();

        Platform.runLater(() -> champNom.requestFocus());

        chargerCategories();
    }

    /** Touche Suppr et clic droit sur une ligne : deux raccourcis vers le bouton Supprimer. */
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
                    if (getItem() == null) return;
                    tableCategories.getSelectionModel().select(getIndex());
                    menu.show(this, evenement.getScreenX(), evenement.getScreenY());
                });
            }
        });
    }

    /** La recherche filtre en mémoire, sans jamais toucher à la base. */
    private void installerRecherche() {
        champRecherche.textProperty().addListener((obs, avant, texte) -> {
            String terme = texte == null ? "" : texte.trim().toLowerCase();
            lignesFiltrees.setPredicate(categorie -> categorie.getNom() != null
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
            typesEnBase.clear();
            for (Category categorie : chargees) {
                typesEnBase.put(categorie.getIdCategorie(), categorie.getType());
            }
            if (idARechoisir != null) {
                int cible = idARechoisir;
                idARechoisir = null;
                chargees.stream()
                        .filter(categorie -> categorie.getIdCategorie() == cible)
                        .findFirst()
                        .ifPresent(categorie -> tableCategories.getSelectionModel().select(categorie));
            }
        });
        tache.setOnFailed(evenement -> log.error("Chargement catégories échoué", tache.getException()));
        com.budgetmanager.util.DbExecutor.soumettre(tache);
    }

    @FXML
    public void ajouterCategorie() {
        if (!DbHeartbeat.ok()) return;
        Category nouvelle = lireFormulaire(null);
        if (nouvelle == null) return;

        if (categoryDAO.creer(nouvelle)) {
            chargerCategories();
            viderFormulaire();
        } else {
            AlertUtil.afficherErreur("Erreur", "Impossible d'enregistrer la catégorie.");
        }
    }

    @FXML
    public void modifierCategorie() {
        if (!DbHeartbeat.ok()) return;
        if (categorieSelectionnee == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner une catégorie à modifier.");
            return;
        }
        Category modifiee = lireFormulaire(categorieSelectionnee);
        if (modifiee == null) return;

        // Le rechargement remplace les lignes : celle-ci est choisie de sitôt, la main
        // reste sur le formulaire pour l'autre champ à corriger.
        idARechoisir = modifiee.getIdCategorie();
        if (categoryDAO.modifier(modifiee)) {
            chargerCategories();
        } else {
            AlertUtil.afficherErreur("Erreur", "Impossible d'enregistrer la catégorie.");
        }
    }

    @FXML
    public void supprimerCategorie() {
        if (!DbHeartbeat.ok()) return;
        if (categorieSelectionnee == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner une catégorie à supprimer.");
            return;
        }
        supprimer(categorieSelectionnee);
    }

    /** Ctrl+N : le curseur va au champ Nom du formulaire, comme sur les autres pages. */
    @Override
    public void auRaccourciAjouter() {
        viderFormulaire();
    }

    /**
     * Le formulaire contrôlé : la catégorie prête à écrire, ou null une fois le refus
     * dit à l'écran. Nom et type sont exigés, le plafond ne se fixe que sur une dépense.
     *
     * @base la ligne choisie au tableau pour une modification, null pour une création
     */
    private Category lireFormulaire(Category base) {
        String nom = champNom.getText() == null ? "" : champNom.getText().trim();
        CategoryType type = comboType.getValue();

        if (nom.isEmpty() || type == null) {
            AlertUtil.afficherErreur("Champs manquants", "Une catégorie a besoin d'un nom et d'un type.");
            return null;
        }
        // Le champ borne déjà la frappe, mais une collure ou un setText court-circuite
        // le TextFormatter : la limite du VARCHAR(50) est donc redite ici.
        if (nom.length() > 50) {
            AlertUtil.afficherErreur("Nom trop long",
                    "Un nom de catégorie ne dépasse pas 50 caractères.");
            return null;
        }

        Double plafond = com.budgetmanager.util.NumberFieldFormatter.lire(champPlafond.getText());
        if (plafond != null && type == CategoryType.REVENU) {
            AlertUtil.afficherErreur("Plafond impossible",
                    "Un plafond mensuel se fixe sur une catégorie de dépenses, pas sur un revenu.");
            return null;
        }
        if (plafond != null && plafond > 999_999_999_999.99) {
            AlertUtil.afficherErreur("Plafond trop grand",
                    "Un plafond mensuel s'écrit en dessous de 1 000 milliards.");
            return null;
        }

        if (base != null) {
            // Changer le volet (revenus ou dépenses) d'une catégorie déjà référencée
            // laisserait totaux et graphiques en désaccord.
            CategoryType typeEnBase = typesEnBase.get(base.getIdCategorie());
            if (typeEnBase != null && typeEnBase != type
                    && categoryDAO.nombreUtilisations(base.getIdCategorie()) > 0) {
                AlertUtil.afficherErreur("Catégorie utilisée",
                        "Impossible de changer le type : des opérations utilisent déjà cette catégorie. "
                      + "Réaffectez-les ou supprimez-les d'abord.");
                return null;
            }
        }

        User utilisateur = SessionManager.getUtilisateurConnecte();
        Category categorie = base == null
                ? new Category(utilisateur.getIdUser(), nom, type)
                : new Category(base.getIdCategorie(), base.getIdUser(), nom, type, base.getDateCreation());
        categorie.setBudget(plafond);
        return categorie;
    }

    private void remplirFormulaire(Category categorie) {
        champNom.setText(categorie.getNom());
        comboType.setValue(categorie.getType());
        champPlafond.setText(categorie.getBudget() == null ? ""
                : com.budgetmanager.util.NumberFieldFormatter.ecrire(categorie.getBudget()));
    }

    /** Le formulaire repart vide : la main est laissée pour l'entrée suivante. */
    private void viderFormulaire() {
        tableCategories.getSelectionModel().clearSelection();
        categorieSelectionnee = null;
        champNom.clear();
        comboType.setValue(null);
        champPlafond.clear();
        Platform.runLater(() -> champNom.requestFocus());
    }

    private void supprimer(Category categorie) {
        if (categorie == null) return;
        if (!DbHeartbeat.ok()) return;

        int utilisations = categoryDAO.nombreUtilisations(categorie.getIdCategorie());
        if (utilisations > 0) {
            AlertUtil.afficherErreur("Catégorie utilisée", "Cette catégorie est utilisée par " + utilisations
                    + (utilisations > 1 ? " opérations" : " opération")
                    + ". Supprimez ou modifiez d'abord ces opérations, puis réessayez.");
            return;
        }
        if (AlertUtil.confirmer("Confirmation", "Supprimer « " + categorie.getNom() + " » ?")) {
            if (!categoryDAO.supprimer(categorie.getIdCategorie(),
                    SessionManager.getUtilisateurConnecte().getIdUser())) {
                AlertUtil.afficherErreur("Erreur",
                        "Impossible de supprimer : des opérations utilisent peut-être encore cette catégorie.");
            }
            chargerCategories();
            viderFormulaire();
        }
    }
}
