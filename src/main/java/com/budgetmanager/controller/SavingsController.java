package com.budgetmanager.controller;

import com.budgetmanager.dao.SavingDAO;
import com.budgetmanager.model.Frequency;
import com.budgetmanager.model.Saving;
import com.budgetmanager.model.User;
import com.budgetmanager.util.AlertUtil;
import com.budgetmanager.util.CurrencyUtil;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.ListSyncUtil;
import com.budgetmanager.util.RaccourcisClavier;
import com.budgetmanager.util.SavingsCurve;
import com.budgetmanager.util.SavingsCurve.Period;
import com.budgetmanager.util.SessionManager;
import com.budgetmanager.util.SqlUtil;
import com.budgetmanager.util.VideEtat;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.AreaChart;
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
public class SavingsController implements RaccourcisClavier, com.budgetmanager.util.Rafraichissable {

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
    @FXML private Label pastilleNombre;
    @FXML private javafx.scene.layout.GridPane grilleCartes;
    @FXML private Label labelAlerteSolde;
    @FXML private ComboBox<Period> comboPeriode;
    @FXML private AreaChart<String, Number> courbeEconomies;

    @FXML private TextField champRecherche;
    @FXML private DatePicker champFiltreDateDebut;
    @FXML private DatePicker champFiltreDateFin;
    @FXML private Button boutonFiltrer;
    @FXML private Button boutonReinitialiser;

    @FXML private TextField champRetraitMontant;
    @FXML private TextField champRetraitMotif;
    @FXML private DatePicker champRetraitDate;
    @FXML private Button boutonRetirer;
    @FXML private Label labelTirelire;
    @FXML private Label labelApresRetrait;
    @FXML private Label labelDetailMouvements;
    @FXML private javafx.scene.layout.StackPane sortiePuce;

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
    /** Toutes les économies : la courbe et les totaux ne dépendent jamais du filtre de recherche.
     *  Assigné uniquement sur le thread FX (dans le setOnSucceeded du chargement). */
    private List<Saving> toutes = List.of();
    private Saving economieSelectionnee;
    /** Ce que contient la tirelire aujourd'hui : le plafond naturel d'un retrait. */
    private double tirelire;
    private String devise = "Ar";

