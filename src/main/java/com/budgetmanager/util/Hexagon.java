package com.budgetmanager.util;

import javafx.scene.Node;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Badge hexagonal lumineux (pointe vers le haut) contenant une icône blanche :
 * le motif graphique de l'écran de connexion. Dessiné avec {@link Polygon},
 * sans aucune image.
 */
public final class Hexagon {

    private Hexagon() {}

    /**
     * @param taille hauteur du hexagone en pixels
     * @param icone  icône dessinée au centre (blanche)
     * @param halo   {@code true} pour ajouter la lueur bleue autour du badge
     */
    public static Node badge(double taille, Icons.IconFactory icone, boolean halo) {
        double rayon = taille / 2;

        Polygon forme = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(-90 + 60 * i);
            forme.getPoints().addAll(rayon * Math.cos(angle), rayon * Math.sin(angle));
        }
        forme.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2f6bff")),
                new Stop(1, Color.web("#1738a8"))));
        forme.setStroke(Color.web("#6db3ff"));
        forme.setStrokeWidth(Math.max(1.2, taille / 34));
        forme.setStrokeLineJoin(StrokeLineJoin.ROUND);
        if (halo) {
            forme.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.web("#3d8bff", 0.75), taille * 0.38, 0.12, 0, 0));
        }

        StackPane badge = new StackPane(forme, icone.create(taille * 0.46, Color.WHITE));
        badge.setPickOnBounds(false);
        return badge;
    }
}
