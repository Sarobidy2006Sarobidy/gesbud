package com.budgetmanager.util;

import com.budgetmanager.dao.ExpenseDAO;
import com.budgetmanager.dao.IncomeDAO;
import com.budgetmanager.dao.SavingDAO;
import com.budgetmanager.model.User;

import java.time.LocalDate;

/**
 * Règles du budget personnel, regroupées au même endroit :
 *
 *   solde disponible = total des revenus - total des dépenses - total des économies
 *
 * Une économie est de l'argent mis de côté : elle n'est plus disponible pour
 * les dépenses du quotidien, mais elle n'est pas perdue (elle apparaît dans
 * « Économies »). Le taux d'épargne est la part des revenus mise de côté.
 */
public final class BudgetUtil {

    private BudgetUtil() {}

    public static double soldeDisponible(int idUser) {
        return new IncomeDAO().totalParUser(idUser)
                - new ExpenseDAO().totalParUser(idUser)
                - new SavingDAO().totalParUser(idUser);
    }

    /** Part des revenus mise de côté, en pourcentage (0 s'il n'y a aucun revenu). */
    public static double tauxEpargne(double revenus, double economies) {
        return revenus > 0 ? economies * 100.0 / revenus : 0;
    }

    /**
     * Avant d'enregistrer une dépense ou une économie : si son montant dépasse le
     * solde disponible, demande confirmation. Les opérations datées dans le futur
     * ne sont pas contrôlées (l'argent n'est pas encore dépensé).
     *
     * @return true si l'on peut continuer l'enregistrement
     */
    public static boolean confirmerSiDepassement(double montant, LocalDate date) {
        if (date == null || date.isAfter(LocalDate.now())) return true;
        User utilisateur = SessionManager.getUtilisateurConnecte();
        if (utilisateur == null) return true;

        double disponible = soldeDisponible(utilisateur.getIdUser());
        if (montant <= disponible) return true;

        String devise = utilisateur.getDevise();
        return AlertUtil.confirmer("Solde insuffisant",
                "Ce montant (" + CurrencyUtil.formater(montant, devise) + ") dépasse votre solde disponible ("
                        + CurrencyUtil.formater(disponible, devise) + ").\nVoulez-vous quand même l'enregistrer ?");
    }
}
