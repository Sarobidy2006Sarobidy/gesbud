package com.budgetmanager.util;

/**
 * Actions globales déclenchables au clavier, implémentées par les contrôleurs
 * des écrans concernés. {@code Main} branche Ctrl+N (ajouter) et Ctrl+S
 * (enregistrer) sur les accélérateurs de la Scene : si le contrôleur de la vue
 * affichée n'implémente pas cette interface, les raccourcis ne font rien.
 *
 * <p>Les méthodes ont un corps vide par défaut : chaque écran n'active que les
 * raccourcis qui ont du sens pour lui (un écran sans formulaire ignore Ctrl+N).</p>
 */
public interface RaccourcisClavier {

    /** Ctrl+N — créer un nouvel élément (revenu, dépense, économie, catégorie…). */
    default void auRaccourciAjouter() {}

    /** Ctrl+S — enregistrer le formulaire / les paramètres en cours. */
    default void auRaccourciEnregistrer() {}
}
