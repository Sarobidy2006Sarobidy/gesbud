package com.budgetmanager.controller;

import com.budgetmanager.dao.CarnetDAO;
import com.budgetmanager.model.Carnet;
import com.budgetmanager.model.SensCarnet;
import com.budgetmanager.model.StatutCarnet;
import com.budgetmanager.model.User;
import com.budgetmanager.util.AlertUtil;
import com.budgetmanager.util.CurrencyUtil;
import com.budgetmanager.util.DbHeartbeat;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.ListSyncUtil;
import com.budgetmanager.util.LongueurFieldFormatter;
import com.budgetmanager.util.NumberFieldFormatter;
import com.budgetmanager.util.RaccourcisClavier;
import com.budgetmanager.util.SessionManager;
import com.budgetmanager.util.TableUtil;
import com.budgetmanager.util.VideEtat;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Écran « Emprunts &amp; prêts » : un carnet de ce que l'utilisateur doit et de
 * ce qu'on lui doit. Il n'appelle aucun DAO des opérations du budget et
 * alimente aucun total : c'est un bloc-notes, pas une comptabilité.
 */
public class CarnetController implements RaccourcisClavier, com.budgetmanager.util.Rafraichissable {

    private static final Logger log = LoggerFactory.getLogger(CarnetController.class);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private ComboBox<SensCarnet> comboSens;
    @FXML private ComboBox<StatutCarnet> comboStatut;
    @FXML private TextField champPersonne;
    @FXML private TextField champMotif;
    @FXML private TextField champMontant;
    @FXML private DatePicker champDateDebut;
    @FXML private DatePicker champDateFin;

    @FXML private Label labelNombre;
    @FXML private Label labelEnCours;
    @FXML private Label labelRegles;
    @FXML private Label pastilleNombre;
    @FXML private javafx.scene.layout.GridPane grilleCartes;

    @FXML private ComboBox<String> comboFiltreSens;
    @FXML private TextField champRecherche;
    @FXML private Button boutonFiltrer;
    @FXML private Button boutonReinitialiser;

    @FXML private TableView<Carnet> tableCarnet;
    @FXML private TableColumn<Carnet, SensCarnet> colonneSens;
    @FXML private TableColumn<Carnet, String> colonnePersonne;
    @FXML private TableColumn<Carnet, String> colonneMotif;
    @FXML private TableColumn<Carnet, Double> colonneMontant;
    @FXML private TableColumn<Carnet, LocalDate> colonneDateDebut;
    @FXML private TableColumn<Carnet, LocalDate> colonneDateFin;
    @FXML private TableColumn<Carnet, StatutCarnet> colonneStatut;

    @FXML private Button boutonAjouter;
    @FXML private Button boutonModifier;
    @FXML private Button boutonSupprimer;

    /** Contrôleur de la barre latérale incluse (fx:id="sidebar" -> champ "sidebarController"). */
    @FXML private SidebarController sidebarController;

    private final CarnetDAO carnetDAO = new CarnetDAO();
    private final ObservableList<Carnet> donnees = FXCollections.observableArrayList();
    private final FilteredList<Carnet> donneesFiltrees = new FilteredList<>(donnees, c -> true);
    private Carnet ecritureSelectionnee;
    private String devise = "Ar";

