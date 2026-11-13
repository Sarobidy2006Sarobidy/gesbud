package com.budgetmanager.harnais;

import com.budgetmanager.Main;
import com.budgetmanager.config.DatabaseConnection;
import com.budgetmanager.controller.DashboardController;
import com.budgetmanager.controller.ExpenseController;
import com.budgetmanager.dao.CategoryDAO;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

/**
 * Sonde d'interface : elle navigue avec le vrai Main.changerScene, saisit une dépense
 * « Une seule fois » dans le formulaire de la page Dépenses, puis revient au tableau de
 * bord comme le fait l'utilisateur, et compare le solde affiché au solde de la base.
 */
public class SondeSoldeDepense {

    private static final String SONDE = "__sonde__";
    private static final double MONTANT = 1.0;

    private static int reussis = 0;
    private static int echecs = 0;
    private static volatile boolean alertesActives = true;
    private static Object cleControleur;

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
        }
        System.out.println("RESULTAT : " + reussis + " OK / " + echecs + " ECHEC(S)");
        Platform.exit();
        System.exit(echecs == 0 ? 0 : 1);
    }

    private static void executer() throws Exception {
        User utilisateur = premierUtilisateurAvecCategorieDepense();
        if (utilisateur == null) {
            echecs++;
            System.out.println("ECHEC : aucun utilisateur avec une catégorie DÉPENSE en base");
            return;
        }
        SessionManager.connecter(utilisateur);
        int idUser = utilisateur.getIdUser();
        String devise = utilisateur.getDevise();
        System.out.println("USER test : id " + idUser + " (" + utilisateur.getPseudo() + ") devise " + devise);

        Field cle = Main.class.getDeclaredField("CLE_CONTROLEUR");
        cle.setAccessible(true);
        cleControleur = cle.get(null);

        etape();
        VeilleurAlertes.ajouter();

        // 1 — ouverture du tableau de bord par la navigation réelle
        DashboardController tableauDeBord = (DashboardController) ouvrir("view/dashboard.fxml");
        double soldeAvant = BudgetUtil.soldeDisponible(idUser);
        String afficheAvant = texte(tableauDeBord, "labelSolde");
        verifier("solde du tableau de bord lu", afficheAvant.equals(CurrencyUtil.formater(soldeAvant, devise)),
                "affiché " + afficheAvant + " / attendu " + CurrencyUtil.formater(soldeAvant, devise));

        // 2 — page Dépenses, une dépense « Une seule fois » ajoutée par le bouton
        ExpenseController page = (ExpenseController) ouvrir("view/expense.fxml");
        Category categorie = categorieAuPlusGrandPlafond(idUser);
        int lignesAvant = nbLignes(page);
        System.out.println("AVANT : solde base = " + soldeAvant + ", lignes table = " + lignesAvant
                + ", catégorie = " + categorie.getNom() + ", plafond = " + categorie.getBudget());

        fx(() -> {
            ((ComboBox<Category>) champ(page, "comboCategorie")).setValue(categorie);
            ((DatePicker) champ(page, "champDate")).setValue(LocalDate.now());
            ((TextField) champ(page, "champMontant")).setText("1");
            ((TextField) champ(page, "champDescription")).setText(SONDE);
            page.ajouterDepense();
            return null;
        }).get();
        attendre();

        int lignesApres = nbLignes(page);
        double soldeApres = BudgetUtil.soldeDisponible(idUser);
        System.out.println("APRES : solde base = " + soldeApres + ", lignes table = " + lignesApres);
        verifier("la dépense « Une seule fois » est écrite en base", lignesApres == lignesAvant + 1,
                "lignes " + lignesAvant + " -> " + lignesApres);
        verifier("le solde calculé sur la base baisse du montant",
                Math.abs((soldeAvant - soldeApres) - MONTANT) < 0.005, "écart " + (soldeAvant - soldeApres));

        // 3 — l'utilisateur clique sur « Tableau de bord » : c'est le geste réel à tester
        tableauDeBord = (DashboardController) ouvrir("view/dashboard.fxml");
        String afficheApres = texte(tableauDeBord, "labelSolde");
        verifier("après le retour au tableau de bord, le solde affiché a baissé",
                afficheApres.equals(CurrencyUtil.formater(soldeApres, devise)),
                "affiché " + afficheApres + " / attendu " + CurrencyUtil.formater(soldeApres, devise)
                        + " (avant : " + afficheAvant + ")");

        // 4 — la page Dépenses, vue du cache, montre-t-elle la nouvelle ligne ?
        ExpenseController page2 = (ExpenseController) ouvrir("view/expense.fxml");
        verifier("la ligne est visible dans le tableau des dépenses après navigation",
                nbLignes(page2) == lignesApres, "lignes " + nbLignes(page2));

        alertesActives = false;
        nettoyer(idUser);
    }

    /** Scène porteuse : changerScene a besoin d'une fenêtre principale réelle. */
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
        return fx(() -> {
            Parent racine = ViewCache.obtenir(fxml, Main.class);
            return racine.getProperties().get(cleControleur);
        }).get();
    }

    private static int nbLignes(ExpenseController page) throws Exception {
        return fx(() -> ((ObservableList<?>) ((TableView<?>) champ(page, "tableDepenses")).getItems()).size()).get();
    }

    private static String texte(Object controleur, String champNom) throws Exception {
        return fx(() -> ((Label) champ(controleur, champNom)).getText()).get();
    }

    private static Object champ(Object cible, String nom) {
        for (Class<?> c = cible.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(nom);
                f.setAccessible(true);
                return f.get(cible);
            } catch (NoSuchFieldException e) {
                // chercher dans la classe parente
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        throw new IllegalStateException("champ introuvable : " + nom);
    }

    private static User premierUtilisateurAvecCategorieDepense() throws Exception {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT u.id_user FROM users u JOIN categories c ON c.id_user = u.id_user"
                   + " AND c.type = 'DEPENSE' GROUP BY u.id_user ORDER BY COUNT(c.id_categorie) DESC")) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return new UserDAO().trouverParId(rs.getInt(1)).orElse(null);
            }
        }
        return null;
    }

    private static Category categorieAuPlusGrandPlafond(int idUser) {
        List<Category> categories = new CategoryDAO().listerParUserEtType(idUser, CategoryType.DEPENSE);
        return categories.stream().max((a, b) -> Double.compare(budget(a), budget(b)))
                .orElseThrow(() -> new IllegalStateException("aucune catégorie DÉPENSE"));
    }

    private static double budget(Category c) {
        return c.getBudget() == null ? Double.MAX_VALUE : c.getBudget();
    }

    private static <T> Future<T> fx(Callable<T> action) {
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
        Thread.sleep(500);
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

    /** Clique « Confirmer » ou « Compris » sur toute boîte de dialogue qui surgit. */
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

    private static void nettoyer(int idUser) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM expenses WHERE id_user = ? AND description = ?")) {
            stmt.setInt(1, idUser);
            stmt.setString(2, SONDE);
            System.out.println("NETTOYAGE : " + stmt.executeUpdate() + " ligne(s) de test supprimée(s)");
        } catch (Exception e) {
            System.out.println("NETTOYAGE IMPOSSIBLE : " + e.getMessage());
        }
    }
}
