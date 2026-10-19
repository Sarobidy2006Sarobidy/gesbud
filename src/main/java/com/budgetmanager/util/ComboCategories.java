package com.budgetmanager.util;

import com.budgetmanager.model.Category;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

import java.util.List;

/**
 * Liste de catégories avec une dernière ligne « + Nouvelle catégorie »,
 * pour qu'un compte neuf n'ait pas une liste vide sans issue.
 */
public final class ComboCategories {

    public static final int ID_NOUVELLE = -1;

    private ComboCategories() {}

    public static Category ligneNouvelle() {
        Category ligne = new Category();
        ligne.setIdCategorie(ID_NOUVELLE);
        ligne.setNom("+ Nouvelle catégorie");
        return ligne;
    }

    public static boolean estChoisie(Category categorie) {
        return categorie != null && categorie.getIdCategorie() != ID_NOUVELLE;
    }

    public static void installer(ComboBox<Category> combo, Runnable ouvrirCategories) {
        combo.setCellFactory(liste -> cellule(false));
        combo.setButtonCell(cellule(true));
        combo.valueProperty().addListener((obs, avant, apres) -> {
            if (apres != null && apres.getIdCategorie() == ID_NOUVELLE) {
                combo.setValue(estChoisie(avant) ? avant : null);
                if (ouvrirCategories != null) ouvrirCategories.run();
            }
        });
    }

    public static void remplir(ComboBox<Category> combo, List<Category> categories) {
        ObservableList<Category> items = FXCollections.observableArrayList(categories);
        items.add(ligneNouvelle());
        combo.setItems(items);
        combo.setPromptText(categories.isEmpty()
                ? "Aucune catégorie — créez-en une"
                : "Choisir");
    }

    private static ListCell<Category> cellule(boolean bouton) {
        return new ListCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(bouton ? null : null);
                    return;
                }
                setText(item == null ? null : item.getNom());
            }
        };
    }
}