    @FXML
    public void initialize() {
        sidebarController.setActive("economies");

        // Deux cartes côte à côte dès qu'elles gardent ~280 px chacune.
        com.budgetmanager.util.ResponsiveGrid.appliquer(grilleCartes,
                largeur -> largeur >= 560 ? 2 : 1);

        boutonAjouter.setGraphic(Icons.plusCircle(15, Color.WHITE));
        boutonModifier.setGraphic(Icons.edit(15, Color.web("#374151")));
        boutonSupprimer.setGraphic(Icons.trash(15, Color.web("#b91c1c")));
        boutonFiltrer.setGraphic(Icons.filter(15, Color.WHITE));
        boutonReinitialiser.setGraphic(Icons.rotateCcw(15, Color.web("#374151")));
        boutonRetirer.setGraphic(Icons.arrowLeft(15, Color.web("#b3261e")));
        sortiePuce.getChildren().setAll(Icons.trendingDown(18, Color.web("#b3261e")));

        VideEtat.surTable(tableEconomies, Icons.pieChart(26, VideEtat.couleurIcone()),
                "Aucune économie",
                "Placez de l'argent de côté depuis le formulaire : la courbe le suivira mois par mois, "
                        + "et il redescendra le jour où vous reprendrez ce qui est dans la tirelire.");

        colonneMontant.setCellValueFactory(cellData -> cellData.getValue().montantProperty().asObject());
        com.budgetmanager.util.TableUtil.formaterMontants(colonneMontant);
        colonneDescription.setCellValueFactory(cellData -> cellData.getValue().descriptionProperty());
        colonneDate.setCellValueFactory(cellData -> cellData.getValue().dateEconomieProperty());
        com.budgetmanager.util.TableUtil.marquerPrevues(colonneDate);
        // Une ligne négative n'est pas une fréquence, c'est une sortie : elle se dit telle quelle.
        colonneFrequence.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getMontant() < 0
                        ? "Retiré de la tirelire"
                        : cellData.getValue().getLibelleFrequence()));

        // Validation : champ montant accepte uniquement des nombres décimaux.
        champMontant.setTextFormatter(com.budgetmanager.util.NumberFieldFormatter.creer());
        champDescription.setTextFormatter(com.budgetmanager.util.LongueurFieldFormatter.creer(255));
        champRetraitMontant.setTextFormatter(com.budgetmanager.util.NumberFieldFormatter.creer());
        champRetraitMotif.setTextFormatter(com.budgetmanager.util.LongueurFieldFormatter.creer(255));
        champRetraitDate.setValue(LocalDate.now());
        champRetraitMontant.textProperty().addListener((obs, ancien, nouveau) -> apercuRetrait());
        tableEconomies.setItems(donnees);

        // La pastille suit les lignes visibles, hors opérations prévues.
        pastilleNombre.textProperty().bind(
                com.budgetmanager.util.TableUtil.compterRealisees(
                        tableEconomies.getItems(), Saving::getDateEconomie));
        labelTotal.setTooltip(new javafx.scene.control.Tooltip(
                "Ce qui reste dans la tirelire : ce que vous avez mis de côté, moins ce que vous en avez repris.\n" +
                "Les opérations datées après aujourd'hui sont prévues : elles restent au tableau,\n" +
                "elles n'entrent dans aucun total, ni ici ni au tableau de bord."));

        comboFrequence.setItems(FXCollections.observableArrayList(Frequency.values()));
        comboFrequence.setValue(Frequency.PONCTUEL);
        comboFrequence.valueProperty().addListener((obs, ancien, nouveau) -> mettreAJourFormulaireFrequence());
        mettreAJourFormulaireFrequence();
        champDate.setValue(LocalDate.now());

        comboPeriode.setItems(FXCollections.observableArrayList(Period.values()));
        comboPeriode.setValue(Period.MOIS);
        comboPeriode.valueProperty().addListener((obs, ancien, nouveau) -> afficherCourbe());

        courbeEconomies.setLegendVisible(false);
        courbeEconomies.setAnimated(false);
        ((javafx.scene.chart.NumberAxis) courbeEconomies.getYAxis())
                .setTickLabelFormatter(com.budgetmanager.util.CurrencyUtil.compact());
        // Le point passé sous zéro ne se repère qu'après disposition : c'est là que
        // la courbe fabrique ses symboles.
        courbeEconomies.needsLayoutProperty().addListener((obs, ancien, nouveau) -> {
            if (!nouveau) com.budgetmanager.util.MarqueursDeficit.marquer(courbeEconomies);
        });

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

    /** Redemandé par la navigation quand la vue revient du cache. */
    @Override
    public void rafraichirDonnees() {
        chargerEconomies();
    }

    private void chargerEconomies() {
        User utilisateur = SessionManager.getUtilisateurConnecte();
        int idUser = utilisateur.getIdUser();
        devise = utilisateur.getDevise();

        // Données calculées en arrière-plan pour ne pas bloquer l'UI.
        // La liste voyage dans la valeur de la Task : « toutes » n'est écrit que sur le thread FX.
        Task<Chargement> tacheStats = new Task<>() {
            @Override
            protected Chargement call() {
                savingDAO.genererRecurrences(idUser);
                List<Saving> economies = savingDAO.listerParUser(idUser);
                double total = savingDAO.totalParUser(idUser);
                double mois  = savingDAO.totalParMois(idUser, YearMonth.now());
                double solde = com.budgetmanager.util.BudgetUtil.soldeDisponible(idUser);
                List<Saving> faites = economies.stream()
                        .filter(e -> SqlUtil.estRealisee(e.getDateEconomie())).toList();
                return new Chargement(economies, total, mois, solde,
                        (int) faites.stream().filter(e -> e.getMontant() > 0).count(),
                        (int) faites.stream().filter(e -> e.getMontant() < 0).count());
            }
        };
        tacheStats.setOnSucceeded(evt -> {
            Chargement c = tacheStats.getValue();
            toutes = c.economies();
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
            labelTotal.setText(CurrencyUtil.formater(c.total(), devise));
            labelMois.setText(CurrencyUtil.formater(c.mois(), devise));
            labelNombre.setText(String.valueOf(c.economies().stream()
                    .filter(e -> SqlUtil.estRealisee(e.getDateEconomie())).count()));
            tirelire = c.total();
            labelTirelire.setText(CurrencyUtil.formater(c.total(), devise));
            labelDetailMouvements.setText(c.deposes() + " dépôt" + (c.deposes() > 1 ? "s" : "")
                    + " · " + c.retraits() + " retrait" + (c.retraits() > 1 ? "s" : ""));
            apercuRetrait();
            double solde = c.solde();
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

        courbeEconomies.setTitle("Ce qu'il reste dans la tirelire — " + periode.toString().toLowerCase());
        courbeEconomies.setCreateSymbols(true);
        courbeEconomies.getData().clear();

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (SavingsCurve.Point p : points) {
            serie.getData().add(new XYChart.Data<>(p.libelle(), p.cumul()));
        }
        courbeEconomies.getData().add(serie);
    }

    private void remplirFormulaire(Saving economie) {
        // Une ligne négative est une sortie : elle se corrige dans le formulaire de retrait.
        if (economie.getMontant() < 0) {
            champRetraitMontant.setText(com.budgetmanager.util.NumberFieldFormatter.ecrire(-economie.getMontant()));
            champRetraitMotif.setText(economie.getDescription());
            champRetraitDate.setValue(economie.getDateEconomie());
            return;
        }
        champMontant.setText(com.budgetmanager.util.NumberFieldFormatter.ecrire(economie.getMontant()));
        champDescription.setText(economie.getDescription());
        champDate.setValue(economie.getDateEconomie());
        comboFrequence.setValue(economie.getFrequence());
        champDateFin.setValue(economie.getDateFin());
    }

    /** Le montant qu'on veut reprendre, ou null si la saisie n'est pas encore exploitable. */
    private Double montantDemande() {
        return com.budgetmanager.util.NumberFieldFormatter.lire(champRetraitMontant.getText());
    }

    /** Pendant la frappe, dit ce que la tirelire contiendra après le retrait. */
    private void apercuRetrait() {
        Double montant = montantDemande();
        if (montant == null) {
            labelApresRetrait.setText("");
            return;
        }
        double reste = tirelire - montant;
        labelApresRetrait.setText("Il resterait " + CurrencyUtil.formater(reste, devise) + " dans la tirelire"
                + (reste < 0 ? ", soit " + CurrencyUtil.formater(-reste, devise)
                              + " de plus que ce qu'elle contient." : "."));
    }

    /**
     * Reprise d'argent déjà mis de côté : la tirelire baisse et le solde disponible remonte
     * du même montant, ce qui permet de dépenser cet argent sans le compter deux fois.
     */
    @FXML
    public void retirerEconomie() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        Double montant = montantDemande();
        if (montant == null) {
            AlertUtil.afficherErreur("Montant invalide", "Indiquez le montant à reprendre, supérieur à 0.");
            return;
        }
        LocalDate date = champRetraitDate.getValue();
        if (date == null) {
            AlertUtil.afficherErreur("Champ manquant", "Veuillez choisir une date.");
            return;
        }
        if (montant > tirelire && !AlertUtil.confirmer("Retrait trop grand",
                "Vous reprenez " + CurrencyUtil.formater(montant, devise) + ", mais la tirelire ne contient que "
                        + CurrencyUtil.formater(tirelire, devise) + ".\nContinuer quand même ? "
                        + "Ce qui manque sera compté comme déjà dépensé.")) {
            return;
        }

        User utilisateur = SessionManager.getUtilisateurConnecte();
        Saving retrait = new Saving(utilisateur.getIdUser(), -montant, champRetraitMotif.getText().trim(), date);
        if (savingDAO.creer(retrait)) {
            chargerEconomies();
            viderFormulaireRetrait();
        } else {
            AlertUtil.afficherErreur("Erreur", "Impossible d'enregistrer le retrait.");
        }
    }

    /** Corriger une reprise déjà enregistrée — la ligne garde son signe négatif. */
    private void modifierRetrait() {
        Double montant = montantDemande();
        if (montant == null) {
            AlertUtil.afficherErreur("Montant invalide", "Indiquez le montant à reprendre, supérieur à 0.");
            return;
        }
        LocalDate date = champRetraitDate.getValue();
        if (date == null) {
            AlertUtil.afficherErreur("Champ manquant", "Veuillez choisir une date.");
            return;
        }
        economieSelectionnee.setMontant(-montant);
        economieSelectionnee.setDescription(champRetraitMotif.getText().trim());
        economieSelectionnee.setDateEconomie(date);
        if (savingDAO.modifier(economieSelectionnee)) {
            chargerEconomies();
            viderFormulaireRetrait();
        } else {
            AlertUtil.afficherErreur("Erreur", "Impossible de modifier le retrait.");
        }
    }

    private void viderFormulaireRetrait() {
        champRetraitMontant.clear();
        champRetraitMotif.clear();
        champRetraitDate.setValue(LocalDate.now());
        economieSelectionnee = null;
        tableEconomies.getSelectionModel().clearSelection();
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

    /** Ctrl+N : même action que le bouton « Ajouter ». */
    @Override
    public void auRaccourciAjouter() { ajouterEconomie(); }

    @FXML
    public void modifierEconomie() {
        if (!com.budgetmanager.util.DbHeartbeat.ok()) return;
        if (economieSelectionnee == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner une économie à modifier.");
            return;
        }
        if (economieSelectionnee.getMontant() < 0) {
            modifierRetrait();
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
                    if (savingDAO.changerMontantRecurrent(economieSelectionnee, suite)) {
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
        String message = economieSelectionnee.getMontant() < 0
                ? "Supprimer ce retrait ? L'argent retourne dans la tirelire."
                : source
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
        String motCle = champRecherche.getText();
        LocalDate debut = champFiltreDateDebut.getValue();
        LocalDate fin = champFiltreDateFin.getValue();
        Task<List<Saving>> tache = new Task<>() {
            @Override
            protected List<Saving> call() {
                return savingDAO.rechercher(utilisateur.getIdUser(), motCle, debut, fin);
            }
        };
        tache.setOnSucceeded(evt -> donnees.setAll(tache.getValue()));
        tache.setOnFailed(evt -> log.error("Recherche économies échouée", tache.getException()));
        com.budgetmanager.util.DbExecutor.soumettre(tache);
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
        champDate.setValue(LocalDate.now());
        comboFrequence.setValue(Frequency.PONCTUEL);
        champDateFin.setValue(null);
        economieSelectionnee = null;
        tableEconomies.getSelectionModel().clearSelection();
    }

    /** Résultat du chargement en arrière-plan : liste complète et totaux rapatriés ensemble sur le thread FX. */
    private record Chargement(List<Saving> economies, double total, double mois, double solde,
                              int deposes, int retraits) {}
}
