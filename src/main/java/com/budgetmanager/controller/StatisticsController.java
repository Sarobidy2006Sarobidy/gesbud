package com.budgetmanager.controller;

import com.budgetmanager.dao.ExpenseDAO;
import com.budgetmanager.dao.IncomeDAO;
import com.budgetmanager.dao.SavingDAO;
import com.budgetmanager.model.User;
import com.budgetmanager.util.SessionManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class StatisticsController {

    private static final Logger log = LoggerFactory.getLogger(StatisticsController.class);

    @FXML private PieChart pieRevenus;
    @FXML private PieChart pieDepenses;
    @FXML private BarChart<String, Number> barRevenusMensuels;
    @FXML private BarChart<String, Number> barDepensesMensuelles;
    @FXML private LineChart<String, Number> lineSolde;

    /** Contrôleur de la barre latérale incluse (fx:id="sidebar" -> champ "sidebarController"). */
    @FXML private SidebarController sidebarController;

    private final IncomeDAO incomeDAO = new IncomeDAO();
    @FXML private javafx.scene.layout.GridPane grilleGraphiques;

    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final SavingDAO savingDAO = new SavingDAO();

    private static final String[] MOIS_LABELS = {
            "Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"
    };

    @FXML
    public void initialize() {
        sidebarController.setActive("statistiques");
        com.budgetmanager.util.ResponsiveGrid.appliquer(grilleGraphiques, largeur -> largeur >= 740 ? 2 : 1);

        User utilisateur = SessionManager.getUtilisateurConnecte();
        int idUser = utilisateur.getIdUser();
        int annee = YearMonth.now().getYear();

        Task<Void> tache = new Task<>() {
            // Résultats calculés sur le thread de fond.
            List<Object[]> repartitionRevenus;
            List<Object[]> repartitionDepenses;
            List<Double> revenusMensuels;
            List<Double> depensesMensuelles;
            List<Double> economiesMensuelles;
            double soldeInitial;
            double economiesInitiales;

            @Override
            protected Void call() {
                incomeDAO.genererRecurrences(idUser);
                expenseDAO.genererRecurrences(idUser);
                savingDAO.genererRecurrences(idUser);

                repartitionRevenus  = incomeDAO.repartitionParCategorie(idUser);
                repartitionDepenses = expenseDAO.repartitionParCategorie(idUser);
                revenusMensuels     = totauxAnnee(idUser, true);
                depensesMensuelles  = totauxAnnee(idUser, false);
                economiesMensuelles = new ArrayList<>();
                for (int mois = 1; mois <= 12; mois++) {
                    economiesMensuelles.add(savingDAO.totalParMois(idUser, YearMonth.of(annee, mois)));
                }
                soldeInitial      = incomeDAO.totalAvantAnnee(idUser, annee)
                                  - expenseDAO.totalAvantAnnee(idUser, annee)
                                  - savingDAO.totalAvantAnnee(idUser, annee);
                economiesInitiales = savingDAO.totalAvantAnnee(idUser, annee);
                return null;
            }

            @Override
            protected void succeeded() {
                remplirPieChart(pieRevenus,  repartitionRevenus,  "Répartition des revenus");
                remplirPieChart(pieDepenses, repartitionDepenses, "Répartition des dépenses");
                remplirBarChart(barRevenusMensuels,    revenusMensuels,    "Revenus " + annee);
                remplirBarChart(barDepensesMensuelles, depensesMensuelles, "Dépenses " + annee);
                remplirLineChartSolde(revenusMensuels, depensesMensuelles,
                        economiesMensuelles, soldeInitial, economiesInitiales);
            }
        };

        tache.setOnFailed(evt -> log.error("Chargement statistiques échoué", tache.getException()));
        com.budgetmanager.util.DbExecutor.soumettre(tache);
    }

    private List<Double> totauxAnnee(int idUser, boolean revenus) {
        List<Double> totaux = new ArrayList<>();
        int anneeActuelle = YearMonth.now().getYear();
        for (int mois = 1; mois <= 12; mois++) {
            YearMonth ym = YearMonth.of(anneeActuelle, mois);
            double total = revenus ? incomeDAO.totalParMois(idUser, ym) : expenseDAO.totalParMois(idUser, ym);
            totaux.add(total);
        }
        return totaux;
    }

    private void remplirPieChart(PieChart chart, List<Object[]> donnees, String titre) {
        chart.setTitle(titre);
        // Mettre à jour les données existantes sans tout effacer → pas de clignotement.
        List<PieChart.Data> nouvellesDonnees = new ArrayList<>();
        for (Object[] ligne : donnees) {
            String nom = (String) ligne[0];
            double valeur = (Double) ligne[1];
            if (valeur > 0) nouvellesDonnees.add(new PieChart.Data(nom, valeur));
        }
        // Synchroniser avec la liste existante : ajout/suppression seulement si nécessaire.
        if (chart.getData().size() == nouvellesDonnees.size()) {
            for (int i = 0; i < nouvellesDonnees.size(); i++) {
                chart.getData().get(i).setPieValue(nouvellesDonnees.get(i).getPieValue());
                chart.getData().get(i).setName(nouvellesDonnees.get(i).getName());
            }
        } else {
            chart.getData().setAll(nouvellesDonnees);
        }
    }

    private void remplirBarChart(BarChart<String, Number> chart, List<Double> valeursMensuelles, String titre) {
        chart.setTitle(titre);
        if (chart.getData().isEmpty()) {
            // Premier chargement : créer la série.
            XYChart.Series<String, Number> serie = new XYChart.Series<>();
            for (int i = 0; i < 12; i++) {
                serie.getData().add(new XYChart.Data<>(MOIS_LABELS[i], valeursMensuelles.get(i)));
            }
            chart.getData().add(serie);
        } else {
            // Mise à jour : modifier les valeurs existantes sans recréer → zéro clignotement.
            XYChart.Series<String, Number> serie = chart.getData().get(0);
            for (int i = 0; i < 12; i++) {
                serie.getData().get(i).setYValue(valeursMensuelles.get(i));
            }
        }
    }

    private void remplirLineChartSolde(List<Double> revenusMensuels, List<Double> depensesMensuelles,
                                       List<Double> economiesMensuelles,
                                       double soldeInitial, double economiesInitiales) {
        lineSolde.setTitle("Solde disponible et économies " + YearMonth.now().getYear());

        double soldeCumule = soldeInitial;
        double economiesCumulees = economiesInitiales;

        if (lineSolde.getData().size() < 2) {
            // Premier chargement : créer les deux séries.
            lineSolde.getData().clear();
            XYChart.Series<String, Number> serieSolde = new XYChart.Series<>();
            serieSolde.setName("Solde disponible");
            XYChart.Series<String, Number> serieEconomies = new XYChart.Series<>();
            serieEconomies.setName("Économies cumulées");
            for (int i = 0; i < 12; i++) {
                soldeCumule += revenusMensuels.get(i) - depensesMensuelles.get(i) - economiesMensuelles.get(i);
                economiesCumulees += economiesMensuelles.get(i);
                serieSolde.getData().add(new XYChart.Data<>(MOIS_LABELS[i], soldeCumule));
                serieEconomies.getData().add(new XYChart.Data<>(MOIS_LABELS[i], economiesCumulees));
            }
            lineSolde.getData().add(serieSolde);
            lineSolde.getData().add(serieEconomies);
        } else {
            // Mise à jour : modifier les valeurs en place → pas de recalcul visuel complet.
            XYChart.Series<String, Number> serieSolde    = lineSolde.getData().get(0);
            XYChart.Series<String, Number> serieEconomies = lineSolde.getData().get(1);
            for (int i = 0; i < 12; i++) {
                soldeCumule += revenusMensuels.get(i) - depensesMensuelles.get(i) - economiesMensuelles.get(i);
                economiesCumulees += economiesMensuelles.get(i);
                serieSolde.getData().get(i).setYValue(soldeCumule);
                serieEconomies.getData().get(i).setYValue(economiesCumulees);
            }
        }
    }

}
