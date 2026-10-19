package com.budgetmanager.util;

import javafx.scene.chart.AreaChart;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;

/**
 * Signale les points passés sous zéro sur une courbe en aire.
 *
 * L'aire se remplit du trait jusqu'à la ligne zéro, dans le bleu de la série :
 * un solde négatif se peindrait donc comme un surplus. Seul le point négatif se
 * munit d'un marqueur rouge, posé sur le nœud que le graphe lui consacre.
 */
public final class MarqueursDeficit {

    /** Diamètre du disque rouge ; le liseré blanc se peint par-dessus, en CSS. */
    private static final double TAILLE = 9;

    private MarqueursDeficit() {}

    /**
     * Pose les marqueurs après la disposition : un point de courbe ne reçoit son
     * nœud que lorsque le graphe a été calculé.
     *
     * Le nœud du graphe n'est jamais redimensionné pour un point sous zéro, il
     * reste à 0×0. Le disque est donc son propre enfant, centré par le conteneur
     * sur ce point, et rien n'est rogné puisque la zone n'est pas découpée.
     */
    public static void marquer(AreaChart<String, Number> courbe) {
        for (XYChart.Series<String, Number> serie : courbe.getData()) {
            for (XYChart.Data<String, Number> point : serie.getData()) {
                if (!(point.getNode() instanceof Pane emplacement)) continue;
                double valeur = point.getYValue() == null ? 0 : point.getYValue().doubleValue();
                boolean marque = emplacement.getChildrenUnmodifiable().size() > 0;
                if (valeur < 0 && !marque) {
                    Circle tache = new Circle(TAILLE / 2);
                    tache.getStyleClass().add("point-deficit");
                    emplacement.getChildren().add(tache);
                    // Le support est nu par le CSS : sans porteur, le disque resterait
                    // invisible avec son hôte à opacity 0.
                    if (!emplacement.getStyleClass().contains("porteur-deficit")) {
                        emplacement.getStyleClass().add("porteur-deficit");
                    }
                } else if (valeur >= 0 && marque) {
                    emplacement.getChildren().clear();
                    emplacement.getStyleClass().remove("porteur-deficit");
                }
            }
        }
    }
}
