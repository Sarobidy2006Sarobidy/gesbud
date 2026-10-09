package com.budgetmanager.controller;

import com.budgetmanager.dao.SavingDAO;
import com.budgetmanager.model.Frequency;
import com.budgetmanager.model.Saving;
import com.budgetmanager.model.User;
import com.budgetmanager.util.AlertUtil;
import com.budgetmanager.util.CurrencyUtil;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.ListSyncUtil;
import com.budgetmanager.util.SavingsCurve;
import com.budgetmanager.util.SavingsCurve.Period;
import com.budgetmanager.util.SessionManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Écran des économies : ajout d'économies ponctuelles ou récurrentes
 * (chaque jour, chaque semaine, chaque mois), total économisé et courbe
 * du cumul par jour, par semaine ou par mois.
 */
public class SavingsController {

    private static final Logger log = LoggerFactory.getLogger(SavingsController.class);

    @FXML private TextField champMontant;
    @FXML private TextField champDescription;
    @FXML private DatePicker champDate;
    @FXML private ComboBox<Frequency> comboFrequence;
    @FXML private DatePicker champDateFin;
    @FXML private Label labelAideFrequence;

    @FXML private Label labelTotal;
    @FXML private Label labelMois;
    @FXML private Label labelNombre;
    @FXML private Label labelAlerteSolde;
    @FXML private ComboBox<Period> comboPeriode;
    @FXML private LineChart<String, Number> courbeEconomies;

    @FXML private TextField champRecherche;
    @FXML private DatePicker champFiltreDateDebut;
    @FXML private DatePicker champFiltreDateFin;
    @FXML private Button boutonFiltrer;
    @FXML private Button boutonReinitialiser;

    @FXML private TableView<Saving> tableEconomies;
    @FXML private TableColumn<Saving, Double> colonneMontant;
    @FXML private TableColumn<Saving, String> colonneDescription;
    @FXML private TableColumn<Saving, LocalDate> colonneDate;
    @FXML private TableColumn<Saving, String> colonneFrequence;

    @FXML private Button boutonAjouter;
    @FXML private Button boutonModifier;
    @FXML private Button boutonSupprimer;

    /** Contrôleur de la barre latérale incluse (fx:id="sidebar" -> champ "sidebarController"). */
    @FXML private SidebarController sidebarController;

    private final SavingDAO savingDAO = new SavingDAO();
    private final ObservableList<Saving> donnees = FXCollections.observableArrayList();
    /** Toutes les économies : la courbe et les totaux ne dépendent jamais du filtre de recherche. */
    private List<Saving> toutes = List.of();
    private Saving economieSelectionnee;