    @FXML
    public void initialize() {
        sidebarController.setActive("carnet");

        // Deux cartes côte à côte dès qu'elles gardent ~280 px chacune.
        com.budgetmanager.util.ResponsiveGrid.appliquer(grilleCartes,
                largeur -> largeur >= 560 ? 2 : 1);

        boutonAjouter.setGraphic(Icons.plusCircle(15, Color.WHITE));
        boutonModifier.setGraphic(Icons.edit(15, Color.web("#374151")));
        boutonSupprimer.setGraphic(Icons.trash(15, Color.web("#b91c1c")));
        boutonFiltrer.setGraphic(Icons.filter(15, Color.WHITE));
        boutonReinitialiser.setGraphic(Icons.rotateCcw(15, Color.web("#374151")));

        VideEtat.surTable(tableCarnet, Icons.wallet(26, VideEtat.couleurIcone()),
                "Le carnet est vide",
                "Notez ici ce que vous devez et ce qu'on vous doit : rien de tout cela ne touche votre budget.");

        comboSens.setItems(FXCollections.observableArrayList(SensCarnet.values()));
        comboSens.setValue(SensCarnet.EMPRUNT);
        comboStatut.setItems(FXCollections.observableArrayList(StatutCarnet.values()));
        comboStatut.setValue(StatutCarnet.EN_COURS);
        comboFiltreSens.setItems(FXCollections.observableArrayList("Tout", "Je dois", "On me doit"));
        comboFiltreSens.setValue("Tout");

        champMontant.setTextFormatter(NumberFieldFormatter.creer());
        champPersonne.setTextFormatter(LongueurFieldFormatter.creer(80));
        champMotif.setTextFormatter(LongueurFieldFormatter.creer(255));
        champDateDebut.setValue(LocalDate.now());

        colonneSens.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getSens()));
        colonnePersonne.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPersonne()));
        colonneMotif.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getMotif()));
        colonneMontant.setCellValueFactory(cell -> cell.getValue().montantProperty().asObject());
        TableUtil.formaterMontants(colonneMontant);
        colonneDateDebut.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getDateDebut()));
        colonneDateFin.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getDateFin()));
        colonneStatut.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getStatut()));

        // Le sens et le statut sont des états, pas du texte : ils se lisent en couleur.
        puce(colonneSens, SensCarnet::getLibelle,
                s -> s == SensCarnet.EMPRUNT ? "puce-emprunt" : "puce-pret");
        puce(colonneStatut, StatutCarnet::getLibelle,
                s -> s == StatutCarnet.REGLE ? "puce-regle" : "puce-cours");
        dates(colonneDateDebut);
        dates(colonneDateFin);

        SortedList<Carnet> donneesTriees = new SortedList<>(donneesFiltrees);
        donneesTriees.comparatorProperty().bind(tableCarnet.comparatorProperty());
        tableCarnet.setItems(donneesTriees);

        pastilleNombre.textProperty().bind(
                javafx.beans.binding.Bindings.size(tableCarnet.getItems()).asString());

        tableCarnet.getSelectionModel().selectedItemProperty().addListener((obs, ancien, nouveau) -> {
            ecritureSelectionnee = nouveau;
            if (nouveau != null) remplirFormulaire(nouveau);
        });

        chargerCarnet();
    }

    /** Redemandé par la navigation quand la vue revient du cache. */
    @Override
    public void rafraichirDonnees() {
        chargerCarnet();
    }

    private void chargerCarnet() {
        User utilisateur = SessionManager.getUtilisateurConnecte();
        if (utilisateur == null) return;
        int idUser = utilisateur.getIdUser();
        devise = utilisateur.getDevise();

        Task<List<Carnet>> tache = new Task<>() {
            @Override
            protected List<Carnet> call() {
                return carnetDAO.listerParUser(idUser);
            }
        };
        tache.setOnSucceeded(evt -> {
            List<Carnet> ecritures = tache.getValue();
            ListSyncUtil.synchroniser(donnees, ecritures,
                    Carnet::getIdCarnet,
                    (existant, nouveau) -> {
                        existant.setSens(nouveau.getSens());
                        existant.setPersonne(nouveau.getPersonne());
                        existant.setMotif(nouveau.getMotif());
                        existant.setMontant(nouveau.getMontant());
                        existant.setDateDebut(nouveau.getDateDebut());
                        existant.setDateFin(nouveau.getDateFin());
                        existant.setStatut(nouveau.getStatut());
                    });
            labelNombre.setText(String.valueOf(ecritures.size()));
            labelEnCours.setText(String.valueOf(ecritures.stream()
                    .filter(e -> e.getStatut() == StatutCarnet.EN_COURS).count()));
            labelRegles.setText(String.valueOf(ecritures.size()
                    - ecritures.stream().filter(e -> e.getStatut() == StatutCarnet.EN_COURS).count()));
        });
        tache.setOnFailed(evt -> log.error("Chargement du carnet échoué", tache.getException()));
        com.budgetmanager.util.DbExecutor.soumettre(tache);
    }

    private void remplirFormulaire(Carnet ecriture) {
        comboSens.setValue(ecriture.getSens());
        comboStatut.setValue(ecriture.getStatut());
        champPersonne.setText(ecriture.getPersonne());
        champMotif.setText(ecriture.getMotif());
        champMontant.setText(NumberFieldFormatter.ecrire(ecriture.getMontant()));
        champDateDebut.setValue(ecriture.getDateDebut());
        champDateFin.setValue(ecriture.getDateFin());
    }

    /** L'écriture du formulaire, ou null avec le message d'erreur affiché. */
    private Carnet lireFormulaire() {
        User utilisateur = SessionManager.getUtilisateurConnecte();
        String personne = champPersonne.getText().trim();
        if (personne.isEmpty()) {
            AlertUtil.afficherErreur("Champ manquant", "Dites avec qui l'argent est en jeu.");
            return null;
        }
        if (champDateDebut.getValue() == null) {
            AlertUtil.afficherErreur("Champ manquant", "Veuillez choisir une date.");
            return null;
        }
        double montant;
        try {
            montant = Double.parseDouble(champMontant.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            AlertUtil.afficherErreur("Montant invalide", "Veuillez saisir un montant numérique valide.");
            return null;
        }
        if (montant <= 0) {
            AlertUtil.afficherErreur("Montant invalide", "Le montant doit être supérieur à 0.");
            return null;
        }
        LocalDate debut = champDateDebut.getValue();
        LocalDate fin = champDateFin.getValue();
        if (fin != null && fin.isBefore(debut)) {
            AlertUtil.afficherErreur("Dates incohérentes", "La date de retour doit être après la date de l'écriture.");
            return null;
        }

        Carnet ecriture = new Carnet(utilisateur.getIdUser(),
                comboSens.getValue() == null ? SensCarnet.EMPRUNT : comboSens.getValue(),
                personne, champMotif.getText().trim(), montant, debut);
        ecriture.setDateFin(fin);
        ecriture.setStatut(comboStatut.getValue() == null ? StatutCarnet.EN_COURS : comboStatut.getValue());
        return ecriture;
    }

    @FXML
    public void ajouterEcriture() {
        if (!DbHeartbeat.ok()) return;
        Carnet ecriture = lireFormulaire();
        if (ecriture == null) return;

        if (carnetDAO.creer(ecriture)) {
            chargerCarnet();
            viderFormulaire();
        } else {
            AlertUtil.afficherErreur("Erreur", "Impossible d'ajouter l'écriture.");
        }
    }

    /** Ctrl+N : même action que le bouton « Ajouter ». */
    @Override
    public void auRaccourciAjouter() { ajouterEcriture(); }

    @FXML
    public void modifierEcriture() {
        if (!DbHeartbeat.ok()) return;
        if (ecritureSelectionnee == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner une écriture à modifier.");
            return;
        }
        Carnet nouvelle = lireFormulaire();
        if (nouvelle == null) return;

        ecritureSelectionnee.setSens(nouvelle.getSens());
        ecritureSelectionnee.setStatut(nouvelle.getStatut());
        ecritureSelectionnee.setPersonne(nouvelle.getPersonne());
        ecritureSelectionnee.setMotif(nouvelle.getMotif());
        ecritureSelectionnee.setMontant(nouvelle.getMontant());
        ecritureSelectionnee.setDateDebut(nouvelle.getDateDebut());
        ecritureSelectionnee.setDateFin(nouvelle.getDateFin());

        if (carnetDAO.modifier(ecritureSelectionnee)) {
            chargerCarnet();
            viderFormulaire();
        } else {
            AlertUtil.afficherErreur("Erreur", "Impossible de modifier l'écriture.");
        }
    }

    /** Ctrl+S : enregistrer, en ajoutant si aucune ligne n'est sélectionnée. */
    @Override
    public void auRaccourciEnregistrer() {
        if (ecritureSelectionnee == null) ajouterEcriture();
        else modifierEcriture();
    }

    @FXML
    public void supprimerEcriture() {
        if (!DbHeartbeat.ok()) return;
        if (ecritureSelectionnee == null) {
            AlertUtil.afficherErreur("Aucune sélection", "Veuillez sélectionner une écriture à supprimer.");
            return;
        }
        String detail = ecritureSelectionnee.getPersonne() + " · "
                + CurrencyUtil.formater(ecritureSelectionnee.getMontant(), devise);
        if (!AlertUtil.confirmer("Confirmation", "Supprimer cette écriture du carnet ?\n" + detail)) return;

        if (carnetDAO.supprimer(ecritureSelectionnee.getIdCarnet(),
                SessionManager.getUtilisateurConnecte().getIdUser())) {
            chargerCarnet();
            viderFormulaire();
        } else {
            AlertUtil.afficherErreur("Erreur", "Impossible de supprimer l'écriture.");
        }
    }

    @FXML
    public void rechercherEtFiltrer() {
        String filtreSens = comboFiltreSens.getValue();
        String motCle = champRecherche.getText().trim().toLowerCase();
        donneesFiltrees.setPredicate(ecriture ->
                switch (filtreSens == null ? "Tout" : filtreSens) {
                    case "Je dois" -> ecriture.getSens() == SensCarnet.EMPRUNT;
                    case "On me doit" -> ecriture.getSens() == SensCarnet.PRET;
                    default -> true;
                }
                && (motCle.isEmpty()
                        || ecriture.getPersonne().toLowerCase().contains(motCle)
                        || ecriture.getMotif().toLowerCase().contains(motCle)));
    }

    @FXML
    public void reinitialiserFiltres() {
        comboFiltreSens.setValue("Tout");
        champRecherche.clear();
        donneesFiltrees.setPredicate(c -> true);
    }

    private void viderFormulaire() {
        comboSens.setValue(SensCarnet.EMPRUNT);
        comboStatut.setValue(StatutCarnet.EN_COURS);
        champPersonne.clear();
        champMotif.clear();
        champMontant.clear();
        champDateDebut.setValue(LocalDate.now());
        champDateFin.setValue(null);
        ecritureSelectionnee = null;
        tableCarnet.getSelectionModel().clearSelection();
    }

    /** Une colonne d'état ne se lit pas, elle se reconnaît : texte court, fond coloré. */
    private static <T> void puce(TableColumn<Carnet, T> colonne,
                                 Function<T, String> texte,
                                 Function<T, String> classe) {
        colonne.setCellFactory(colonneParam -> new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label puce = new Label(texte.apply(item));
                puce.getStyleClass().addAll("carnet-puce", classe.apply(item));
                setGraphic(puce);
            }
        });
    }

    /** Les dates du carnet se lisent « 15/10/2026 » ; une absence de date se dit. */
    private static void dates(TableColumn<Carnet, LocalDate> colonne) {
        colonne.setCellFactory(colonneParam -> new TableCell<>() {
            {   // Le montant est aligné à droite : sans respiration ici, les deux se touchent.
                setPadding(new javafx.geometry.Insets(0, 0, 0, 12));
            }

            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setGraphic(null);
                setText(empty || date == null ? null : date.format(JOUR));
            }
        });
    }
}
