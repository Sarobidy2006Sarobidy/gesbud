package com.budgetmanager.controller;

import com.budgetmanager.Main;
import com.budgetmanager.model.User;
import com.budgetmanager.util.AlertUtil;
import com.budgetmanager.util.Hexagon;
import com.budgetmanager.util.Icons;
import com.budgetmanager.util.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.io.IOException;

/**
 * Contrôleur de la barre latérale (sidebar.fxml), incluse dans chaque écran
 * principal via &lt;fx:include&gt;. Centralise la navigation et le dessin des
 * icônes pour éviter de dupliquer ce code dans les 7 écrans.
 *
 * Chaque écran appelle {@link #setActive(String)} dans son initialize() pour
 * mettre en surbrillance l'entrée du menu correspondante.
 */
public class SidebarController {

    @FXML private StackPane zoneLogo;
    @FXML private Label labelSousTitre;

    @FXML private Button boutonDashboard;
    @FXML private Button boutonRevenus;
    @FXML private Button boutonDepenses;
    @FXML private Button boutonEconomies;
    @FXML private Button boutonCategories;
    @FXML private Button boutonHistorique;
    @FXML private Button boutonCarnet;
    @FXML private Button boutonParametres;
    @FXML private Button boutonDeconnexion;

    private static final Color ICONE_INACTIVE = Color.web("#5b7099");
    /** L'entrée active a un fond bleu très clair : l'icône prend la couleur d'action. */
    private static final Color ICONE_ACTIVE = Color.web("#1565c0");
    private static final Color ICONE_DECONNEXION = Color.web("#c62828");
    private static final double TAILLE_ICONE = 17;

    private Button[] boutonsMenu;
    private String[] clesMenu;
    private Icons.IconFactory[] fabriques;

    @FXML
    public void initialize() {
        zoneLogo.getChildren().add(Hexagon.badge(34, Icons::wallet, true));

        User utilisateur = SessionManager.getUtilisateurConnecte();
        // Accroché à la propriété devise : la modification faite dans Paramètres
        // doit apparaître ici sans redémarrer l'application.
        if (utilisateur == null) {
            labelSousTitre.setText("Gestion personnelle");
        } else {
            labelSousTitre.textProperty().bind(
                    javafx.beans.binding.Bindings.createStringBinding(
                            () -> "Devise : " + utilisateur.getDevise(),
                            utilisateur.deviseProperty()));
        }

        boutonsMenu = new Button[]{ boutonDashboard, boutonRevenus, boutonDepenses,
                boutonEconomies, boutonCategories, boutonHistorique, boutonCarnet, boutonParametres };
        clesMenu = new String[]{ "dashboard", "revenus", "depenses",
                "economies", "categories", "historique", "carnet", "parametres" };
        fabriques = new Icons.IconFactory[]{
                Icons::dashboard, Icons::trendingUp, Icons::trendingDown,
                Icons::wallet, Icons::tag, Icons::clock, Icons::dollar, Icons::settings };

        boutonDashboard.setGraphic(Icons.dashboard(TAILLE_ICONE, ICONE_INACTIVE));
        boutonRevenus.setGraphic(Icons.trendingUp(TAILLE_ICONE, ICONE_INACTIVE));
        boutonDepenses.setGraphic(Icons.trendingDown(TAILLE_ICONE, ICONE_INACTIVE));
        boutonEconomies.setGraphic(Icons.wallet(TAILLE_ICONE, ICONE_INACTIVE));
        boutonCategories.setGraphic(Icons.tag(TAILLE_ICONE, ICONE_INACTIVE));
        boutonHistorique.setGraphic(Icons.clock(TAILLE_ICONE, ICONE_INACTIVE));
        boutonCarnet.setGraphic(Icons.dollar(TAILLE_ICONE, ICONE_INACTIVE));
        boutonParametres.setGraphic(Icons.settings(TAILLE_ICONE, ICONE_INACTIVE));
        boutonDeconnexion.setGraphic(Icons.logOut(TAILLE_ICONE, ICONE_DECONNEXION));
    }

    /**
     * Met en surbrillance l'élément de menu correspondant à l'écran affiché.
     * @param cle une des valeurs : "dashboard", "revenus", "depenses",
     *            "economies", "categories", "historique", "carnet", "parametres".
     */
    public void setActive(String cle) {
        for (int i = 0; i < boutonsMenu.length; i++) {
            Button bouton = boutonsMenu[i];
            boolean actif = clesMenu[i].equals(cle);
            bouton.getStyleClass().remove("nav-item-active");
            bouton.setGraphic(fabriques[i].create(TAILLE_ICONE, actif ? ICONE_ACTIVE : ICONE_INACTIVE));
            if (actif) {
                bouton.getStyleClass().add("nav-item-active");
            }
        }
    }

    @FXML public void allerVersDashboard()  { naviguer("view/dashboard.fxml"); }
    @FXML public void allerVersRevenus()    { naviguer("view/income.fxml"); }
    @FXML public void allerVersDepenses()   { naviguer("view/expense.fxml"); }
    @FXML public void allerVersEconomies()  { naviguer("view/savings.fxml"); }
    @FXML public void allerVersCategories() { naviguer("view/category.fxml"); }
    @FXML public void allerVersHistorique() { naviguer("view/history.fxml"); }
    @FXML public void allerVersCarnet()     { naviguer("view/carnet.fxml"); }
    @FXML public void allerVersParametres() { naviguer("view/settings.fxml"); }

    @FXML
    public void seDeconnecter() {
        // Vider le cache des vues pour libérer les références de l'utilisateur précédent.
        com.budgetmanager.util.ViewCache.vider();
        SessionManager.deconnecter();
        naviguer("view/login.fxml");
    }

    private void naviguer(String fxml) {
        try {
            Main.changerScene(fxml, Main.LARGEUR_APP, Main.HAUTEUR_APP);
        } catch (IOException e) {
            AlertUtil.afficherErreur("Erreur", "Impossible de charger la page demandée.");
        }
    }
}
