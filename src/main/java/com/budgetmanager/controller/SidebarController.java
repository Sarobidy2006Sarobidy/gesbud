package com.budgetmanager.controller;

import com.budgetmanager.Main;
import com.budgetmanager.util.AlertUtil;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;

import java.io.IOException;

/**
 * Contrôleur de la barre latérale (sidebar.fxml), incluse dans chaque écran
 * principal via &lt;fx:include&gt;. Centralise la navigation et le dessin
 * des icônes pour éviter de dupliquer ce code dans les 7 écrans.
 *
 * Chaque écran appelle {@link #setActive(String)} dans son initialize()
 * pour indiquer quel élément du menu doit être mis en surbrillance.
 */
public class SidebarController {

    @FXML private Button boutonDashboard;
    @FXML private Button boutonRevenus;
    @FXML private Button boutonDepenses;
    @FXML private Button boutonEconomies;
    @FXML private Button boutonCategories;
    @FXML private Button boutonHistorique;
    @FXML private Button boutonStatistiques;
    @FXML private Button boutonParametres;
    @FXML private Button boutonDeconnexion;

    private static final Color COULEUR_INACTIVE = Color.web("#8fb0e8");
    /** Le cercle actif a un fond blanc (.nav-circle-active) : l'icône doit donc être foncée pour rester lisible. */
    private static final Color COULEUR_ACTIVE = Color.web("#1565c0");
    private static final Color COULEUR_DECONNEXION = Color.web("#ff8a80");

    private Button[] boutonsMenu;
    private String[] clesMenu;

    @FXML
    public void initialize() {
        boutonDashboard.setGraphic(Icons.dashboard(19, COULEUR_INACTIVE));
        boutonRevenus.setGraphic(Icons.trendingUp(19, COULEUR_INACTIVE));
        boutonDepenses.setGraphic(Icons.trendingDown(19, COULEUR_INACTIVE));
        boutonEconomies.setGraphic(Icons.wallet(19, COULEUR_INACTIVE));
        boutonCategories.setGraphic(Icons.tag(19, COULEUR_INACTIVE));
        boutonHistorique.setGraphic(Icons.clock(19, COULEUR_INACTIVE));
        boutonStatistiques.setGraphic(Icons.barChart(19, COULEUR_INACTIVE));
        boutonParametres.setGraphic(Icons.settings(19, COULEUR_INACTIVE));
        boutonDeconnexion.setGraphic(Icons.logOut(18, COULEUR_DECONNEXION));

        // setTooltip (et non Tooltip.install) : l'info-bulle reste la propriété
        // du bouton, donc découvrable via getTooltip() par le nettoyage explicite
        // de ViewCache.vider() à la déconnexion — sinon sa fenêtre pop-up garde
        // le contrôleur et sa vue FXML bloqués en RAM.
        boutonDashboard.setTooltip(new Tooltip("Tableau de bord"));
        boutonRevenus.setTooltip(new Tooltip("Revenus"));
        boutonDepenses.setTooltip(new Tooltip("Dépenses"));
        boutonEconomies.setTooltip(new Tooltip("Économies"));
        boutonCategories.setTooltip(new Tooltip("Catégories"));
        boutonHistorique.setTooltip(new Tooltip("Historique"));
        boutonStatistiques.setTooltip(new Tooltip("Statistiques"));
        boutonParametres.setTooltip(new Tooltip("Paramètres"));
        boutonDeconnexion.setTooltip(new Tooltip("Déconnexion"));

        boutonsMenu = new Button[]{ boutonDashboard, boutonRevenus, boutonDepenses,
                boutonEconomies, boutonCategories, boutonHistorique, boutonStatistiques, boutonParametres };
        clesMenu = new String[]{ "dashboard", "revenus", "depenses",
                "economies", "categories", "historique", "statistiques", "parametres" };
    }

    /**
     * Met en surbrillance l'élément de menu correspondant à l'écran affiché
     * et redessine son icône en blanc (les autres restent gris doux).
     * @param cle une des valeurs : "dashboard", "revenus", "depenses",
     *            "economies", "categories", "historique", "statistiques", "parametres".
     */
    public void setActive(String cle) {
        Icons.IconFactory[] fabriques = {
                Icons::dashboard, Icons::trendingUp, Icons::trendingDown,
                Icons::wallet, Icons::tag, Icons::clock, Icons::barChart, Icons::settings
        };

        for (int i = 0; i < boutonsMenu.length; i++) {
            boolean actif = clesMenu[i].equals(cle);
            boutonsMenu[i].getStyleClass().remove("nav-circle-active");
            boutonsMenu[i].setGraphic(fabriques[i].create(19, actif ? COULEUR_ACTIVE : COULEUR_INACTIVE));
            if (actif) {
                boutonsMenu[i].getStyleClass().add("nav-circle-active");
            }
        }
    }

    @FXML public void allerVersDashboard()    { naviguer("view/dashboard.fxml"); }
    @FXML public void allerVersRevenus()      { naviguer("view/income.fxml"); }
    @FXML public void allerVersDepenses()     { naviguer("view/expense.fxml"); }
    @FXML public void allerVersEconomies()    { naviguer("view/savings.fxml"); }
    @FXML public void allerVersCategories()   { naviguer("view/category.fxml"); }
    @FXML public void allerVersHistorique()   { naviguer("view/history.fxml"); }
    @FXML public void allerVersStatistiques() { naviguer("view/statistics.fxml"); }
    @FXML public void allerVersParametres()   { naviguer("view/settings.fxml"); }

    @FXML
    public void seDeconnecter() {
        // Vider le cache des vues pour libérer les références de l'utilisateur précédent.
        com.budgetmanager.util.ViewCache.vider();
        SessionManager.deconnecter();
        naviguer("view/login.fxml");
    }

    private void naviguer(String fxml) {
        try {
            Main.changerScene(fxml, 1140, 740);
        } catch (IOException e) {
            AlertUtil.afficherErreur("Erreur", "Impossible de charger la page demandée.");
        }
    }
}
