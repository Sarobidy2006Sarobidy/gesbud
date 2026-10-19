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
import com.budgetmanager.util.RaccourcisClavier;
import com.budgetmanager.util.SessionManager;
import com.budgetmanager.util.SqlUtil;
import com.budgetmanager.util.VideEtat;
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

public class ExpenseController implements RaccourcisClavier, com.budgetmanager.util.Rafraichissable {

    private static final Logger log = LoggerFactory.getLogger(ExpenseController.class);

    @FXML private TextField champMontant;
    @FXML private TextField champDescription;
    @FXML private DatePicker champDate;
    @FXML private ComboBox<Category> comboCategorie;
    @FXML private ComboBox<Frequency> comboFrequence;
    @FXML private DatePicker champDateFin;
    @FXML private Label labelAideFrequence;
    @FXML private Label labelTotalAffiche;
    @FXML private Label pastilleNombre;

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

        VideEtat.surTable(tableDepenses, Icons.tag(26, VideEtat.couleurIcone()),
                "Aucune dépense",
                "Enregistrez une dépense depuis le formulaire, ou élargissez les dates du filtre.");

        colonneCategorie.setCellValueFactory(cellData -> cellData.getValue().nomCategorieProperty());
        colonneMontant.setCellValueFactory(cellData -> cellData.getValue().montantProperty().asObject());
        com.budgetmanager.util.TableUtil.formaterMontants(colonneMontant);
        colonneDescription.setCellValueFactory(cellData -> cellData.getValue().descriptionProperty());
        colonneDate.setCellValueFactory(cellData -> cellData.getValue().dateDepenseProperty());
        com.budgetmanager.util.TableUtil.marquerPrevues(colonneDate);
        colonneFrequence.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getLibelleFrequence()));

        // Validation : champ montant accepte uniquement des nombres décimaux.
        champMontant.setTextFormatter(com.budgetmanager.util.NumberFieldFormatter.creer());
        champDescription.setTextFormatter(com.budgetmanager.util.LongueurFieldFormatter.creer(255));

        comboFrequence.setItems(FXCollections.observableArrayList(Frequency.values()));
        comboFrequence.setValue(Frequency.PONCTUEL);
        comboFrequence.valueProperty().addListener((obs, ancien, nouveau) -> mettreAJourFormulaireFrequence());
        mettreAJourFormulaireFrequence();
        donneesFiltrees = new FilteredList<>(donneesMaitres, p -> true);
        SortedList<Expense> donneeTriees = new SortedList<>(donneesFiltrees);
        donneeTriees.comparatorProperty().bind(tableDepenses.comparatorProperty());
        tableDepenses.setItems(donneeTriees);

        // Puces d'en-tête : ce que le filtre laisse voir, pas la DB, hors opérations prévues.
        pastilleNombre.textProperty().bind(
                com.budgetmanager.util.TableUtil.compterRealisees(
                        tableDepenses.getItems(), Expense::getDateDepense));
        // Devise lue à chaque évaluation, la propriété devise servant de dépendance :
        // un changement dans Paramètres se reflète sans redémarrer la vue.
        labelTotalAffiche.textProperty().bind(
                javafx.beans.binding.Bindings.createObjectBinding(
                        () -> com.budgetmanager.util.CurrencyUtil.formater(
                                tableDepenses.getItems().stream()
                                        .filter(d -> SqlUtil.estRealisee(d.getDateDepense()))
                                        .mapToDouble(Expense::getMontant).sum(),
                                com.budgetmanager.util.CurrencyUtil.deviseCourante()),
                        tableDepenses.getItems(), com.budgetmanager.util.CurrencyUtil.sourceDevise()));

        labelTotalAffiche.setTooltip(new javafx.scene.control.Tooltip(
                "Les opérations datées après aujourd'hui sont prévues : elles restent au tableau,\n" +
                "elles n'entrent dans aucun total, ni ici ni au tableau de bord."));

        tableDepenses.getSelectionModel().selectedItemProperty()
                .addListener(new WeakChangeListener<>(listenerSelection));

        com.budgetmanager.util.BoutonsLigne.lier(tableDepenses, boutonModifier, boutonSupprimer);
        com.budgetmanager.util.DateFr.appliquer(champDate, champDateFin, champFiltreDateDebut, champFiltreDateFin);
        com.budgetmanager.util.ComboCategories.installer(comboCategorie,
                sidebarController::allerVersCategories);

        champDate.setValue(LocalDate.now());
        javafx.application.Platform.runLater(() -> champMontant.requestFocus());

        chargerCombosCategories();
    }

    /**
     * Remplit les deux combos de catégories puis lance le chargement des dépenses.
     * Appelé à l'initialisation et à chaque retour de la vue depuis le cache :
     * une catégorie ajoutée ou renommée ailleurs doit être proposable ici.
     */
    private void chargerCombosCategories() {
        User utilisateur = SessionManager.getUtilisateurConnecte();
        Task<java.util.List<Category>> tacheCategories = new Task<>() {
            @Override
            protected java.util.List<Category> call() {
                return categoryDAO.listerParUserEtType(utilisateur.getIdUser(), CategoryType.DEPENSE);
            }
        };
        tacheCategories.setOnSucceeded(evt -> {
            java.util.List<Category> categories = tacheCategories.getValue();
            com.budgetmanager.util.ComboCategories.remplir(comboCategorie, categories);
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

    /** Redemandé par la navigation quand la vue revient du cache. */
    @Override
    public void rafraichirDonnees() {
        chargerCombosCategories();
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
            com.budgetmanager.util.BoutonsLigne.appliquer(tableDepenses, boutonModifier, boutonSupprimer);
            // Pas de vidage ici : une fiche en cours de frappe doit survivre au rechargement.
        });
        tache.setOnFailed(evt -> {
            log.error("Chargement dépenses échoué", tache.getException());
            overlayChargement.setVisible(false);
            overlayChargement.setManaged(false);
            boutonAjouter.setDisable(false);
            com.budgetmanager.util.BoutonsLigne.appliquer(tableDepenses, boutonModifier, boutonSupprimer);
        });
        com.budgetmanager.util.DbExecutor.soumettre(tache);
    }

    private void remplirFormulaire(Expense depense) {
        champMontant.setText(com.budgetmanager.util.NumberFieldFormatter.ecrire(depense.getMontant()));
        champDescription.setText(depense.getDescription());
        champDate.setValue(depense.getDateDepense());
        comboFrequence.setValue(depense.getFrequence());
        champDateFin.setValue(depense.getDateFin());
        // Valeur explicitement vidée si la catégorie n'est plus proposée : sinon
        // l'ancienne sélection resterait affichée et serait réécrite à l'enregistrement.
        comboCategorie.setValue(comboCategorie.getItems().stream()
                .filter(c -> c.getIdCategorie() == depense.getIdCategorie())
                .findFirst().orElse(null));
    }

    @FXML
    public void ajouterDepense() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        if (depenseSelectionne != null) {
            modifierDepense();
            return;
        }
        User utilisateur = SessionManager.getUtilisateurConnecte();
        Double montantLu = com.budgetmanager.util.NumberFieldFormatter.lire(champMontant.getText());
        if (montantLu == null) {
            AlertUtil.afficherErreur("Montant invalide",
                    com.budgetmanager.util.NumberFieldFormatter.messageErreur(champMontant.getText()));
            return;
        }
        double montant = montantLu;
        Category categorie = comboCategorie.getValue();
        LocalDate date = champDate.getValue();

        if (categorie == null || date == null) {
            AlertUtil.afficherErreur("Champs manquants", "Veuillez sélectionner une catégorie et une date.");
            return;
        }
        Frequency frequence = comboFrequence.getValue() != null ? comboFrequence.getValue() : Frequency.PONCTUEL;
        LocalDate dateFin = frequence.estRecurrent() ? champDateFin.getValue() : null;
        if (dateFin != null && dateFin.isBefore(date)) {
            AlertUtil.afficherErreur("Dates incohérentes", "La date de fin doit être après la date de début.");
            return;
        }

        if (!com.budgetmanager.util.BudgetUtil.confirmerSiDepassement(montant, date)) return;
        if (!com.budgetmanager.util.BudgetUtil.confirmerSiPlafond(categorie, montant, date, null)) return;

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
    }

    /** Ctrl+N : nouveau formulaire vide, sans créer de doublon. */
    @Override
    public void auRaccourciAjouter() { viderFormulaire(); }

    /** Ctrl+S : modifier la ligne choisie, sinon ajouter. */
    @Override
    public void auRaccourciEnregistrer() {
        if (depenseSelectionne == null) ajouterDepense();
        else modifierDepense();
    }

    @FXML
    public void modifierDepense() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        if (depenseSelectionne == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner une dépense à modifier.");
            return;
        }
        Double montantLu = com.budgetmanager.util.NumberFieldFormatter.lire(champMontant.getText());
        if (montantLu == null) {
            AlertUtil.afficherErreur("Montant invalide",
                    com.budgetmanager.util.NumberFieldFormatter.messageErreur(champMontant.getText()));
            return;
        }
        double montant = montantLu;
        LocalDate date = champDate.getValue();
        Category categorie = comboCategorie.getValue();
        if (categorie == null || date == null) {
            AlertUtil.afficherErreur("Champs manquants", "Veuillez sélectionner une catégorie et une date.");
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
                        + com.budgetmanager.util.DateFr.formater(prochaine)
                        + ". Les montants déjà enregistrés ne changent pas. Continuer ?")) {
                    return;
                }
                Expense suite = new Expense(depenseSelectionne.getIdUser(), categorie.getIdCategorie(), montant, champDescription.getText().trim(), prochaine);
                suite.setFrequence(depenseSelectionne.getFrequence());
                suite.setDateFin(dateFin);
                depenseSelectionne.setDateFin(depenseSelectionne.getDerniereGeneration());
                if (expenseDAO.changerMontantRecurrent(depenseSelectionne, suite)) {
                    chargerDepenses();
                    viderFormulaire();
                } else {
                    AlertUtil.afficherErreur("Erreur", "Impossible d'appliquer le nouveau montant.");
                }
                return;
            }
        }

        if (!com.budgetmanager.util.BudgetUtil.confirmerSiPlafond(categorie, montant, date, depenseSelectionne)) return;

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
            if (expenseDAO.supprimer(depenseSelectionne.getIdDepense(),
                    SessionManager.getUtilisateurConnecte().getIdUser())) {
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
        champDate.setValue(LocalDate.now());
        comboCategorie.setValue(null);
        comboFrequence.setValue(Frequency.PONCTUEL);
        champDateFin.setValue(null);
        depenseSelectionne = null;
        tableDepenses.getSelectionModel().clearSelection();
    }

}
