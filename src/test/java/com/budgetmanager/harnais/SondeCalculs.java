package com.budgetmanager.harnais;

import com.budgetmanager.Main;
import com.budgetmanager.config.DatabaseConnection;
import com.budgetmanager.controller.DashboardController;
import com.budgetmanager.controller.ExpenseController;
import com.budgetmanager.controller.IncomeController;
import com.budgetmanager.controller.SavingsController;
import com.budgetmanager.dao.CategoryDAO;
import com.budgetmanager.dao.ExpenseDAO;
import com.budgetmanager.dao.IncomeDAO;
import com.budgetmanager.dao.SavingDAO;
import com.budgetmanager.dao.UserDAO;
import com.budgetmanager.model.Category;
import com.budgetmanager.model.CategoryType;
import com.budgetmanager.model.User;
import com.budgetmanager.util.BudgetUtil;
import com.budgetmanager.util.CurrencyUtil;
import com.budgetmanager.util.DbExecutor;
import com.budgetmanager.util.SessionManager;
import com.budgetmanager.util.ViewCache;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * Sonde de calculs sur la base réelle : chaque nombre affiché (totaux, solde, taux
 * d'épargne, cartes du mois, reste par jour) est comparé à une somme SQL indépendante,
 * puis les trois écritures (revenu, dépense, économie) sont testées dans l'interface.
 */
public class SondeCalculs {

    private static final String SONDE = "__sonde_calc__";
    private static int reussis = 0;
    private static int echecs = 0;
    private static volatile boolean alertesActives = true;
    private static Object cleControleur;
    private static String devise;

    public static void main(String[] args) throws Exception {
        CountDownLatch toolkit = new CountDownLatch(1);
        Platform.startup(toolkit::countDown);
        toolkit.await();
        try {
            executer();
        } catch (Exception e) {
            echecs++;
            System.out.println("ECHEC : exception " + e);
            e.printStackTrace();
        } finally {
            alertesActives = false;
            nettoyer();
        }
        System.out.println("RESULTAT : " + reussis + " OK / " + echecs + " ECHEC(S)");
        Platform.exit();
        System.exit(echecs == 0 ? 0 : 1);
    }

    private static void executer() throws Exception {
        User utilisateur = new UserDAO().trouverParId(2).orElse(null);
        if (utilisateur == null) {
            echecs++;
            System.out.println("ECHEC : utilisateur 2 absent de la base réelle");
            return;
        }
        SessionManager.connecter(utilisateur);
        int idUser = utilisateur.getIdUser();
        devise = utilisateur.getDevise();
        System.out.println("BASE REELLE : utilisateur " + idUser + " (" + utilisateur.getPseudo() + ") devise " + devise);

        Field cle = Main.class.getDeclaredField("CLE_CONTROLEUR");
        cle.setAccessible(true);
        cleControleur = cle.get(null);
        etape();
        VeilleurAlertes.ajouter();

        // ---- 1. Les totaux du code comparent à la base SQL nue ----
        double sqlRevenus = sqlDouble("SELECT COALESCE(SUM(montant),0) FROM incomes WHERE id_user=" + idUser
                + " AND date_revenu<=CURDATE()");
        double sqlDepenses = sqlDouble("SELECT COALESCE(SUM(montant),0) FROM expenses WHERE id_user=" + idUser
                + " AND date_depense<=CURDATE()");
        double sqlEconomies = sqlDouble("SELECT COALESCE(SUM(montant),0) FROM savings WHERE id_user=" + idUser
                + " AND date_economie<=CURDATE()");
        verifier("IncomeDAO.totalParUser = somme SQL", egal(new IncomeDAO().totalParUser(idUser), sqlRevenus),
                new IncomeDAO().totalParUser(idUser) + " vs " + sqlRevenus);
        verifier("ExpenseDAO.totalParUser = somme SQL", egal(new ExpenseDAO().totalParUser(idUser), sqlDepenses),
                new ExpenseDAO().totalParUser(idUser) + " vs " + sqlDepenses);
        verifier("SavingDAO.totalParUser = somme SQL", egal(new SavingDAO().totalParUser(idUser), sqlEconomies),
                new SavingDAO().totalParUser(idUser) + " vs " + sqlEconomies);

        double soldeAttendu = sqlRevenus - sqlDepenses - sqlEconomies;
        verifier("BudgetUtil.soldeDisponible = revenus - dépenses - économies",
                egal(BudgetUtil.soldeDisponible(idUser), soldeAttendu),
                BudgetUtil.soldeDisponible(idUser) + " vs " + soldeAttendu);

        // ---- 2. Le tableau de bord affiche ces mêmes nombres ----
        DashboardController tableauDeBord = (DashboardController) ouvrir("view/dashboard.fxml");
        verifier("carte Revenus", egal(texte(tableauDeBord, "labelTotalRevenus"), forme(sqlRevenus)),
                texte(tableauDeBord, "labelTotalRevenus") + " vs " + forme(sqlRevenus));
        verifier("carte Dépenses", egal(texte(tableauDeBord, "labelTotalDepenses"), forme(sqlDepenses)),
                texte(tableauDeBord, "labelTotalDepenses") + " vs " + forme(sqlDepenses));
        verifier("carte Économies", egal(texte(tableauDeBord, "labelTotalEconomies"), forme(sqlEconomies)),
                texte(tableauDeBord, "labelTotalEconomies") + " vs " + forme(sqlEconomies));
        verifier("bandeau Solde", egal(texte(tableauDeBord, "labelSolde"), forme(soldeAttendu)),
                texte(tableauDeBord, "labelSolde") + " vs " + forme(soldeAttendu));
        verifier("compteurs de lignes", nb(texte(tableauDeBord, "labelNombreDepenses"))
                        == sqlInt("SELECT COUNT(*) FROM expenses WHERE id_user=" + idUser + " AND date_depense<=CURDATE()"),
                texte(tableauDeBord, "labelNombreDepenses"));
        String tauxAttendu = String.format(java.util.Locale.FRANCE, "%.1f %%",
                sqlRevenus > 0 ? sqlEconomies * 100.0 / sqlRevenus : 0);
        verifier("taux d'épargne = économies / revenus", egal(texte(tableauDeBord, "labelTauxEpargne"), tauxAttendu),
                texte(tableauDeBord, "labelTauxEpargne") + " vs " + tauxAttendu);

        // ---- 3. Les cartes du mois courant ----
        YearMonth mois = YearMonth.now();
        double sqlMoisRev = sqlDouble("SELECT COALESCE(SUM(montant),0) FROM incomes WHERE id_user=" + idUser
                + " AND date_revenu>='" + mois.atDay(1) + "' AND date_revenu<='" + mois.atEndOfMonth() + "' AND date_revenu<=CURDATE()");
        double sqlMoisDep = sqlDouble("SELECT COALESCE(SUM(montant),0) FROM expenses WHERE id_user=" + idUser
                + " AND date_depense>='" + mois.atDay(1) + "' AND date_depense<='" + mois.atEndOfMonth() + "' AND date_depense<=CURDATE()");
        double sqlMoisEco = sqlDouble("SELECT COALESCE(SUM(montant),0) FROM savings WHERE id_user=" + idUser
                + " AND date_economie>='" + mois.atDay(1) + "' AND date_economie<='" + mois.atEndOfMonth() + "' AND date_economie<=CURDATE()");
        double soldeMois = sqlMoisRev - sqlMoisDep - sqlMoisEco;
        verifier("carte du mois : revenus", egal(texte(tableauDeBord, "labelRevenusMois"), forme(sqlMoisRev)),
                texte(tableauDeBord, "labelRevenusMois") + " vs " + forme(sqlMoisRev));
        verifier("carte du mois : dépenses", egal(texte(tableauDeBord, "labelDepensesMois"), forme(sqlMoisDep)),
                texte(tableauDeBord, "labelDepensesMois") + " vs " + forme(sqlMoisDep));
        verifier("carte du mois : économies", egal(texte(tableauDeBord, "labelEconomiesMois"), forme(sqlMoisEco)),
                texte(tableauDeBord, "labelEconomiesMois") + " vs " + forme(sqlMoisEco));
        verifier("carte du mois : solde = revenus - dépenses - économies",
                egal(texte(tableauDeBord, "labelSoldeMois"), forme(soldeMois)),
                texte(tableauDeBord, "labelSoldeMois") + " vs " + forme(soldeMois));

        int jours = mois.lengthOfMonth() - LocalDate.now().getDayOfMonth() + 1;
        String resteAttendu = forme(soldeMois <= 0.004 ? 0 : soldeMois / jours);
        verifier("reste par jour = solde du mois / jours restants (" + jours + ")",
                egal(texte(tableauDeBord, "labelResteParJour"), resteAttendu),
                texte(tableauDeBord, "labelResteParJour") + " vs " + resteAttendu);

        // ---- 4. Les trois écritures, dans la vraie interface ----
        double soldeAvant = BudgetUtil.soldeDisponible(idUser);

        IncomeController pageRevenu = (IncomeController) ouvrir("view/income.fxml");
        Category categorieRevenu = categorie(idUser, CategoryType.REVENU);
        ecrit(pageRevenu, categorieRevenu, "1000");
        fx(() -> { pageRevenu.ajouterRevenu(); return null; }).get();
        attendre();
        double soldeApresRevenu = BudgetUtil.soldeDisponible(idUser);
        verifier("un revenu de 1000 Augmente le solde de 1000", egal(soldeApresRevenu, soldeAvant + 1000),
                soldeAvant + " -> " + soldeApresRevenu);

        final ExpenseController pageDepense = (ExpenseController) ouvrir("view/expense.fxml");
        Category categorieDepense = categorie(idUser, CategoryType.DEPENSE);
        ecrit(pageDepense, categorieDepense, "500");
        fx(() -> { pageDepense.ajouterDepense(); return null; }).get();
        attendre();
        double soldeApresDepense = BudgetUtil.soldeDisponible(idUser);
        verifier("une dépense de 500 baisse le solde de 500", egal(soldeApresDepense, soldeApresRevenu - 500),
                soldeApresRevenu + " -> " + soldeApresDepense);

        SavingsController pageEconomie = (SavingsController) ouvrir("view/savings.fxml");
        fx(() -> {
            ((TextField) champ(pageEconomie, "champMontant")).setText("200");
            ((TextField) champ(pageEconomie, "champDescription")).setText(SONDE);
            ((DatePicker) champ(pageEconomie, "champDate")).setValue(LocalDate.now());
            pageEconomie.ajouterEconomie();
            return null;
        }).get();
        attendre();
        double soldeApresEconomie = BudgetUtil.soldeDisponible(idUser);
        verifier("une économie de 200 baisse le solde de 200", egal(soldeApresEconomie, soldeApresDepense - 200),
                soldeApresDepense + " -> " + soldeApresEconomie);

        // et le tableau de bord, atteint par la navigation réelle, montre le solde final
        tableauDeBord = (DashboardController) ouvrir("view/dashboard.fxml");
        verifier("solde affiché après les trois écritures",
                egal(texte(tableauDeBord, "labelSolde"), forme(soldeApresEconomie)),
                texte(tableauDeBord, "labelSolde") + " vs " + forme(soldeApresEconomie));

        // ---- 5. Règle « prévue » : une dépense de demain ne touche pas le solde ----
        double soldeAvantFuture = BudgetUtil.soldeDisponible(idUser);
        final ExpenseController pageFinale = (ExpenseController) ouvrir("view/expense.fxml");
        fx(() -> {
            ((ComboBox<Category>) champ(pageFinale, "comboCategorie")).setValue(categorieDepense);
            ((DatePicker) champ(pageFinale, "champDate")).setValue(LocalDate.now().plusDays(1));
            ((TextField) champ(pageFinale, "champMontant")).setText("700");
            ((TextField) champ(pageFinale, "champDescription")).setText(SONDE);
            pageFinale.ajouterDepense();
            return null;
        }).get();
        attendre();
        verifier("une dépense datée de demain laisse le solde inchangé (elle est prévue)",
                egal(BudgetUtil.soldeDisponible(idUser), soldeAvantFuture),
                soldeAvantFuture + " -> " + BudgetUtil.soldeDisponible(idUser));
        verifier("mais elle est bien enregistrée",
                sqlInt("SELECT COUNT(*) FROM expenses WHERE id_user=" + idUser + " AND description='" + SONDE + "'") == 2,
                "2 attendues (500 aujourd'hui + 700 demain)");

        alertesActives = false;
        nettoyer();
    }

    private static void ecrit(Object controleur, Category categorie, String montant) throws Exception {
        fx(() -> {
            ((ComboBox<Category>) champ(controleur, "comboCategorie")).setValue(categorie);
            ((DatePicker) champ(controleur, "champDate")).setValue(LocalDate.now());
            ((TextField) champ(controleur, "champMontant")).setText(montant);
            ((TextField) champ(controleur, "champDescription")).setText(SONDE);
            return null;
        }).get();
    }

    private static Category categorie(int idUser, CategoryType type) {
        List<Category> categories = new CategoryDAO().listerParUserEtType(idUser, type);
        if (categories.isEmpty()) throw new IllegalStateException("aucune catégorie " + type + " pour " + idUser);
        return categories.get(0);
    }

    private static void etape() throws Exception {
        fx(() -> {
            Field champ = Main.class.getDeclaredField("stagePrincipal");
            champ.setAccessible(true);
            Stage etape = new Stage();
            etape.setScene(new Scene(new javafx.scene.layout.Pane(), 1220, 800));
            etape.show();
            champ.set(null, etape);
            return null;
        }).get();
    }

    private static Object ouvrir(String fxml) throws Exception {
        fx(() -> {
            Main.changerScene(fxml, 1220, 800);
            return null;
        }).get();
        attendre();
        return fx(() -> ViewCache.obtenir(fxml, Main.class).getProperties().get(cleControleur)).get();
    }

    private static String texte(Object controleur, String nomChamp) throws Exception {
        return fx(() -> ((Label) champ(controleur, nomChamp)).getText()).get();
    }

    private static Object champ(Object cible, String nom) {
        for (Class<?> c = cible.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(nom);
                f.setAccessible(true);
                return f.get(cible);
            } catch (NoSuchFieldException e) {
                // classe parente
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        throw new IllegalStateException("champ introuvable : " + nom);
    }

    private static <T> CompletableFuture<T> fx(Callable<T> action) {
        CompletableFuture<T> futur = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                futur.complete(action.call());
            } catch (Throwable t) {
                futur.completeExceptionally(t);
            }
        });
        return futur;
    }

    private static void attendre() throws Exception {
        for (int i = 0; i < 300 && DbExecutor.aTachesActives(); i++) Thread.sleep(50);
        Thread.sleep(400);
    }

    // ---- lectures SQL indépendantes ----
    private static double sqlDouble(String requete) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(requete);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    private static int sqlInt(String requete) throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(requete);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String forme(double montant) {
        return CurrencyUtil.formater(montant, devise);
    }

    private static int nb(String texte) {
        try {
            return Integer.parseInt(texte.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean egal(double a, double b) {
        return Math.abs(a - b) < 0.005;
    }

    private static boolean egal(String a, String b) {
        return a != null && a.equals(b);
    }

    private static void verifier(String quoi, boolean ok, String detail) {
        if (ok) {
            reussis++;
            System.out.println("OK    : " + quoi);
        } else {
            echecs++;
            System.out.println("ECHEC : " + quoi + "  [" + detail + "]");
        }
    }

    static class VeilleurAlertes {
        static void ajouter() {
            Thread veilleur = new Thread(() -> {
                while (alertesActives) {
                    Platform.runLater(() -> {
                        for (Window fenetre : Window.getWindows()) {
                            Scene scene = fenetre.getScene();
                            if (scene == null || scene.getRoot() == null) continue;
                            for (Node racine : scene.getRoot().lookupAll(".dialog-pane")) {
                                for (Node bouton : racine.lookupAll(".button")) {
                                    if (bouton instanceof Button b
                                            && ("Confirmer".equals(b.getText()) || "Compris".equals(b.getText()))) {
                                        b.fire();
                                    }
                                }
                            }
                        }
                    });
                    try {
                        Thread.sleep(120);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }, "click-alertes");
            veilleur.setDaemon(true);
            veilleur.start();
        }
    }

    private static void nettoyer() {
        String[] tables = {"incomes", "expenses", "savings"};
        for (String table : tables) {
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM " + table + " WHERE description = ?")) {
                stmt.setString(1, SONDE);
                int n = stmt.executeUpdate();
                if (n > 0) System.out.println("NETTOYAGE " + table + " : " + n + " ligne(s) retirée(s)");
            } catch (Exception e) {
                System.out.println("NETTOYAGE IMPOSSIBLE " + table + " : " + e.getMessage());
            }
        }
    }
}
