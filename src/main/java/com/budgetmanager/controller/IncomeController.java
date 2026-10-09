package com.budgetmanager.controller;

import com.budgetmanager.dao.CategoryDAO;
import com.budgetmanager.dao.IncomeDAO;
import com.budgetmanager.model.Category;
import com.budgetmanager.model.CategoryType;
import com.budgetmanager.model.Frequency;
import com.budgetmanager.model.Income;
import com.budgetmanager.model.User;
import com.budgetmanager.util.AlertUtil;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.SessionManager;
import com.budgetmanager.util.ListSyncUtil;
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

public class IncomeController {

    private static final Logger log = LoggerFactory.getLogger(IncomeController.class);

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

    @FXML private TableView<Income> tableRevenus;
    @FXML private TableColumn<Income, String> colonneCategorie;
    @FXML private TableColumn<Income, Double> colonneMontant;
    @FXML private TableColumn<Income, String> colonneDescription;
    @FXML private TableColumn<Income, LocalDate> colonneDate;
    @FXML private TableColumn<Income, String> colonneFrequence;

    @FXML private Button boutonAjouter;
    @FXML private Button boutonModifier;
    @FXML private Button boutonSupprimer;
    @FXML private Button boutonFiltrer;
    @FXML private Button boutonReinitialiser;
    @FXML private javafx.scene.layout.VBox overlayChargement;
    @FXML private javafx.scene.control.ProgressIndicator indicateurChargement;

    /** Contrôleur de la barre latérale incluse (fx:id="sidebar" -> champ "sidebarController"). */
    @FXML private SidebarController sidebarController;

    private final IncomeDAO incomeDAO = new IncomeDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    /** Liste maître : toutes les données chargées depuis la DB. */
    private final ObservableList<Income> donneesMaitres = FXCollections.observableArrayList();
    /** FilteredList liée au champ de recherche — filtre en mémoire sans requête DB. */
    private FilteredList<Income> donneesFiltrees;
    /** SortedList liée à la TableView — tri natif par colonne. */
    private final ObservableList<Income> donnees = FXCollections.observableArrayList();
    private Income revenuSelectionne;
    /** Référence forte sur le listener de sélection pour éviter le GC prématuré avec WeakChangeListener. */
    private final javafx.beans.value.ChangeListener<Income> listenerSelection = (obs, ancien, nouveau) -> {
        revenuSelectionne = nouveau;
        if (nouveau != null) remplirFormulaire(nouveau);
    };

