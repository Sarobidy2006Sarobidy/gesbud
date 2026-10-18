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
import javafx.scene.chart.AreaChart;
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
    /** Ce qu'il reste à dépenser chaque jour du mois choisi. */
    @FXML private Label labelResteParJour;
    @FXML private Label detailResteParJour;
    /** Bandeau des plafonds mensuels dépassés, vide quand rien ne dépasse. */
    @FXML private Label labelAlertePlafonds;

    /** Graphiques de la section statistiques (fusion de l'ancienne page Statistics). */
    @FXML private javafx.scene.layout.GridPane grilleGraphiques;
    @FXML private PieChart pieRevenus;
    @FXML private PieChart pieDepenses;
    @FXML private Label videPieRevenus;
    @FXML private Label videPieDepenses;
    @FXML private javafx.scene.layout.Pane survolPieRevenus;
    @FXML private javafx.scene.layout.Pane survolPieDepenses;
    @FXML private BarChart<String, Number> barRevenusMensuels;
    @FXML private BarChart<String, Number> barDepensesMensuelles;
    @FXML private AreaChart<String, Number> lineSolde;

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
        // 5 cartes dès que chacune garde ~220 px ; en dessous, 3 par rangée pour ne
        // jamais laisser une seule carte toute seule sur sa ligne.
        com.budgetmanager.util.ResponsiveGrid.appliquer(grilleCartes,
                largeur -> largeur >= 1100 ? 5 : largeur >= 660 ? 3 : 1);
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
        preparerAnneauxEtCourbes();

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

    /**
     * Les deux parts de camembert deviennent un anneau et la courbe du solde une
     * aire dégradée : le creux, les pourcentages et le total central sont posés par
     * dessus, mesurés sur les parts réellement dessinées.
     */
    private void preparerAnneauxEtCourbes() {
        lineSolde.setCreateSymbols(false);
        for (var paire : java.util.List.of(
                java.util.Map.entry(pieRevenus, survolPieRevenus),
                java.util.Map.entry(pieDepenses, survolPieDepenses))) {
            PieChart camembert = paire.getKey();
            javafx.scene.layout.Pane survol = paire.getValue();
            camembert.setAnimated(false);
            camembert.setLabelsVisible(false);
            // Chaque fin de disposition et chaque changement de taille reposent le
            // repère de l'anneau, une fois le camembert posé sous le mesurage.
            camembert.needsLayoutProperty().addListener((obs, avant, apres) -> {
                if (!apres) habillerDonut(camembert, survol);
            });
            camembert.widthProperty().addListener((obs, avant, apres) -> habillerDonut(camembert, survol));
            camembert.heightProperty().addListener((obs, avant, apres) -> habillerDonut(camembert, survol));
        }
    }

    /** Trou blanc, pourcentage de chaque part et total du mois, d'après les parts dessinées. */
    private void habillerDonut(PieChart camembert, javafx.scene.layout.Pane survol) {
        if (habillageEnCours) return;
        habillageEnCours = true;
        try { habillerDonutSansGarde(camembert, survol); }
        finally { habillageEnCours = false; }
    }

    private boolean habillageEnCours = false;

    private void habillerDonutSansGarde(PieChart camembert, javafx.scene.layout.Pane survol) {
        double somme = camembert.getData().stream().mapToDouble(PieChart.Data::getPieValue).sum();

        javafx.scene.shape.Circle trou = null;
        javafx.scene.control.Label centre = null;
        for (javafx.scene.Node enfant : survol.getChildren()) {
            if (enfant instanceof javafx.scene.shape.Circle c) trou = c;
            if (enfant instanceof javafx.scene.control.Label l) centre = l;
        }
        // Un mois sans la moindre opération n'a rien à mesurer : l'anneau revient à plat.
        if (somme <= 0) {
            if (trou != null) trou.setRadius(0);
            if (centre != null) centre.setText("");
            survol.getChildren().removeIf(n -> n instanceof javafx.scene.text.Text);
            return;
        }

        // Un camembert ne dessine ses parts que pendant sa propre disposition, et
        // l'écouteur de disposition est appelé juste avant : on pose le graphe ici pour
        // lire l'empreinte réelle des parts, sinon l'anneau ne se forme jamais.
        camembert.applyCss();
        camembert.layout();

        double gauche = 0, haut = 0, droite = 0, bas = 0;
        for (PieChart.Data donnee : camembert.getData()) {
            javafx.geometry.Bounds b = autourDe(survol, donnee.getNode());
            if (b == null) continue;
            if (droite == 0) {
                gauche = b.getMinX(); haut = b.getMinY(); droite = b.getMaxX(); bas = b.getMaxY();
            } else {
                gauche = Math.min(gauche, b.getMinX()); haut = Math.min(haut, b.getMinY());
                droite = Math.max(droite, b.getMaxX()); bas = Math.max(bas, b.getMaxY());
            }
        }
        // Toujours pas de part mesurable (graphe hors scène, taille nulle) : on ne
        // touche à rien, la prochaine disposition reposera l'habillage.
        if (droite - gauche < 10) return;

        if (trou == null) {
            trou = new javafx.scene.shape.Circle();
            trou.getStyleClass().add("donut-trou");
            trou.setMouseTransparent(true);
            survol.getChildren().add(trou);
            // Le blanc du creux vient de la feuille de style : sans cette passe, le
            // cercle neuf garde son noir par défaut le temps d'un instant.
            trou.applyCss();
        }
        if (centre == null) {
            centre = new javafx.scene.control.Label();
            centre.getStyleClass().add("donut-centre");
            centre.setMouseTransparent(true);
            survol.getChildren().add(centre);
        }
        survol.getChildren().removeIf(n -> n instanceof javafx.scene.text.Text);

        double cx = (gauche + droite) / 2;
        double cy = (haut + bas) / 2;
        double rayon = Math.min(droite - gauche, bas - haut) / 2;

        // Le creux se rétracte sur les petits graphiques : à 0,6 R, un anneau de 50 px
        // de rayon n'offre qu'une couronne de 20 px, trop étroite pour un pourcentage
        // quel que soit son angle. En dessous de 70 px de rayon, le creux rend de la
        // place à la couleur, sans jamais descendre sous ce que peut contenir le total.
        double creux = rayon * Math.min(0.6, 0.5 + Math.max(0, rayon - 40) / 300);
        trou.setCenterX(cx);
        trou.setCenterY(cy);
        trou.setRadius(creux);

        // Le creux est petit : « 1 140 000,00 Ar » n'y tiendrait pas.
        centre.setText(CurrencyUtil.compact().toString(somme));
        for (double taille : new double[] { 0, 12, 10 }) {
            if (taille > 0) {
                centre.setStyle("-fx-font-family: \"Inter SemiBold\"; -fx-font-size: " + taille
                        + "px; -fx-text-fill: #0b2a6b;");
            }
            centre.applyCss();
            javafx.geometry.Bounds t = centre.getLayoutBounds();
            // Une diagonale qui dépasse le diamètre du creux déborderait sur la couleur.
            if (Math.hypot(t.getWidth(), t.getHeight()) <= creux * 2 || taille == 10) break;
        }
        javafx.geometry.Bounds t = centre.getLayoutBounds();
        centre.relocate(cx - t.getWidth() / 2, cy - t.getHeight() / 2);

        // Un camembert peint ses parts dans le sens horaire depuis 3 heures, mais le
        // cumul des fractions ne dit rien de l'angle réellement occupé : une part de
        // 40,9 % posée à 0° finit à -147°, pas à +147°. C'est l'Arc de la part qui
        // porte la vérité, et lui seul, sinon le pourcentage atterrit chez son voisin.
        for (PieChart.Data donnee : camembert.getData()) {
            javafx.scene.shape.Arc forme = formeDePart(donnee.getNode());
            if (forme == null) continue;
            double milieu = Math.toRadians(forme.getStartAngle() + forme.getLength() / 2);
            double dx = Math.cos(milieu);
            double dy = -Math.sin(milieu);
            javafx.scene.text.Text pourcentage = new javafx.scene.text.Text(
                    Math.round(donnee.getPieValue() / somme * 100) + " %");
            pourcentage.getStyleClass().add("donut-pourcent");
            pourcentage.setMouseTransparent(true);
            // Dans le graphe d'abord, la feuille de style ensuite : hors scène, un Text
            // ignore sa classe et resterait noir sur une part déjà sombre.
            survol.getChildren().add(pourcentage);
            // Une part trop étroite pour son texte n'a pas de pourcentage : la légende
            // garde son nom et son montant, et l'anneau ne se couvre pas de miettes.
            if (!poserDansLaPart(pourcentage, forme, donnee.getNode(), survol, cx, cy, rayon, creux, dx, dy)) {
                survol.getChildren().remove(pourcentage);
            }
        }
    }

    /** Échelons de police essayés quand la taille de la feuille ne tient pas dans la couronne. */
    private static final double[] TAILLES_POURCENT = { 0, 10, 9, 8 };

    /** Décalages essayés depuis le cœur de la couronne, du plus proche au plus lointain. */
    private static final double[] PAS_POURCENT = { 0, -2, 2, -4, 4, -6, 6 };

    /**
     * Pose un pourcentage sur la bissectrice de sa part, au cœur de sa couronne, en
     * décalant son rayon puis en rétrécissant sa police tant que ses quatre coins ne
     * tiennent pas dans la couleur de cette part.
     */
    private static boolean poserDansLaPart(javafx.scene.text.Text pourcentage,
                                            javafx.scene.shape.Arc forme, javafx.scene.Node part,
                                            javafx.scene.layout.Pane survol,
                                            double cx, double cy, double rayon, double creux,
                                            double dx, double dy) {
        double coeur = (creux + rayon) / 2;
        for (double taille : TAILLES_POURCENT) {
            if (taille > 0) {
                pourcentage.setStyle("-fx-font-family: \"Inter SemiBold\"; -fx-font-size: "
                        + taille + "px; -fx-fill: white;");
            }
            pourcentage.applyCss();
            for (double pas : PAS_POURCENT) {
                javafx.geometry.Bounds libelle = pourcentage.getBoundsInParent();
                double distance = coeur + pas;
                pourcentage.relocate(cx + dx * distance - libelle.getWidth() / 2,
                                     cy + dy * distance - libelle.getHeight() / 2);
                libelle = pourcentage.getBoundsInParent();
                if (tientDansLaPart(forme, part, survol, libelle, cx, cy, rayon, creux)) return true;
            }
        }
        return false;
    }

    /** Les quatre coins du libellé sont-ils dans la couronne et dans le secteur de sa part ? */
    private static boolean tientDansLaPart(javafx.scene.shape.Arc forme, javafx.scene.Node part,
                                           javafx.scene.layout.Pane survol, javafx.geometry.Bounds b,
                                           double cx, double cy, double rayon, double creux) {
        double[][] coins = { { b.getMinX(), b.getMinY() }, { b.getMaxX(), b.getMinY() },
                             { b.getMaxX(), b.getMaxY() }, { b.getMinX(), b.getMaxY() } };
        for (double[] coin : coins) {
            double depuisLeCentre = Math.hypot(coin[0] - cx, coin[1] - cy);
            if (depuisLeCentre < creux || depuisLeCentre > rayon) return false;
            javafx.geometry.Point2D local = part.sceneToLocal(survol.localToScene(coin[0], coin[1]));
            if (!forme.contains(local.getX(), local.getY())) return false;
        }
        return true;
    }

    /** La part d'un camembert est une Region habillée d'un Arc : c'est lui qui tourne. */
    private static javafx.scene.shape.Arc formeDePart(javafx.scene.Node part) {
        return part instanceof javafx.scene.layout.Region region
                && region.getShape() instanceof javafx.scene.shape.Arc arc ? arc : null;
    }

    /** L'empreinte d'une part dans le repère du panneau posé sur le camembert. */
    private static javafx.geometry.Bounds autourDe(javafx.scene.layout.Pane survol, javafx.scene.Node part) {
        if (part == null || part.getBoundsInLocal().getWidth() < 1) return null;
        return survol.sceneToLocal(part.localToScene(part.getBoundsInLocal()));
    }

    /** Résultats du calcul mensuel : valeurs du mois M et précédent + répartition du mois. */
    private record Mois(double revenus, double revenusPrecedents,
                        double depenses, double depensesPrecedentes,
                        double economies, double economiesPrecedentes,
                        List<Object[]> repartitionRevenus,
                        List<Object[]> repartitionDepenses,
                        List<Object[]> plafondsDepasses) {}

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
                        expenseDAO.repartitionParCategorie(idUser, mois),
                        expenseDAO.plafondsDepasses(idUser, mois));
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

        afficherResteParJour(mois, soldeMois, devise);
        afficherPlafondsDepasses(valeurs.plafondsDepasses(), mois, devise);

        // Les deux camemberts suivent le mois choisi : sinon changer de période ne
        // déplaçait que quatre petites cartes et donnait l'impression d'une page figée.
        String libelleMois = mois.getMonth().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.FRANCE) + " " + mois.getYear();
        remplirPieChart(pieRevenus, videPieRevenus, survolPieRevenus, valeurs.repartitionRevenus(),
                "Répartition des revenus · " + libelleMois,
                "Aucun revenu enregistré en " + libelleMois);
        remplirPieChart(pieDepenses, videPieDepenses, survolPieDepenses, valeurs.repartitionDepenses(),
                "Répartition des dépenses · " + libelleMois,
                "Aucune dépense enregistrée en " + libelleMois);

        // Le fondu signale visuellement que ces valeurs viennent de changer.
        for (Label label : java.util.List.of(labelRevenusMois, labelDepensesMois,
                labelEconomiesMois, labelSoldeMois, labelResteParJour,
                badgeRevenus, badgeDepenses,
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

    /**
     * « Reste par jour » : la question du quotidien, celle qu'aucun total ne pose.
     * Le mois courant la répartit sur les jours encore à courir (aujourd'hui compris),
     * un mois clos sur ses jours entiers, et un mois futur n'a encore rien à répartir.
     */
    private void afficherResteParJour(YearMonth mois, double soldeMois, String devise) {
        YearMonth courant = YearMonth.now();
        if (mois.isAfter(courant)) {
            labelResteParJour.setText("—");
            detailResteParJour.setText("Le mois n'est pas encore commencé");
            return;
        }

        int jours = mois.equals(courant)
                ? mois.lengthOfMonth() - java.time.LocalDate.now().getDayOfMonth() + 1
                : mois.lengthOfMonth();
        labelResteParJour.setText(CurrencyUtil.formater(soldeMois / jours, devise));

        if (mois.equals(courant) && soldeMois <= 0) {
            detailResteParJour.setText("Le mois est déjà à découvert : rien de plus à dépenser");
        } else if (mois.equals(courant)) {
            detailResteParJour.setText("Réparti sur " + jours + (jours > 1 ? " jours restants" : " jour restant"));
        } else {
            detailResteParJour.setText("Solde du mois réparti sur ses " + jours + " jours");
        }
    }

    /**
     * Bandeau des plafonds dépassés sur le mois choisi. Vide quand aucune catégorie
     * ne dépasse sa limite, donc la page ne dit rien quand tout va bien.
     */
    private void afficherPlafondsDepasses(List<Object[]> depassements, YearMonth mois, String devise) {
        if (depassements.isEmpty()) {
            labelAlertePlafonds.setVisible(false);
            labelAlertePlafonds.setManaged(false);
            return;
        }

        StringBuilder texte = new StringBuilder("Plafond dépassé en ")
                .append(mois.getMonth().getDisplayName(
                        java.time.format.TextStyle.FULL, java.util.Locale.FRANCE))
                .append(" ").append(mois.getYear()).append(" : ");
        for (int i = 0; i < depassements.size(); i++) {
            Object[] ligne = depassements.get(i);
            if (i > 0) texte.append("  ·  ");
            texte.append((String) ligne[0]).append(" ")
                    .append(CurrencyUtil.formater((Double) ligne[1], devise))
                    .append(" au lieu de ")
                    .append(CurrencyUtil.formater((Double) ligne[2], devise));
        }
        labelAlertePlafonds.setText(texte.toString());
        labelAlertePlafonds.setVisible(true);
        labelAlertePlafonds.setManaged(true);
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

    private void remplirPieChart(PieChart chart, Label vide, javafx.scene.layout.Pane survol,
                                 List<Object[]> donnees,
                                 String titre, String messageVide) {
        chart.setTitle(titre);
        // Un camembert sans part n'affiche qu'un cercle vide, difficile à
        // distinguer d'un graphique cassé : le message nomme le mois concerné.
        vide.setText(messageVide);
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
        vide.setVisible(nouvellesDonnees.isEmpty());
        // Une passe de layout plus loin, les parts ont leur taille définitive : le
        // creux et les pourcentages se posent alors exactement dessus.
        javafx.application.Platform.runLater(() -> habillerDonut(chart, survol));
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
        int annee = YearMonth.now().getYear();
        lineSolde.setTitle("Solde disponible et économies " + annee);

        // Les mois écoulés sont les seuls à avoir un solde : tracer les mois à venir à plat
        // se lirait comme une prévision, alors que c'est simplement l'absence de données.
        int derniersMois = YearMonth.now().getMonthValue();

        List<Double> soldes = new ArrayList<>();
        List<Double> economiesCumulees = new ArrayList<>();
        double solde = soldeInitial;
        double cumul = economiesInitiales;
        for (int i = 0; i < 12; i++) {
            solde += revenusMensuels.get(i) - depensesMensuelles.get(i) - economiesMensuelles.get(i);
            cumul += economiesMensuelles.get(i);
            if (i < derniersMois) {
                soldes.add(solde);
                economiesCumulees.add(cumul);
            }
        }

        if (lineSolde.getData().size() < 2) {
            lineSolde.getData().clear();
            XYChart.Series<String, Number> serieSolde = new XYChart.Series<>();
            serieSolde.setName("Solde disponible");
            XYChart.Series<String, Number> serieEconomies = new XYChart.Series<>();
            serieEconomies.setName("Économies cumulées");
            lineSolde.getData().add(serieSolde);
            lineSolde.getData().add(serieEconomies);
        }
        // Recharger les valeurs en place quand le nombre de points ne change pas :
        // reconstruire la série déclenche un recalcul visuel complet, donc un clignotement.
        poserPoints(lineSolde.getData().get(0), soldes);
        poserPoints(lineSolde.getData().get(1), economiesCumulees);
    }

    private static void poserPoints(XYChart.Series<String, Number> serie, List<Double> valeurs) {
        if (serie.getData().size() != valeurs.size()) {
            serie.getData().clear();
            for (int i = 0; i < valeurs.size(); i++) {
                serie.getData().add(new XYChart.Data<>(MOIS_LABELS[i], valeurs.get(i)));
            }
        } else {
            for (int i = 0; i < valeurs.size(); i++) {
                serie.getData().get(i).setYValue(valeurs.get(i));
            }
        }
    }
}
