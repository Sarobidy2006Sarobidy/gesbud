package com.budgetmanager.controller;

import com.budgetmanager.dao.ExpenseDAO;
import com.budgetmanager.dao.IncomeDAO;
import com.budgetmanager.dao.SavingDAO;
import com.budgetmanager.model.User;
import com.budgetmanager.util.BudgetUtil;
import com.budgetmanager.util.CurrencyUtil;
import com.budgetmanager.util.SessionManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class DashboardController implements com.budgetmanager.util.Rafraichissable {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    @FXML private Label labelBienvenue;
    @FXML private Label labelTotalRevenus;
    @FXML private Label labelTotalDepenses;
    @FXML private Label labelSolde;
    @FXML private Label labelNombreRevenus;
    @FXML private Label labelNombreDepenses;
    @FXML private javafx.scene.layout.GridPane grilleCartes;
    @FXML private Label labelTotalEconomies;
    @FXML private Label labelAlerteSolde;
    @FXML private Label labelTauxEpargne;
    @FXML private Label labelNombreEconomies;

    /** Indicateurs du mois choisi dans le sélecteur, avec évolution vs mois précédent. */
    @FXML private javafx.scene.control.ComboBox<YearMonth> comboPeriode;
    @FXML private Label labelAnnee;
    @FXML private Label labelRevenusMois;
    @FXML private Label badgeRevenus;
    @FXML private Label detailRevenusMois;
    @FXML private Label labelDepensesMois;
    @FXML private Label badgeDepenses;
    @FXML private Label detailDepensesMois;
    @FXML private Label labelEconomiesMois;
    @FXML private Label badgeEconomies;
    @FXML private Label detailEconomiesMois;
    @FXML private Label labelSoldeMois;
    @FXML private Label badgeSolde;
    @FXML private Label detailSoldeMois;

    /** Graphiques de la section statistiques (fusion de l'ancienne page Statistics). */
    @FXML private javafx.scene.layout.GridPane grilleGraphiques;
    @FXML private PieChart pieRevenus;
    @FXML private PieChart pieDepenses;
    @FXML private BarChart<String, Number> barRevenusMensuels;
    @FXML private BarChart<String, Number> barDepensesMensuelles;
    @FXML private LineChart<String, Number> lineSolde;

    /** Contrôleur de la barre latérale incluse (fx:id="sidebar" -> champ "sidebarController"). */
    @FXML private SidebarController sidebarController;

    private final IncomeDAO incomeDAO = new IncomeDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final SavingDAO savingDAO = new SavingDAO();

    private static final String[] MOIS_LABELS = {
            "Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"
    };

    private User utilisateurCourant;

    @FXML
    public void initialize() {
        sidebarController.setActive("dashboard");
        // 4 colonnes dès que chaque carte garde ~210 px (montants Poppins 22 px).
        com.budgetmanager.util.ResponsiveGrid.appliquer(grilleCartes,
                largeur -> largeur >= 880 ? 4 : largeur >= 560 ? 2 : 1);
        com.budgetmanager.util.ResponsiveGrid.appliquer(grilleGraphiques,
                largeur -> largeur >= 740 ? 2 : 1);
        // Le bandeau solde est un TilePane : il replie ses trois blocs de lui-même.

        // Les cartes portent une ombre (flou gaussien) recalculée à chaque repaint :
        // leur bitmap est confié au cache GPU, elles ne changent qu'au remplissage.
        for (javafx.scene.Node carte : grilleCartes.getChildren()) {
            carte.setCache(true);
            carte.setCacheHint(javafx.scene.CacheHint.SPEED);
        }

        configurerSelecteurPeriode();
        compacterAxesDesGraphiques();

        User utilisateur = SessionManager.getUtilisateurConnecte();
        if (utilisateur == null) return;
        utilisateurCourant = utilisateur;

        // Nom et devise accrochés aux propriétés de l'utilisateur : ils sont
        // modifiables dans Paramètres et doivent se refléter ici sans redémarrage.
        labelBienvenue.textProperty().bind(
                javafx.beans.binding.Bindings.createStringBinding(
                        () -> "Bienvenue, " + utilisateur.getNomComplet()
                              + "  ·  devise " + utilisateur.getDevise(),
                        utilisateur.nomProperty(), utilisateur.prenomProperty(),
                        utilisateur.deviseProperty()));

        chargerDonnees(utilisateur);
        chargerMoisSelectionne();
    }

    /** Les 12 derniers mois, du plus récent au plus ancien ; libellé « janvier 2026 ». */
    private void configurerSelecteurPeriode() {
        YearMonth moisCourant = YearMonth.now();
        for (int i = 0; i < 12; i++) {
            comboPeriode.getItems().add(moisCourant.minusMonths(i));
        }
        comboPeriode.setConverter(new javafx.util.StringConverter<YearMonth>() {
            @Override
            public String toString(YearMonth mois) {
                return mois == null ? "" : mois.getMonth().getDisplayName(
                        java.time.format.TextStyle.FULL, java.util.Locale.FRANCE)
                        + " " + mois.getYear();
            }
            @Override
            public YearMonth fromString(String texte) {
                throw new UnsupportedOperationException();
            }
        });
        comboPeriode.setValue(moisCourant);
        comboPeriode.valueProperty().addListener((obs, avant, apres) -> {
            if (apres != null && utilisateurCourant != null) chargerMoisSelectionne();
        });
        labelAnnee.setText("Analyse de l'année " + moisCourant.getYear());
    }

    /** Axe des montants libellé en milliers/millions : « 1 250 000 » est illisible. */
    private void compacterAxesDesGraphiques() {
        for (javafx.scene.chart.XYChart<String, Number> graphique : java.util.List.of(
                barRevenusMensuels, barDepensesMensuelles, lineSolde)) {
            ((javafx.scene.chart.NumberAxis) graphique.getYAxis())
                    .setTickLabelFormatter(com.budgetmanager.util.CurrencyUtil.compact());
        }
    }

    /** Résultats du calcul mensuel : valeurs du mois M et précédent + répartition du mois. */
    private record Mois(double revenus, double revenusPrecedents,
                        double depenses, double depensesPrecedentes,
                        double economies, double economiesPrecedentes,
                        List<Object[]> repartitionRevenus,
                        List<Object[]> repartitionDepenses) {}

    private void chargerMoisSelectionne() {
        YearMonth mois = comboPeriode.getValue();
        User utilisateur = utilisateurCourant;
        if (mois == null || utilisateur == null) return;

        int idUser = utilisateur.getIdUser();
        String devise = utilisateur.getDevise();
        YearMonth precedent = mois.minusMonths(1);

        Task<Mois> tache = new Task<>() {
            @Override
            protected Mois call() {
                return new Mois(
                        incomeDAO.totalParMois(idUser, mois),
                        incomeDAO.totalParMois(idUser, precedent),
                        expenseDAO.totalParMois(idUser, mois),
                        expenseDAO.totalParMois(idUser, precedent),
                        savingDAO.totalParMois(idUser, mois),
                        savingDAO.totalParMois(idUser, precedent),
                        incomeDAO.repartitionParCategorie(idUser, mois),
                        expenseDAO.repartitionParCategorie(idUser, mois));
            }
        };

        tache.setOnSucceeded(evt -> afficherMois(tache.getValue(), mois, devise));
        tache.setOnFailed(evt -> log.error("Chargement du mois {} échoué", mois, tache.getException()));
        com.budgetmanager.util.DbExecutor.soumettre(tache);
    }

    private void afficherMois(Mois valeurs, YearMonth mois, String devise) {
        double soldeMois = valeurs.revenus() - valeurs.depenses() - valeurs.economies();
        double soldePrecedent = valeurs.revenusPrecedents() - valeurs.depensesPrecedentes()
                - valeurs.economiesPrecedentes();

        labelRevenusMois.setText(CurrencyUtil.formater(valeurs.revenus(), devise));
        labelDepensesMois.setText(CurrencyUtil.formater(valeurs.depenses(), devise));
        labelEconomiesMois.setText(CurrencyUtil.formater(valeurs.economies(), devise));
        labelSoldeMois.setText(CurrencyUtil.formater(soldeMois, devise));

        detailRevenusMois.setText("Mois précédent : "
                + CurrencyUtil.formater(valeurs.revenusPrecedents(), devise));
        detailDepensesMois.setText("Mois précédent : "
                + CurrencyUtil.formater(valeurs.depensesPrecedentes(), devise));
        detailEconomiesMois.setText("Mois précédent : "
                + CurrencyUtil.formater(valeurs.economiesPrecedentes(), devise));
        detailSoldeMois.setText("Mois précédent : "
                + CurrencyUtil.formater(soldePrecedent, devise));

        // Une hausse des dépenses n'est pas une bonne nouvelle : le sens de la couleur
        // suit l'interprétation budgétaire, pas seulement le signe du pourcentage.
        afficherTendance(badgeRevenus, valeurs.revenus(), valeurs.revenusPrecedents(), true);
        afficherTendance(badgeDepenses, valeurs.depenses(), valeurs.depensesPrecedentes(), false);
        afficherTendance(badgeEconomies, valeurs.economies(), valeurs.economiesPrecedentes(), true);
        afficherTendance(badgeSolde, soldeMois, soldePrecedent, true);

        // Les deux camemberts suivent le mois choisi : sinon changer de période ne
        // déplaçait que quatre petites cartes et donnait l'impression d'une page figée.
        String libelleMois = mois.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.FRANCE) + " " + mois.getYear();
        remplirPieChart(pieRevenus,  valeurs.repartitionRevenus(),
                "Répartition des revenus · " + libelleMois);
        remplirPieChart(pieDepenses, valeurs.repartitionDepenses(),
                "Répartition des dépenses · " + libelleMois);

        // Le fondu signale visuellement que ces valeurs viennent de changer.
        for (Label label : java.util.List.of(labelRevenusMois, labelDepensesMois,
                labelEconomiesMois, labelSoldeMois, badgeRevenus, badgeDepenses,
                badgeEconomies, badgeSolde)) {
            label.setOpacity(0);
            javafx.animation.FadeTransition fondu =
                    new javafx.animation.FadeTransition(javafx.util.Duration.millis(340), label);
            fondu.setFromValue(0);
            fondu.setToValue(1);
            fondu.play();
        }
    }

    private void afficherTendance(Label badge, double actuel, double precedent, boolean hausseEstBonne) {
        badge.getStyleClass().removeAll("tendance-hausse", "tendance-baisse", "tendance-neutre");

        if (precedent == 0 && actuel == 0) {
            badge.setText("—");
            badge.getStyleClass().add("tendance-neutre");
            return;
        }

        double variation;
        if (precedent == 0) {
            variation = 100;                       // passage de rien à quelque chose
        } else {
            variation = (actuel - precedent) / Math.abs(precedent) * 100;
        }

        boolean enHausse = variation >= 0;
        badge.setText(String.format(java.util.Locale.FRANCE, "%s%.1f %%",
                enHausse ? "▲ " : "▼ ", Math.abs(variation)));
        if (Math.abs(variation) < 0.05) {
            badge.getStyleClass().add("tendance-neutre");
        } else if (enHausse == hausseEstBonne) {
            badge.getStyleClass().add("tendance-hausse");
        } else {
            badge.getStyleClass().add("tendance-baisse");
        }
    }

    /** Résultats du chargement calculés sur le thread de fond, consommés sur le thread FX. */
    private record Chargement(
            double totalRevenus, double totalDepenses, double totalEconomies,
            double solde, double tauxEpargne,
            int nbRevenus, int nbDepenses, int nbEconomies,
            List<Double> revenusMensuels, List<Double> depensesMensuelles,
            List<Double> economiesMensuelles,
            double soldeInitial, double economiesInitiales) {}

    /**
     * Exécute toutes les requêtes SQL (totaux des cartes + données des
     * graphiques) sur un thread de fond (Task) pour ne jamais bloquer le
     * JavaFX Application Thread. Labels et charts sont mis à jour via
     * setOnSucceeded(), qui s'exécute automatiquement sur l'UI thread.
     */
    /** Redemandé par la navigation quand la vue revient du cache. */
    @Override
    public void rafraichirDonnees() {
        if (utilisateurCourant == null) return;
        chargerDonnees(utilisateurCourant);
        chargerMoisSelectionne();
    }

    private void chargerDonnees(User utilisateur) {
        int idUser = utilisateur.getIdUser();
        String devise = utilisateur.getDevise();

        Task<Chargement> tache = new Task<>() {
            @Override
            protected Chargement call() {
                // Génération des récurrences échues (écriture), puis lectures.
                incomeDAO.genererRecurrences(idUser);
                expenseDAO.genererRecurrences(idUser);
                savingDAO.genererRecurrences(idUser);

                int annee = YearMonth.now().getYear();
                List<Double> revenusMensuels     = totauxAnnee(idUser, true);
                List<Double> depensesMensuelles  = totauxAnnee(idUser, false);
                List<Double> economiesMensuelles = new ArrayList<>();
                for (int mois = 1; mois <= 12; mois++) {
                    economiesMensuelles.add(savingDAO.totalParMois(idUser, YearMonth.of(annee, mois)));
                }
                double soldeInitial = incomeDAO.totalAvantAnnee(idUser, annee)
                                    - expenseDAO.totalAvantAnnee(idUser, annee)
                                    - savingDAO.totalAvantAnnee(idUser, annee);
                double economiesInitiales = savingDAO.totalAvantAnnee(idUser, annee);

                double totalRevenus   = incomeDAO.totalParUser(idUser);
                double totalDepenses  = expenseDAO.totalParUser(idUser);
                double totalEconomies = savingDAO.totalParUser(idUser);
                double solde          = totalRevenus - totalDepenses - totalEconomies;

                return new Chargement(
                        totalRevenus, totalDepenses, totalEconomies,
                        solde, BudgetUtil.tauxEpargne(totalRevenus, totalEconomies),
                        (int) incomeDAO.nombreParUser(idUser),
                        (int) expenseDAO.nombreParUser(idUser),
                        (int) savingDAO.nombreParUser(idUser),
                        revenusMensuels, depensesMensuelles, economiesMensuelles,
                        soldeInitial, economiesInitiales);
            }
        };

        tache.setOnSucceeded(evt -> {
            Chargement c = tache.getValue();

            labelTotalRevenus.setText(CurrencyUtil.formater(c.totalRevenus(),   devise));
            labelTotalDepenses.setText(CurrencyUtil.formater(c.totalDepenses(), devise));
            labelSolde.setText(CurrencyUtil.formater(c.solde(),                 devise));
            labelTotalEconomies.setText(CurrencyUtil.formater(c.totalEconomies(), devise));
            labelTauxEpargne.setText(String.format(java.util.Locale.FRANCE, "%.1f %%", c.tauxEpargne()));
            labelNombreRevenus.setText(String.valueOf(c.nbRevenus()));
            labelNombreDepenses.setText(String.valueOf(c.nbDepenses()));
            labelNombreEconomies.setText(String.valueOf(c.nbEconomies()));

            boolean negatif = c.solde() < 0;
            labelAlerteSolde.setVisible(negatif);
            labelAlerteSolde.setManaged(negatif);
            if (negatif) {
                labelAlerteSolde.setText("Attention : votre solde disponible est négatif ("
                        + CurrencyUtil.formater(c.solde(), devise)
                        + "). Vos dépenses et économies dépassent vos revenus.");
            }

            int annee = YearMonth.now().getYear();
            remplirBarChart(barRevenusMensuels,    c.revenusMensuels(),    "Revenus " + annee);
            remplirBarChart(barDepensesMensuelles, c.depensesMensuelles(), "Dépenses " + annee);
            remplirLineChartSolde(c.revenusMensuels(), c.depensesMensuelles(),
                    c.economiesMensuelles(), c.soldeInitial(), c.economiesInitiales());
        });

        tache.setOnFailed(evt -> log.error("Chargement dashboard échoué", tache.getException()));
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