    @FXML
    public void initialize() {
        sidebarController.setActive("revenus");

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
        colonneDate.setCellValueFactory(cellData -> cellData.getValue().dateRevenuProperty());
        colonneFrequence.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getLibelleFrequence()));

        // Validation : champ montant accepte uniquement des nombres décimaux.
        champMontant.setTextFormatter(com.budgetmanager.util.NumberFieldFormatter.creer());

        comboFrequence.setItems(FXCollections.observableArrayList(Frequency.values()));
        comboFrequence.setValue(Frequency.PONCTUEL);
        comboFrequence.valueProperty().addListener((obs, ancien, nouveau) -> mettreAJourFormulaireFrequence());
        mettreAJourFormulaireFrequence();
        // FilteredList pour filtrage en mémoire ultra-fluide (Point 2).
        donneesFiltrees = new FilteredList<>(donneesMaitres, p -> true);
        SortedList<Income> donneeTriees = new SortedList<>(donneesFiltrees);
        donneeTriees.comparatorProperty().bind(tableRevenus.comparatorProperty());
        tableRevenus.setItems(donneeTriees);

        // WeakChangeListener — libéré par le GC quand la vue est détruite (Point 1).
        tableRevenus.getSelectionModel().selectedItemProperty()
                .addListener(new WeakChangeListener<>(listenerSelection));

        // Focus automatique sur le premier champ (Point 5).
        javafx.application.Platform.runLater(() -> champMontant.requestFocus());

        // Chargement des catégories et des revenus en arrière-plan.
        Task<java.util.List<Category>> tacheCategories = new Task<>() {
            @Override
            protected java.util.List<Category> call() {
                return categoryDAO.listerParUserEtType(utilisateur.getIdUser(), CategoryType.REVENU);
            }
        };
        tacheCategories.setOnSucceeded(evt -> {
            ObservableList<Category> categories = FXCollections.observableArrayList(tacheCategories.getValue());
            comboCategorie.setItems(categories);
            comboFiltreCategorie.setItems(FXCollections.observableArrayList(categories));
            chargerRevenus(); // Lance le chargement des revenus une fois les catégories prêtes.
        });
        tacheCategories.setOnFailed(evt -> log.error("Chargement catégories revenus échoué", tacheCategories.getException()));
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

    private void chargerRevenus() {
        User utilisateur = SessionManager.getUtilisateurConnecte();
        int idUser = utilisateur.getIdUser();

        // Afficher l'overlay et désactiver les boutons pendant le chargement.
        overlayChargement.setVisible(true);
        overlayChargement.setManaged(true);
        boutonAjouter.setDisable(true);
        boutonModifier.setDisable(true);
        boutonSupprimer.setDisable(true);

        Task<java.util.List<Income>> tache = new Task<>() {
            @Override
            protected java.util.List<Income> call() {
                incomeDAO.genererRecurrences(idUser);
                return incomeDAO.listerParUser(idUser);
            }
        };
        tache.setOnSucceeded(evt -> {
            // Synchronisation intelligente — réutilise les instances existantes (zéro GC pressure).
            ListSyncUtil.synchroniser(donneesMaitres, tache.getValue(),
                    Income::getIdRevenu,
                    (existant, nouveau) -> {
                        existant.setMontant(nouveau.getMontant());
                        existant.setDescription(nouveau.getDescription());
                        existant.setDateRevenu(nouveau.getDateRevenu());
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
            log.error("Chargement revenus échoué", tache.getException());
            overlayChargement.setVisible(false);
            overlayChargement.setManaged(false);
            boutonAjouter.setDisable(false);
            boutonModifier.setDisable(false);
            boutonSupprimer.setDisable(false);
        });
        com.budgetmanager.util.DbExecutor.soumettre(tache);
    }

    private void remplirFormulaire(Income revenu) {
        champMontant.setText(String.valueOf(revenu.getMontant()));
        champDescription.setText(revenu.getDescription());
        champDate.setValue(revenu.getDateRevenu());
        // Une ligne générée automatiquement est un revenu ponctuel : on ne lui montre pas de récurrence.
        comboFrequence.setValue(revenu.getFrequence());
        champDateFin.setValue(revenu.getDateFin());
        comboCategorie.getItems().stream()
                .filter(c -> c.getIdCategorie() == revenu.getIdCategorie())
                .findFirst().ifPresent(comboCategorie::setValue);
    }

    @FXML
    public void ajouterRevenu() {
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

            Income revenu = new Income(utilisateur.getIdUser(), categorie.getIdCategorie(),
                    montant, champDescription.getText().trim(), date);
            revenu.setFrequence(frequence);
            revenu.setDateFin(dateFin);

            if (incomeDAO.creer(revenu)) {
                chargerRevenus();
                viderFormulaire();
            } else {
                AlertUtil.afficherErreur("Erreur", "Impossible d'ajouter le revenu.");
            }
        } catch (NumberFormatException e) {
            AlertUtil.afficherErreur("Montant invalide", "Veuillez saisir un montant numérique valide.");
        }
    }

    @FXML
    public void modifierRevenu() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        if (revenuSelectionne == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner un revenu à modifier.");
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
            if (revenuSelectionne.getIdSource() == null && revenuSelectionne.getFrequence().estRecurrent()
                    && revenuSelectionne.getDerniereGeneration() != null
                    && Math.abs(revenuSelectionne.getMontant() - montant) > 0.004) {
                LocalDate prochaine = revenuSelectionne.getFrequence()
                        .prochaineApres(revenuSelectionne.getDateRevenu(), revenuSelectionne.getDerniereGeneration());
                if (prochaine != null && (dateFin == null || !dateFin.isBefore(prochaine))) {
                    if (!AlertUtil.confirmer("Nouveau montant", "Le nouveau montant s'appliquera à partir du "
                            + prochaine.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            + ". Les montants déjà enregistrés ne changent pas. Continuer ?")) {
                        return;
                    }
                    Income suite = new Income(revenuSelectionne.getIdUser(), categorie.getIdCategorie(), montant, champDescription.getText().trim(), prochaine);
                    suite.setFrequence(revenuSelectionne.getFrequence());
                    suite.setDateFin(dateFin);
                    revenuSelectionne.setDateFin(revenuSelectionne.getDerniereGeneration());
                    if (incomeDAO.modifier(revenuSelectionne) && incomeDAO.creer(suite)) {
                        chargerRevenus();
                        viderFormulaire();
                    } else {
                        AlertUtil.afficherErreur("Erreur", "Impossible d'appliquer le nouveau montant.");
                    }
                    return;
                }
            }

            // Un revenu qui devient récurrent compte déjà comme sa première occurrence.
            if (frequence.estRecurrent() && !revenuSelectionne.getFrequence().estRecurrent()) {
                revenuSelectionne.setDerniereGeneration(date);
            }
            // Une ligne générée automatiquement reste un revenu ponctuel.
            if (revenuSelectionne.getIdSource() != null) {
                frequence = Frequency.PONCTUEL;
                dateFin = null;
            }

            revenuSelectionne.setMontant(montant);
            revenuSelectionne.setDescription(champDescription.getText().trim());
            revenuSelectionne.setDateRevenu(date);
            revenuSelectionne.setIdCategorie(categorie.getIdCategorie());
            revenuSelectionne.setFrequence(frequence);
            revenuSelectionne.setDateFin(dateFin);

            if (incomeDAO.modifier(revenuSelectionne)) {
                chargerRevenus();
                viderFormulaire();
            } else {
                AlertUtil.afficherErreur("Erreur", "Impossible de modifier le revenu.");
            }
        } catch (NumberFormatException e) {
            AlertUtil.afficherErreur("Montant invalide", "Veuillez saisir un montant numérique valide.");
        }
    }

    @FXML
    public void supprimerRevenu() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        if (revenuSelectionne == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner un revenu à supprimer.");
            return;
        }
        boolean source = revenuSelectionne.getIdSource() == null && revenuSelectionne.getFrequence().estRecurrent();
        String message = source
                ? "Supprimer ce revenu récurrent ? Les revenus ajoutés automatiquement seront supprimés aussi."
                : "Supprimer ce revenu ?";
        if (AlertUtil.confirmer("Confirmation", message)) {
            if (incomeDAO.supprimer(revenuSelectionne.getIdRevenu())) {
                chargerRevenus();
                viderFormulaire();
            } else {
                AlertUtil.afficherErreur("Erreur", "Impossible de supprimer le revenu.");
            }
        }
    }

    @FXML
    public void rechercherEtFiltrer() {
        // Filtrage en mémoire avec FilteredList — zéro requête DB (Point 2).
        String motCle = champRecherche.getText().trim().toLowerCase();
        Category categorie = comboFiltreCategorie.getValue();
        Integer idCategorie = categorie != null ? categorie.getIdCategorie() : null;
        LocalDate debut = champFiltreDateDebut.getValue();
        LocalDate fin   = champFiltreDateFin.getValue();

        donneesFiltrees.setPredicate(revenu -> {
            boolean motOk  = motCle.isEmpty() ||
                    (revenu.getDescription() != null && revenu.getDescription().toLowerCase().contains(motCle));
            boolean catOk  = idCategorie == null || revenu.getIdCategorie() == idCategorie;
            boolean debOk  = debut == null || !revenu.getDateRevenu().isBefore(debut);
            boolean finOk  = fin   == null || !revenu.getDateRevenu().isAfter(fin);
            return motOk && catOk && debOk && finOk;
        });
    }

    @FXML
    public void reinitialiserFiltres() {
        champRecherche.clear();
        comboFiltreCategorie.setValue(null);
        champFiltreDateDebut.setValue(null);
        champFiltreDateFin.setValue(null);
        donneesFiltrees.setPredicate(p -> true); // Affiche tout.
    }

    private void viderFormulaire() {
        champMontant.clear();
        champDescription.clear();
        champDate.setValue(null);
        comboCategorie.setValue(null);
        comboFrequence.setValue(Frequency.PONCTUEL);
        champDateFin.setValue(null);
        revenuSelectionne = null;
        tableRevenus.getSelectionModel().clearSelection();
    }

}
