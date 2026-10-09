package com.budgetmanager.util;

import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleToIntFunction;

/**
 * Rend une grille « responsive » : quand la fenêtre change de taille, les cartes
 * sont redistribuées sur plus ou moins de colonnes (comme le fait CSS sur le web).
 * Les cartes qui occupaient toute la largeur dans le FXML (columnSpan > 1) la gardent.
 */
public final class ResponsiveGrid {

    private ResponsiveGrid() {}

    /**
     * @param grille            la grille à rendre adaptable (ses enfants gardent leur ordre)
     * @param colonnesPourLargeur donne le nombre de colonnes selon la largeur disponible de la grille
     */
    public static void appliquer(GridPane grille, DoubleToIntFunction colonnesPourLargeur) {
        List<Node> noeuds = new ArrayList<>(grille.getChildren());
        Set<Node> pleineLargeur = new HashSet<>();
        for (Node n : noeuds) {
            Integer span = GridPane.getColumnSpan(n);
            if (span != null && span > 1) pleineLargeur.add(n);
        }

        int[] courant = {-1};
        grille.widthProperty().addListener((obs, ancienne, largeur) -> {
            if (largeur.doubleValue() <= 0) return;
            int colonnes = Math.max(1, colonnesPourLargeur.applyAsInt(largeur.doubleValue()));
            if (colonnes != courant[0]) {
                courant[0] = colonnes;
                disposer(grille, noeuds, pleineLargeur, colonnes);
            }
        });
    }

    private static void disposer(GridPane grille, List<Node> noeuds, Set<Node> pleineLargeur, int colonnes) {
        grille.getColumnConstraints().clear();
        for (int i = 0; i < colonnes; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / colonnes);
            grille.getColumnConstraints().add(c);
        }

        int col = 0, ligne = 0;
        for (Node n : noeuds) {
            if (pleineLargeur.contains(n)) {
                if (col != 0) { ligne++; col = 0; }
                GridPane.setConstraints(n, 0, ligne, colonnes, 1);
                ligne++;
            } else {
                GridPane.setConstraints(n, col, ligne, 1, 1);
                col++;
                if (col == colonnes) { col = 0; ligne++; }
            }
        }
    }
}