    @FXML
    public void initialize() {
        sidebarController.setActive("economies");

        boutonAjouter.setGraphic(Icons.plusCircle(15, Color.WHITE));
        boutonModifier.setGraphic(Icons.edit(15, Color.web("#374151")));
        boutonSupprimer.setGraphic(Icons.trash(15, Color.web("#b91c1c")));
        boutonFiltrer.setGraphic(Icons.filter(15, Color.WHITE));
        boutonReinitialiser.setGraphic(Icons.rotateCcw(15, Color.web("#374151")));

        colonneMontant.setCellValueFactory(cellData -> cellData.getValue().montantProperty().asObject());
        com.budgetmanager.util.TableUtil.formaterMontants(colonneMontant);
        colonneDescription.setCellValueFactory(cellData -> cellData.getValue().descriptionProperty());
        colonneDate.setCellValueFactory(cellData -> cellData.getValue().dateEconomieProperty());
        colonneFrequence.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getLibelleFrequence()));

        // Validation : champ montant accepte uniquement des nombres décimaux.
        champMontant.setTextFormatter(com.budgetmanager.util.NumberFieldFormatter.creer());
        tableEconomies.setItems(donnees);

        comboFrequence.setItems(FXCollections.observableArrayList(Frequency.values()));
        comboFrequence.setValue(Frequency.PONCTUEL);
        comboFrequence.valueProperty().addListener((obs, ancien, nouveau) -> mettreAJourFormulaireFrequence());
        mettreAJourFormulaireFrequence();

        comboPeriode.setItems(FXCollections.observableArrayList(Period.values()));
        comboPeriode.setValue(Period.MOIS);
        comboPeriode.valueProperty().addListener((obs, ancien, nouveau) -> afficherCourbe());

        courbeEconomies.setLegendVisible(false);
        courbeEconomies.setAnimated(false);

        tableEconomies.getSelectionModel().selectedItemProperty().addListener((obs, ancien, nouveau) -> {
            economieSelectionnee = nouveau;
            if (nouveau != null) remplirFormulaire(nouveau);
        });

        chargerEconomies();
    }

    /** Active « Jusqu'au » et explique la récurrence choisie. */
    private void mettreAJourFormulaireFrequence() {
        Frequency f = comboFrequence.getValue();
        boolean recurrent = f != null && f.estRecurrent();
        champDateFin.setDisable(!recurrent);
        if (!recurrent) champDateFin.setValue(null);
        labelAideFrequence.setText(recurrent
                ? "Ajouté automatiquement " + f.getLibelle().toLowerCase() + " à partir de la date choisie. "
                  + "Si le montant change, corrigez-le sur la ligne concernée."
                : "Enregistré une seule fois, à la date choisie.");
    }

    private void chargerEconomies() {
        User utilisateur = SessionManager.getUtilisateurConnecte();
        int idUser = utilisateur.getIdUser();
        String devise = utilisateur.getDevise();

        // Données calculées en arrière-plan pour ne pas bloquer l'UI.
        Task<double[]> tacheStats = new Task<>() {
            @Override
            protected double[] call() {
                savingDAO.genererRecurrences(idUser);
                // On récupère la liste complète sur ce thread pour l'utiliser dans l'UI après.
                toutes = savingDAO.listerParUser(idUser);
                double total = savingDAO.totalParUser(idUser);
                double mois  = savingDAO.totalParMois(idUser, YearMonth.now());
                double solde = com.budgetmanager.util.BudgetUtil.soldeDisponible(idUser);
                return new double[]{ total, mois, toutes.size(), solde };
            }
        };
        tacheStats.setOnSucceeded(evt -> {
            double[] v = tacheStats.getValue();
            // Synchronisation intelligente — réutilise les instances existantes.
            ListSyncUtil.synchroniser(donnees, toutes,
                    Saving::getIdEconomie,
                    (existant, nouveau) -> {
                        existant.setMontant(nouveau.getMontant());
                        existant.setDescription(nouveau.getDescription());
                        existant.setDateEconomie(nouveau.getDateEconomie());
                        existant.setFrequence(nouveau.getFrequence());
                        existant.setDateFin(nouveau.getDateFin());
                        existant.setIdSource(nouveau.getIdSource());
                    });
            labelTotal.setText(CurrencyUtil.formater(v[0], devise));
            labelMois.setText(CurrencyUtil.formater(v[1], devise));
            labelNombre.setText(String.valueOf((int) v[2]));
            double solde = v[3];
            labelAlerteSolde.setVisible(solde < 0);
            labelAlerteSolde.setManaged(solde < 0);
            if (solde < 0) {
                labelAlerteSolde.setText("Attention : votre solde disponible est négatif ("
                        + CurrencyUtil.formater(solde, devise)
                        + "). Vos dépenses et économies dépassent vos revenus.");
            }
            afficherCourbe();
        });
        tacheStats.setOnFailed(evt -> log.error("Chargement économies échoué", tacheStats.getException()));
        com.budgetmanager.util.DbExecutor.soumettre(tacheStats);
    }

    /** Dessine la courbe du total cumulé selon la période choisie. */
    private void afficherCourbe() {
        Period periode = comboPeriode.getValue() != null ? comboPeriode.getValue() : Period.MOIS;
        List<SavingsCurve.Point> points = SavingsCurve.construire(toutes, periode, LocalDate.now());

        courbeEconomies.setTitle("Total économisé (cumul) — " + periode.toString().toLowerCase());
        courbeEconomies.setCreateSymbols(points.size() <= 40);
        courbeEconomies.getData().clear();

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (SavingsCurve.Point p : points) {
            serie.getData().add(new XYChart.Data<>(p.libelle(), p.cumul()));
        }
        courbeEconomies.getData().add(serie);
    }

    private void remplirFormulaire(Saving economie) {
        champMontant.setText(String.valueOf(economie.getMontant()));
        champDescription.setText(economie.getDescription());
        champDate.setValue(economie.getDateEconomie());
        comboFrequence.setValue(economie.getFrequence());
        champDateFin.setValue(economie.getDateFin());
    }

    @FXML
    public void ajouterEconomie() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        try {
            User utilisateur = SessionManager.getUtilisateurConnecte();
            double montant = Double.parseDouble(champMontant.getText().trim().replace(',', '.'));
            LocalDate date = champDate.getValue();

            if (date == null) {
                AlertUtil.afficherErreur("Champ manquant", "Veuillez choisir une date.");
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

            Saving economie = new Saving(utilisateur.getIdUser(), montant, champDescription.getText().trim(), date);
            economie.setFrequence(frequence);
            economie.setDateFin(dateFin);

            if (savingDAO.creer(economie)) {
                chargerEconomies();
                viderFormulaire();
            } else {
                AlertUtil.afficherErreur("Erreur", "Impossible d'ajouter l'économie.");
            }
        } catch (NumberFormatException e) {
            AlertUtil.afficherErreur("Montant invalide", "Veuillez saisir un montant numérique valide.");
        }
    }

    @FXML
    public void modifierEconomie() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        if (economieSelectionnee == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner une économie à modifier.");
            return;
        }
        try {
            double montant = Double.parseDouble(champMontant.getText().trim().replace(',', '.'));
            LocalDate date = champDate.getValue();
            if (date == null) {
                AlertUtil.afficherErreur("Champ manquant", "Veuillez choisir une date.");
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
            if (economieSelectionnee.getIdSource() == null && economieSelectionnee.getFrequence().estRecurrent()
                    && economieSelectionnee.getDerniereGeneration() != null
                    && Math.abs(economieSelectionnee.getMontant() - montant) > 0.004) {
                LocalDate prochaine = economieSelectionnee.getFrequence()
                        .prochaineApres(economieSelectionnee.getDateEconomie(), economieSelectionnee.getDerniereGeneration());
                if (prochaine != null && (dateFin == null || !dateFin.isBefore(prochaine))) {
                    if (!AlertUtil.confirmer("Nouveau montant", "Le nouveau montant s'appliquera à partir du "
                            + prochaine.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            + ". Les montants déjà enregistrés ne changent pas. Continuer ?")) {
                        return;
                    }
                    Saving suite = new Saving(economieSelectionnee.getIdUser(), montant, champDescription.getText().trim(), prochaine);
                    suite.setFrequence(economieSelectionnee.getFrequence());
                    suite.setDateFin(dateFin);
                    economieSelectionnee.setDateFin(economieSelectionnee.getDerniereGeneration());
                    if (savingDAO.modifier(economieSelectionnee) && savingDAO.creer(suite)) {
                        chargerEconomies();
                        viderFormulaire();
                    } else {
                        AlertUtil.afficherErreur("Erreur", "Impossible d'appliquer le nouveau montant.");
                    }
                    return;
                }
            }

            // Une économie qui devient récurrente compte déjà comme sa première occurrence.
            if (frequence.estRecurrent() && !economieSelectionnee.getFrequence().estRecurrent()) {
                economieSelectionnee.setDerniereGeneration(date);
            }
            // Une ligne générée automatiquement reste une économie ponctuelle.
            if (economieSelectionnee.getIdSource() != null) {
                frequence = Frequency.PONCTUEL;
                dateFin = null;
            }

            economieSelectionnee.setMontant(montant);
            economieSelectionnee.setDescription(champDescription.getText().trim());
            economieSelectionnee.setDateEconomie(date);
            economieSelectionnee.setFrequence(frequence);
            economieSelectionnee.setDateFin(dateFin);

            if (savingDAO.modifier(economieSelectionnee)) {
                chargerEconomies();
                viderFormulaire();
            } else {
                AlertUtil.afficherErreur("Erreur", "Impossible de modifier l'économie.");
            }
        } catch (NumberFormatException e) {
            AlertUtil.afficherErreur("Montant invalide", "Veuillez saisir un montant numérique valide.");
        }
    }

    @FXML
    public void supprimerEconomie() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        if (economieSelectionnee == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner une économie à supprimer.");
            return;
        }
        boolean source = economieSelectionnee.getIdSource() == null && economieSelectionnee.getFrequence().estRecurrent();
        String message = source
                ? "Supprimer cette économie récurrente ? Les économies ajoutées automatiquement seront supprimées aussi."
                : "Supprimer cette économie ?";
        if (AlertUtil.confirmer("Confirmation", message)) {
            if (savingDAO.supprimer(economieSelectionnee.getIdEconomie())) {
                chargerEconomies();
                viderFormulaire();
            } else {
                AlertUtil.afficherErreur("Erreur", "Impossible de supprimer l'économie.");
            }
        }
    }

    @FXML
    public void rechercherEtFiltrer() {
        User utilisateur = SessionManager.getUtilisateurConnecte();
        donnees.setAll(savingDAO.rechercher(
                utilisateur.getIdUser(),
                champRecherche.getText(),
                champFiltreDateDebut.getValue(),
                champFiltreDateFin.getValue()));
    }

    @FXML
    public void reinitialiserFiltres() {
        champRecherche.clear();
        champFiltreDateDebut.setValue(null);
        champFiltreDateFin.setValue(null);
        donnees.setAll(toutes);
    }

    private void viderFormulaire() {
        champMontant.clear();
        champDescription.clear();
        champDate.setValue(null);
        comboFrequence.setValue(Frequency.PONCTUEL);
        champDateFin.setValue(null);
        economieSelectionnee = null;
        tableEconomies.getSelectionModel().clearSelection();
    }
}
