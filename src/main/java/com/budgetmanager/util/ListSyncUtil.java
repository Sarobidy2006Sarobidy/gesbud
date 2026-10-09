package com.budgetmanager.util;

import javafx.collections.ObservableList;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Synchronisation intelligente d'une ObservableList avec de nouvelles données.
 *
 * <p>Au lieu de faire {@code donnees.setAll(nouvelleListe)} qui :
 * <ul>
 *   <li>Détruit tous les objets existants (GC pressure)</li>
 *   <li>Force JavaFX à recalculer toutes les cellules du tableau</li>
 *   <li>Provoque des micro-saccades visibles sur les grandes listes</li>
 * </ul>
 *
 * <p>Cette classe fait une synchronisation chirurgicale :
 * <ul>
 *   <li>Éléments communs → mise à jour en place (zéro objet recréé)</li>
 *   <li>Éléments manquants → ajout</li>
 *   <li>Éléments en trop → suppression</li>
 * </ul>
 *
 * <p>Résultat : JavaFX ne redessine que les cellules réellement modifiées.
 *
 * <p>Utilisation :
 * <pre>
 *   ListSyncUtil.synchroniser(donnees, nouvellesDepenses,
 *       Expense::getIdDepense,
 *       (existante, nouvelle) -> {
 *           existante.setMontant(nouvelle.getMontant());
 *           existante.setDescription(nouvelle.getDescription());
 *       });
 * </pre>
 */
public final class ListSyncUtil {

    private ListSyncUtil() {}

    /**
     * Synchronise {@code liste} avec {@code nouvelles} de façon minimale.
     *
     * @param liste      La liste observable liée à la TableView
     * @param nouvelles  Les données fraîches venues de la DB
     * @param idExtract  Fonction qui extrait l'identifiant unique d'un élément
     * @param mettreAJour Fonction qui copie les champs de {@code nouvelle} dans {@code existante}
     * @param <T>        Type des éléments
     * @param <ID>       Type de l'identifiant (Integer, Long, String, etc.)
     */
    public static <T, ID> void synchroniser(
            ObservableList<T> liste,
            List<T> nouvelles,
            Function<T, ID> idExtract,
            BiConsumer<T, T> mettreAJour) {

        // Cas trivial : liste vide → setAll est optimal
        if (liste.isEmpty()) {
            liste.setAll(nouvelles);
            return;
        }

        // Construire un index des éléments existants par ID
        java.util.Map<ID, T> index = new java.util.LinkedHashMap<>();
        for (T item : liste) {
            index.put(idExtract.apply(item), item);
        }

        // Construire la nouvelle liste en réutilisant les instances existantes
        java.util.List<T> resultat = new java.util.ArrayList<>(nouvelles.size());
        for (T nouveau : nouvelles) {
            ID id = idExtract.apply(nouveau);
            T existant = index.get(id);
            if (existant != null) {
                // Mettre à jour l'instance existante en place — zéro création d'objet
                mettreAJour.accept(existant, nouveau);
                resultat.add(existant);
            } else {
                // Nouvel élément : on l'ajoute
                resultat.add(nouveau);
            }
        }

        // Appliquer les changements sur la liste observable
        // setAll déclenche un seul événement de changement plutôt que N
        liste.setAll(resultat);
    }
}
