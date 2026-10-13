package com.budgetmanager.controller;

import com.budgetmanager.dao.CategoryDAO;
import com.budgetmanager.dao.ExpenseDAO;
import com.budgetmanager.dao.IncomeDAO;
import com.budgetmanager.dao.SavingDAO;
import com.budgetmanager.model.*;
import com.budgetmanager.util.DbExecutor;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HistoryController implements com.budgetmanager.util.Rafraichissable {

    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);

    /** Nombre d'opérations affichées par page. */
    private static final int TAILLE_PAGE = 50;

    /** Ligne unifiée représentant soit un revenu, soit une dépense, pour l'affichage. */
    public static class Operation {
        private final String    type;
        private final String    categorie;
        private final double    montant;
        private final String    description;
        private final LocalDate date;

        public Operation(String type, String categorie, double montant,
                         String description, LocalDate date) {
            this.type        = type;
            this.categorie   = categorie;
            this.montant     = montant;
            this.description = description;
            this.date        = date;
        }

        public String    getType()        { return type; }
        public String    getCategorie()   { return categorie; }
        public double    getMontant()     { return montant; }
        public String    getDescription() { return description; }
        public LocalDate getDate()        { return date; }
    }

    // --- Filtres ---
    @FXML private TextField            champRecherche;
    @FXML private ComboBox<String>     comboFiltreType;
    @FXML private ComboBox<Category>   comboFiltreCategorie;
    @FXML private DatePicker           champFiltreDateDebut;
    @FXML private DatePicker           champFiltreDateFin;

    // --- Tableau ---
    @FXML private TableView<Operation>           tableHistorique;
    @FXML private TableColumn<Operation, String> colonneType;
    @FXML private TableColumn<Operation, String> colonneCategorie;
    @FXML private TableColumn<Operation, Double> colonneMontant;
    @FXML private TableColumn<Operation, String> colonneDescription;
    @FXML private TableColumn<Operation, LocalDate> colonneDate;

    // --- Pagination ---
    @FXML private Button boutonPremiere;
    @FXML private Button boutonPrecedente;
    @FXML private Button boutonSuivante;
    @FXML private Button boutonDerniere;
    @FXML private Label  labelPagination;
    @FXML private Label  pastilleNombre;

    // --- Boutons filtre ---
    @FXML private Button boutonFiltrer;
    @FXML private Button boutonReinitialiser;

    @FXML private SidebarController sidebarController;

    private final IncomeDAO  incomeDAO  = new IncomeDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final SavingDAO  savingDAO  = new SavingDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    /** Toutes les opérations chargées (liste complète en mémoire). */
    private List<Operation> toutesOperations = new ArrayList<>();
    /** Page courante (0-based). */
    private int pageCourante = 0;
    /** Données de la page courante affichées dans la TableView. */
    private final ObservableList<Operation> donnees = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        sidebarController.setActive("historique");

        boutonFiltrer.setGraphic(Icons.filter(15, Color.WHITE));
        boutonReinitialiser.setGraphic(Icons.rotateCcw(15, Color.web("#374151")));

        User utilisateur = SessionManager.getUtilisateurConnecte();

        comboFiltreType.getItems().addAll("Tous", "Revenu", "Dépense", "Économie");
        comboFiltreType.getSelectionModel().selectFirst();

        // Chargement des catégories en arrière-plan.
        Task<List<Category>> tacheCats = new Task<>() {
            @Override protected List<Category> call() {
                return categoryDAO.listerParUser(utilisateur.getIdUser());
            }
        };
        tacheCats.setOnSucceeded(evt ->
            comboFiltreCategorie.setItems(FXCollections.observableArrayList(tacheCats.getValue())));
        DbExecutor.soumettre(tacheCats);

        colonneType.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getType()));
        colonneCategorie.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getCategorie()));
        colonneMontant.setCellValueFactory(c ->
            new javafx.beans.property.SimpleDoubleProperty(c.getValue().getMontant()).asObject());
        com.budgetmanager.util.TableUtil.formaterMontantsAvecType(
                colonneMontant, Operation::getType);
        colonneDescription.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getDescription()));
        colonneDate.setCellValueFactory(c ->
            new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getDate()));

        tableHistorique.setItems(donnees);
        tableHistorique.getSortOrder().add(colonneDate);

        chargerHistorique();
    }

    // ---------------------------------------------------------------------------
    // Chargement
    // ---------------------------------------------------------------------------

    /** Redemandé par la navigation quand la vue revient du cache. */
    @Override
    public void rafraichirDonnees() {
        chargerHistorique();
    }

    private void chargerHistorique() {
        User utilisateur = SessionManager.getUtilisateurConnecte();
        int idUser = utilisateur.getIdUser();

        Task<List<Operation>> tache = new Task<>() {
            @Override
            protected List<Operation> call() {
                incomeDAO.genererRecurrences(idUser);
                expenseDAO.genererRecurrences(idUser);
                savingDAO.genererRecurrences(idUser);

                List<Operation> ops = new ArrayList<>();
                for (Income r : incomeDAO.listerParUser(idUser)) {
                    ops.add(new Operation("Revenu", r.getNomCategorie(),
                            r.getMontant(), r.getDescription(), r.getDateRevenu()));
                }
                for (Expense d : expenseDAO.listerParUser(idUser)) {
                    ops.add(new Operation("Dépense", d.getNomCategorie(),
                            d.getMontant(), d.getDescription(), d.getDateDepense()));
                }
                for (Saving e : savingDAO.listerParUser(idUser)) {
                    ops.add(new Operation("Économie", "—",
                            e.getMontant(), e.getDescription(), e.getDateEconomie()));
                }
                ops.sort(Comparator.comparing(Operation::getDate).reversed());
                return ops;
            }
        };
        tache.setOnSucceeded(evt -> {
            toutesOperations = tache.getValue();
            pageCourante = 0;
            afficherPage();
        });
        tache.setOnFailed(evt -> log.error("Chargement historique échoué", tache.getException()));
        DbExecutor.soumettre(tache);
    }

    // ---------------------------------------------------------------------------
    // Pagination
    // ---------------------------------------------------------------------------

    /**
     * Affiche la tranche de {@value #TAILLE_PAGE} opérations correspondant
     * à {@code pageCourante} et met à jour le label et les boutons.
     */
    private void afficherPage() {
        int total = toutesOperations.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / TAILLE_PAGE));

        // Clamp de sécurité.
        if (pageCourante >= totalPages) pageCourante = totalPages - 1;
        if (pageCourante < 0)           pageCourante = 0;

        int debut = pageCourante * TAILLE_PAGE;
        int fin   = Math.min(debut + TAILLE_PAGE, total);

        donnees.setAll(toutesOperations.subList(debut, fin));

        pastilleNombre.setText(String.valueOf(total));
        labelPagination.setText(
                "Page " + (pageCourante + 1) + " / " + totalPages);

        boutonPremiere.setDisable(pageCourante == 0);
        boutonPrecedente.setDisable(pageCourante == 0);
        boutonSuivante.setDisable(pageCourante >= totalPages - 1);
        boutonDerniere.setDisable(pageCourante >= totalPages - 1);
    }

    @FXML public void allerPremierePage()  { pageCourante = 0; afficherPage(); }
    @FXML public void allerPagePrecedente(){ pageCourante--; afficherPage(); }
    @FXML public void allerPageSuivante()  { pageCourante++; afficherPage(); }
    @FXML public void allerDernierePage()  {
        int total = toutesOperations.size();
        pageCourante = Math.max(0, (int) Math.ceil((double) total / TAILLE_PAGE) - 1);
        afficherPage();
    }

    // ---------------------------------------------------------------------------
    // Filtres
    // ---------------------------------------------------------------------------

    @FXML
    public void rechercherEtFiltrer() {
        User utilisateur = SessionManager.getUtilisateurConnecte();
        String motCle      = champRecherche.getText();
        String typeFiltre  = comboFiltreType.getValue();
        Category catFiltre = comboFiltreCategorie.getValue();
        Integer idCategorie = catFiltre != null ? catFiltre.getIdCategorie() : null;
        LocalDate debut    = champFiltreDateDebut.getValue();
        LocalDate fin      = champFiltreDateFin.getValue();

        Task<List<Operation>> tache = new Task<>() {
            @Override
            protected List<Operation> call() {
                List<Operation> ops = new ArrayList<>();

                if (typeFiltre == null || typeFiltre.equals("Tous") || typeFiltre.equals("Revenu")) {
                    for (Income r : incomeDAO.rechercher(utilisateur.getIdUser(), motCle, idCategorie, debut, fin)) {
                        ops.add(new Operation("Revenu", r.getNomCategorie(),
                                r.getMontant(), r.getDescription(), r.getDateRevenu()));
                    }
                }
                if (typeFiltre == null || typeFiltre.equals("Tous") || typeFiltre.equals("Dépense")) {
                    for (Expense d : expenseDAO.rechercher(utilisateur.getIdUser(), motCle, idCategorie, debut, fin)) {
                        ops.add(new Operation("Dépense", d.getNomCategorie(),
                                d.getMontant(), d.getDescription(), d.getDateDepense()));
                    }
                }
                if (idCategorie == null
                        && (typeFiltre == null || typeFiltre.equals("Tous") || typeFiltre.equals("Économie"))) {
                    String mot = motCle == null ? "" : motCle.trim().toLowerCase();
                    for (Saving e : savingDAO.listerParUser(utilisateur.getIdUser())) {
                        String desc = e.getDescription() == null ? "" : e.getDescription();
                        boolean motOk    = mot.isEmpty() || desc.toLowerCase().contains(mot);
                        boolean debutOk  = debut == null || !e.getDateEconomie().isBefore(debut);
                        boolean finOk    = fin   == null || !e.getDateEconomie().isAfter(fin);
                        if (motOk && debutOk && finOk) {
                            ops.add(new Operation("Économie", "—",
                                    e.getMontant(), desc, e.getDateEconomie()));
                        }
                    }
                }
                ops.sort(Comparator.comparing(Operation::getDate).reversed());
                return ops;
            }
        };
        tache.setOnSucceeded(evt -> {
            toutesOperations = tache.getValue();
            pageCourante = 0;
            afficherPage();
        });
        tache.setOnFailed(evt -> log.error("Filtrage historique échoué", tache.getException()));
        DbExecutor.soumettre(tache);
    }

    @FXML
    public void reinitialiserFiltres() {
        champRecherche.clear();
        comboFiltreType.getSelectionModel().selectFirst();
        comboFiltreCategorie.setValue(null);
        champFiltreDateDebut.setValue(null);
        champFiltreDateFin.setValue(null);
        chargerHistorique();
    }
}
