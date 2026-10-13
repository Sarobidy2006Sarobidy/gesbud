package com.budgetmanager.util;

/**
 * Contrat des contrôleurs qui veulent être rechargés quand leur vue revient du cache.
 *
 * <p>Une vue servie par {@link ViewCache} ne repasse jamais par
 * {@code initialize()} : sans ce hook, les données affichées restent figées
 * à la première visite, alors même qu'une autre page les a modifiées entre-temps
 * (revenu ajouté depuis les Revenus, puis retour au Tableau de bord).</p>
 *
 * <p>{@code Main.changerScene} appelle {@link #rafraichirDonnees()} sur le
 * contrôleur à chaque réaffichage depuis le cache. Les vues qui ne portent que
 * des champs en cours d'édition (Paramètres) n'implémentent pas ce contrat :
 * un rechargement y effacerait la saisie de l'utilisateur.</p>
 */
public interface Rafraichissable {

    /** Relance les requêtes d'affichage, sans toucher aux champs du formulaire. */
    void rafraichirDonnees();
}
