package com.budgetmanager.controller;

import com.budgetmanager.dao.CategoryDAO;
import com.budgetmanager.dao.ExpenseDAO;
import com.budgetmanager.model.Category;
import com.budgetmanager.model.CategoryType;
import com.budgetmanager.model.Expense;
import com.budgetmanager.model.Frequency;
import com.budgetmanager.model.User;
import com.budgetmanager.util.AlertUtil;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.ListSyncUtil;
import com.budgetmanager.util.SessionManager;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

public class ExpenseController {

    private static final Logger log = LoggerFactory.getLogger(ExpenseController.class);

    @FXML private TextField champMontant;
    @FXML private TextField champDescription;
    @FXML private DatePicker champDate;
    @FXML private ComboBox<Category> comboCategorie;
    @FXML private ComboBox<Frequency> comboFrequence;
    @FXML private DatePicker champDateFin;
    @FXML private Label labelAideFrequence;

    @FXML private TextField champRecherche;
    @FXML private ComboBox<Category> comboFiltreCategorie;
    @FXML private DatePicker champFiltreDateDebut;
    @FXML private DatePicker champFiltreDateFin;

    @FXML private TableView<Expense> tableDepenses;
    @FXML private TableColumn<Expense, String> colonneCategorie;
    @FXML private TableColumn<Expense, Double> colonneMontant;
    @FXML private TableColumn<Expense, String> colonneDescription;
    @FXML private TableColumn<Expense, LocalDate> colonneDate;
    @FXML private TableColumn<Expense, String> colonneFrequence;

    @FXML private Button boutonAjouter;
    @FXML private Button boutonModifier;
    @FXML private Button boutonSupprimer;
    @FXML private Button boutonFiltrer;
    @FXML private Button boutonReinitialiser;
    @FXML private javafx.scene.layout.VBox overlayChargement;
    @FXML private javafx.scene.control.ProgressIndicator indicateurChargement;

    /** Contrôleur de la barre latérale incluse (fx:id="sidebar" -> champ "sidebarController"). */
    @FXML private SidebarController sidebarController;

    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ObservableList<Expense> donneesMaitres = FXCollections.observableArrayList();
    private FilteredList<Expense> donneesFiltrees;
    private final ObservableList<Expense> donnees = FXCollections.observableArrayList();
    private Expense depenseSelectionne;
    private final javafx.beans.value.ChangeListener<Expense> listenerSelection = (obs, ancien, nouveau) -> {
        depenseSelectionne = nouveau;
        if (nouveau != null) remplirFormulaire(nouveau);
    };

