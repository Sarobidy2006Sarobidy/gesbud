package com.budgetmanager.util;

import javafx.scene.control.Button;
import javafx.scene.control.TableView;

/**
 * « Modifier » et « Supprimer » ne servent que lorsqu'une ligne est choisie.
 */
public final class BoutonsLigne {

    private BoutonsLigne() {}

    public static void lier(TableView<?> table, Button... boutons) {
        table.getSelectionModel().selectedItemProperty()
                .addListener((obs, avant, apres) -> appliquer(table, boutons));
        appliquer(table, boutons);
    }

    /** À rappeler après un rechargement : la liste a changé, le choix peut avoir sauté. */
    public static void appliquer(TableView<?> table, Button... boutons) {
        boolean aucune = table.getSelectionModel().getSelectedItem() == null;
        for (Button bouton : boutons) {
            if (bouton != null) bouton.setDisable(aucune);
        }
    }
}
