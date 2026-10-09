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
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    @FXML private Label labelBienvenue;
    @FXML private Label labelDevise;
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

    /** Contrôleur de la barre latérale incluse (fx:id="sidebar" -> champ "sidebarController"). */
    @FXML private SidebarController sidebarController;

    private final IncomeDAO incomeDAO = new IncomeDAO();
    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final SavingDAO savingDAO = new SavingDAO();

    @FXML
    public void initialize() {
        sidebarController.setActive("dashboard");
        com.budgetmanager.util.ResponsiveGrid.appliquer(grilleCartes,
                largeur -> largeur >= 1300 ? 5 : largeur >= 880 ? 4 : largeur >= 600 ? 3 : 2);

        User utilisateur = SessionManager.getUtilisateurConnecte();
        if (utilisateur == null) return;

        // Affichage immédiat des informations statiques (pas de DB) pendant le chargement.
        labelBienvenue.setText("Bienvenue, " + utilisateur.getNomComplet());
        labelDevise.setText("Devise : " + utilisateur.getDevise());

        chargerDonnees(utilisateur);
    }

    /**
     * Exécute toutes les requêtes SQL sur un thread de fond (Task) pour ne jamais
     * bloquer le JavaFX Application Thread. Les labels sont mis à jour via
     * setOnSucceeded(), qui s'exécute automatiquement sur l'UI thread.
     */
    private void chargerDonnees(User utilisateur) {
        int idUser = utilisateur.getIdUser();
        String devise = utilisateur.getDevise();

        Task<double[]> tache = new Task<>() {
            @Override
            protected double[] call() {
                // Génération des récurrences échues (écriture), puis lecture des totaux.
                incomeDAO.genererRecurrences(idUser);
                expenseDAO.genererRecurrences(idUser);
                savingDAO.genererRecurrences(idUser);

                double totalRevenus   = incomeDAO.totalParUser(idUser);
                double totalDepenses  = expenseDAO.totalParUser(idUser);
                double totalEconomies = savingDAO.totalParUser(idUser);
                double solde          = totalRevenus - totalDepenses - totalEconomies;
                double tauxEpargne    = BudgetUtil.tauxEpargne(totalRevenus, totalEconomies);
                double nbRevenus      = incomeDAO.nombreParUser(idUser);
                double nbDepenses     = expenseDAO.nombreParUser(idUser);
                double nbEconomies    = savingDAO.nombreParUser(idUser);
                return new double[]{ totalRevenus, totalDepenses, totalEconomies,
                        solde, tauxEpargne, nbRevenus, nbDepenses, nbEconomies };
            }
        };

        tache.setOnSucceeded(evt -> {
            double[] v = tache.getValue();
            double totalRevenus   = v[0];
            double totalDepenses  = v[1];
            double totalEconomies = v[2];
            double solde          = v[3];
            double tauxEpargne    = v[4];

            labelTotalRevenus.setText(CurrencyUtil.formater(totalRevenus,   devise));
            labelTotalDepenses.setText(CurrencyUtil.formater(totalDepenses, devise));
            labelSolde.setText(CurrencyUtil.formater(solde,                 devise));
            labelTotalEconomies.setText(CurrencyUtil.formater(totalEconomies, devise));
            labelTauxEpargne.setText(String.format(java.util.Locale.FRANCE, "%.1f %%", tauxEpargne));
            labelNombreEconomies.setText(String.valueOf((int) v[7]));
            labelNombreRevenus.setText(String.valueOf((int) v[5]));
            labelNombreDepenses.setText(String.valueOf((int) v[6]));

            boolean negatif = solde < 0;
            labelAlerteSolde.setVisible(negatif);
            labelAlerteSolde.setManaged(negatif);
            if (negatif) {
                labelAlerteSolde.setText("Attention : votre solde disponible est négatif ("
                        + CurrencyUtil.formater(solde, devise)
                        + "). Vos dépenses et économies dépassent vos revenus.");
            }
        });

        tache.setOnFailed(evt -> log.error("Chargement dashboard échoué", tache.getException()));
        com.budgetmanager.util.DbExecutor.soumettre(tache);
    }
}
