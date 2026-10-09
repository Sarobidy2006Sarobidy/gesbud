package com.budgetmanager.util;

import javafx.scene.Parent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Cache des vues FXML de l'application principale (dashboard, revenus, etc.).
 *
 * <p>Problème résolu : sans cache, chaque clic sur la sidebar recharge le FXML,
 * recrée le contrôleur et ses DAO, et laisse l'ancienne instance en mémoire
 * avec ses listeners et références vers SessionManager — fuite mémoire garantie.
 *
 * <p>Avec ce cache :
 * <ul>
 *   <li>Chaque vue est chargée UNE SEULE FOIS lors du premier accès.</li>
 *   <li>Les navigations suivantes réutilisent l'instance existante — instantané.</li>
 *   <li>Le cache est vidé à la déconnexion pour libérer proprement la mémoire.</li>
 * </ul>
 *
 * <p>Les vues d'authentification ne sont PAS mises en cache — elles contiennent
 * des champs sensibles qu'il vaut mieux ne pas garder en mémoire.
 */
public final class ViewCache {

    /** Vues mises en cache (clé = chemin FXML relatif au package com.budgetmanager). */
    private static final Map<String, Parent> CACHE = new HashMap<>();

    /** Vues exclues du cache (auth + erreurs) — toujours rechargées fraîches. */
    private static final Set<String> EXCLUSIONS = Set.of(
            "view/login.fxml",
            "view/register.fxml",
            "view/forgot-password.fxml",
            "view/database-error.fxml"
    );

    private ViewCache() {}

    /**
     * Retourne la vue en cache pour ce chemin, ou null si absente.
     * Les vues exclues retournent toujours null (jamais en cache).
     */
    public static Parent obtenir(String fxml, Class<?> base) {
        if (EXCLUSIONS.contains(fxml)) return null;
        return CACHE.get(fxml);
    }

    /**
     * Vrai si la vue est déjà en cache et peut être réutilisée directement.
     */
    public static boolean estEnCache(String fxml) {
        return !EXCLUSIONS.contains(fxml) && CACHE.containsKey(fxml);
    }

    /**
     * Met en cache une vue chargée. Ignoré pour les vues exclues (auth).
     */
    public static void mettreEnCache(String fxml, Parent vue) {
        if (!EXCLUSIONS.contains(fxml)) {
            CACHE.put(fxml, vue);
        }
    }

    /**
     * Vide le cache à la déconnexion.
     * Libère toutes les références vers les contrôleurs, DAO et SessionManager
     * de l'utilisateur précédent — zéro fuite mémoire entre sessions.
     */
    public static void vider() {
        CACHE.clear();
    }
}