    @FXML
    public void initialize() {
        sidebarController.setActive("depenses");

        boutonAjouter.setGraphic(Icons.plusCircle(15, Color.WHITE));
        boutonModifier.setGraphic(Icons.edit(15, Color.web("#374151")));
        boutonSupprimer.setGraphic(Icons.trash(15, Color.web("#b91c1c")));
        boutonFiltrer.setGraphic(Icons.filter(15, Color.WHITE));
        boutonReinitialiser.setGraphic(Icons.rotateCcw(15, Color.web("#374151")));

        User utilisateur = SessionManager.getUtilisateurConnecte();

        colonneCategorie.setCellValueFactory(cellData -> cellData.getValue().nomCategorieProperty());
        colonneMontant.setCellValueFactory(cellData -> cellData.getValue().montantProperty().asObject());
        com.budgetmanager.util.TableUtil.formaterMontants(colonneMontant);
        colonneDescription.setCellValueFactory(cellData -> cellData.getValue().descriptionProperty());
        colonneDate.setCellValueFactory(cellData -> cellData.getValue().dateDepenseProperty());
        colonneFrequence.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getLibelleFrequence()));

        // Validation : champ montant accepte uniquement des nombres décimaux.
        champMontant.setTextFormatter(com.budgetmanager.util.NumberFieldFormatter.creer());

        comboFrequence.setItems(FXCollections.observableArrayList(Frequency.values()));
        comboFrequence.setValue(Frequency.PONCTUEL);
        comboFrequence.valueProperty().addListener((obs, ancien, nouveau) -> mettreAJourFormulaireFrequence());
        mettreAJourFormulaireFrequence();
        donneesFiltrees = new FilteredList<>(donneesMaitres, p -> true);
        SortedList<Expense> donneeTriees = new SortedList<>(donneesFiltrees);
        donneeTriees.comparatorProperty().bind(tableDepenses.comparatorProperty());
        tableDepenses.setItems(donneeTriees);

        tableDepenses.getSelectionModel().selectedItemProperty()
                .addListener(new WeakChangeListener<>(listenerSelection));

        javafx.application.Platform.runLater(() -> champMontant.requestFocus());

        Task<java.util.List<Category>> tacheCategories = new Task<>() {
            @Override
            protected java.util.List<Category> call() {
                return categoryDAO.listerParUserEtType(utilisateur.getIdUser(), CategoryType.DEPENSE);
            }
        };
        tacheCategories.setOnSucceeded(evt -> {
            ObservableList<Category> categories = FXCollections.observableArrayList(tacheCategories.getValue());
            comboCategorie.setItems(categories);
            comboFiltreCategorie.setItems(FXCollections.observableArrayList(categories));
            chargerDepenses();
        });
        tacheCategories.setOnFailed(evt -> log.error("Chargement catégories dépenses échoué", tacheCategories.getException()));
        com.budgetmanager.util.DbExecutor.soumettre(tacheCategories);
    }

    /** Active « Jusqu'au » et explique la récurrence choisie. */
    private void mettreAJourFormulaireFrequence() {
        Frequency f = comboFrequence.getValue();
        boolean recurrent = f != null && f.estRecurrent();
        champDateFin.setDisable(!recurrent);
        if (!recurrent) champDateFin.setValue(null);
        labelAideFrequence.setText(recurrent
                ? "Ajouté automatiquement " + f.getLibelle().toLowerCase() + " à partir de la date choisie. "
                  + "Si le montant change (augmentation, imprévu), corrigez-le sur la ligne concernée."
                : "Enregistré une seule fois, à la date choisie.");
    }

    private void chargerDepenses() {
        User utilisateur = SessionManager.getUtilisateurConnecte();
        int idUser = utilisateur.getIdUser();

        overlayChargement.setVisible(true);
        overlayChargement.setManaged(true);
        boutonAjouter.setDisable(true);
        boutonModifier.setDisable(true);
        boutonSupprimer.setDisable(true);

        Task<java.util.List<Expense>> tache = new Task<>() {
            @Override
            protected java.util.List<Expense> call() {
                expenseDAO.genererRecurrences(idUser);
                return expenseDAO.listerParUser(idUser);
            }
        };
        tache.setOnSucceeded(evt -> {
            // Synchronisation intelligente — réutilise les instances existantes.
            ListSyncUtil.synchroniser(donneesMaitres, tache.getValue(),
                    Expense::getIdDepense,
                    (existant, nouveau) -> {
                        existant.setMontant(nouveau.getMontant());
                        existant.setDescription(nouveau.getDescription());
                        existant.setDateDepense(nouveau.getDateDepense());
                        existant.setIdCategorie(nouveau.getIdCategorie());
                        existant.setNomCategorie(nouveau.getNomCategorie());
                        existant.setFrequence(nouveau.getFrequence());
                        existant.setDateFin(nouveau.getDateFin());
                        existant.setDerniereGeneration(nouveau.getDerniereGeneration());
                        existant.setIdSource(nouveau.getIdSource());
                    });
            overlayChargement.setVisible(false);
            overlayChargement.setManaged(false);
            boutonAjouter.setDisable(false);
            boutonModifier.setDisable(false);
            boutonSupprimer.setDisable(false);
        });
        tache.setOnFailed(evt -> {
            log.error("Chargement dépenses échoué", tache.getException());
            overlayChargement.setVisible(false);
            overlayChargement.setManaged(false);
            boutonAjouter.setDisable(false);
            boutonModifier.setDisable(false);
            boutonSupprimer.setDisable(false);
        });
        com.budgetmanager.util.DbExecutor.soumettre(tache);
    }

    private void remplirFormulaire(Expense depense) {
        champMontant.setText(String.valueOf(depense.getMontant()));
        champDescription.setText(depense.getDescription());
        champDate.setValue(depense.getDateDepense());
        comboFrequence.setValue(depense.getFrequence());
        champDateFin.setValue(depense.getDateFin());
        comboCategorie.getItems().stream()
                .filter(c -> c.getIdCategorie() == depense.getIdCategorie())
                .findFirst().ifPresent(comboCategorie::setValue);
    }

    @FXML
    public void ajouterDepense() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        try {
            User utilisateur = SessionManager.getUtilisateurConnecte();
            double montant = Double.parseDouble(champMontant.getText().trim());
            Category categorie = comboCategorie.getValue();
            LocalDate date = champDate.getValue();

            if (categorie == null || date == null) {
                AlertUtil.afficherErreur("Champs manquants", "Veuillez sélectionner une catégorie et une date.");
                return;
            }
            if (montant <= 0) {
                AlertUtil.afficherErreur("Montant invalide", "Le montant doit être supérieur à 0.");
                return;
            }
            Frequency frequence = comboFrequence.getValue() != null ? comboFrequence.getValue() : Frequency.PONCTUEL;
            LocalDate dateFin = frequence.estRecurrent() ? champDateFin.getValue() : null;
            if (dateFin != null && dateFin.isBefore(date)) {
                AlertUtil.afficherErreur("Dates incohérentes", "La date de fin doit être après la date de début.");
                return;
            }

            if (!com.budgetmanager.util.BudgetUtil.confirmerSiDepassement(montant, date)) return;

            Expense depense = new Expense(utilisateur.getIdUser(), categorie.getIdCategorie(),
                    montant, champDescription.getText().trim(), date);
            depense.setFrequence(frequence);
            depense.setDateFin(dateFin);

            if (expenseDAO.creer(depense)) {
                chargerDepenses();
                viderFormulaire();
            } else {
                AlertUtil.afficherErreur("Erreur", "Impossible d'ajouter la dépense.");
            }
        } catch (NumberFormatException e) {
            AlertUtil.afficherErreur("Montant invalide", "Veuillez saisir un montant numérique valide.");
        }
    }

    @FXML
    public void modifierDepense() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        if (depenseSelectionne == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner une dépense à modifier.");
            return;
        }
        try {
            double montant = Double.parseDouble(champMontant.getText().trim());
            LocalDate date = champDate.getValue();
            Category categorie = comboCategorie.getValue();
            if (categorie == null || date == null) {
                AlertUtil.afficherErreur("Champs manquants", "Veuillez sélectionner une catégorie et une date.");
                return;
            }
            if (montant <= 0) {
                AlertUtil.afficherErreur("Montant invalide", "Le montant doit être supérieur à 0.");
                return;
            }
            Frequency frequence = comboFrequence.getValue() != null ? comboFrequence.getValue() : Frequency.PONCTUEL;
            LocalDate dateFin = frequence.estRecurrent() ? champDateFin.getValue() : null;
            if (dateFin != null && dateFin.isBefore(date)) {
                AlertUtil.afficherErreur("Dates incohérentes", "La date de fin doit être après la date de début.");
                return;
            }

            // Le montant d'une opération récurrente change (augmentation, baisse, imprévu) : il s'applique à partir de
            // la prochaine échéance ; ce qui est déjà enregistré garde l'ancien montant.
            if (depenseSelectionne.getIdSource() == null && depenseSelectionne.getFrequence().estRecurrent()
                    && depenseSelectionne.getDerniereGeneration() != null
                    && Math.abs(depenseSelectionne.getMontant() - montant) > 0.004) {
                LocalDate prochaine = depenseSelectionne.getFrequence()
                        .prochaineApres(depenseSelectionne.getDateDepense(), depenseSelectionne.getDerniereGeneration());
                if (prochaine != null && (dateFin == null || !dateFin.isBefore(prochaine))) {
                    if (!AlertUtil.confirmer("Nouveau montant", "Le nouveau montant s'appliquera à partir du "
                            + prochaine.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            + ". Les montants déjà enregistrés ne changent pas. Continuer ?")) {
                        return;
                    }
                    Expense suite = new Expense(depenseSelectionne.getIdUser(), categorie.getIdCategorie(), montant, champDescription.getText().trim(), prochaine);
                    suite.setFrequence(depenseSelectionne.getFrequence());
                    suite.setDateFin(dateFin);
                    depenseSelectionne.setDateFin(depenseSelectionne.getDerniereGeneration());
                    if (expenseDAO.modifier(depenseSelectionne) && expenseDAO.creer(suite)) {
                        chargerDepenses();
                        viderFormulaire();
                    } else {
                        AlertUtil.afficherErreur("Erreur", "Impossible d'appliquer le nouveau montant.");
                    }
                    return;
                }
            }

            // Une dépense qui devient récurrente compte déjà comme sa première occurrence.
            if (frequence.estRecurrent() && !depenseSelectionne.getFrequence().estRecurrent()) {
                depenseSelectionne.setDerniereGeneration(date);
            }
            // Une ligne générée automatiquement reste une dépense ponctuelle.
            if (depenseSelectionne.getIdSource() != null) {
                frequence = Frequency.PONCTUEL;
                dateFin = null;
            }

            depenseSelectionne.setMontant(montant);
            depenseSelectionne.setDescription(champDescription.getText().trim());
            depenseSelectionne.setDateDepense(date);
            depenseSelectionne.setIdCategorie(categorie.getIdCategorie());
            depenseSelectionne.setFrequence(frequence);
            depenseSelectionne.setDateFin(dateFin);

            if (expenseDAO.modifier(depenseSelectionne)) {
                chargerDepenses();
                viderFormulaire();
            } else {
                AlertUtil.afficherErreur("Erreur", "Impossible de modifier la dépense.");
            }
        } catch (NumberFormatException e) {
            AlertUtil.afficherErreur("Montant invalide", "Veuillez saisir un montant numérique valide.");
        }
    }

    @FXML
    public void supprimerDepense() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        if (depenseSelectionne == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner une dépense à supprimer.");
            return;
        }
        boolean source = depenseSelectionne.getIdSource() == null && depenseSelectionne.getFrequence().estRecurrent();
        String message = source
                ? "Supprimer cette dépense récurrente ? Les dépenses ajoutées automatiquement seront supprimées aussi."
                : "Supprimer cette dépense ?";
        if (AlertUtil.confirmer("Confirmation", message)) {
            if (expenseDAO.supprimer(depenseSelectionne.getIdDepense())) {
                chargerDepenses();
                viderFormulaire();
            } else {
                AlertUtil.afficherErreur("Erreur", "Impossible de supprimer la dépense.");
            }
        }
    }

    @FXML
    public void rechercherEtFiltrer() {
        String motCle = champRecherche.getText().trim().toLowerCase();
        Category categorie = comboFiltreCategorie.getValue();
        Integer idCategorie = categorie != null ? categorie.getIdCategorie() : null;
        LocalDate debut = champFiltreDateDebut.getValue();
        LocalDate fin   = champFiltreDateFin.getValue();

        donneesFiltrees.setPredicate(depense -> {
            boolean motOk  = motCle.isEmpty() ||
                    (depense.getDescription() != null && depense.getDescription().toLowerCase().contains(motCle));
            boolean catOk  = idCategorie == null || depense.getIdCategorie() == idCategorie;
            boolean debOk  = debut == null || !depense.getDateDepense().isBefore(debut);
            boolean finOk  = fin   == null || !depense.getDateDepense().isAfter(fin);
            return motOk && catOk && debOk && finOk;
        });
    }

    @FXML
    public void reinitialiserFiltres() {
        champRecherche.clear();
        comboFiltreCategorie.setValue(null);
        champFiltreDateDebut.setValue(null);
        champFiltreDateFin.setValue(null);
        donneesFiltrees.setPredicate(p -> true);
    }

    private void viderFormulaire() {
        champMontant.clear();
        champDescription.clear();
        champDate.setValue(null);
        comboCategorie.setValue(null);
        comboFrequence.setValue(Frequency.PONCTUEL);
        champDateFin.setValue(null);
        depenseSelectionne = null;
        tableDepenses.getSelectionModel().clearSelection();
    }

}
