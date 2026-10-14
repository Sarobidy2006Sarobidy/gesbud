package com.budgetmanager.util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * État vide d'un tableau ou d'un graphique.
 * <p>Sans message, une page sans donnée ressemble à une page cassée : le
 * placeholder explique ce qui manque et comment le faire apparaître.</p>
 */
public final class VideEtat {

    private static final Color COULEUR_ICONE = Color.web("#5b7099");

    private VideEtat() {}

    /** Couleur unique des icônes d'état vide (bleu ardoise, fond clair). */
    public static Color couleurIcone() {
        return COULEUR_ICONE;
    }

    public static Node creer(Node icone, String titre, String aide) {
        StackPane puce = new StackPane(icone);
        puce.getStyleClass().add("vide-icone");

        Label labelTitre = new Label(titre);
        labelTitre.getStyleClass().add("vide-titre");

        Label labelAide = new Label(aide);
        labelAide.getStyleClass().add("vide-aide");
        labelAide.setWrapText(true);
        labelAide.setMaxWidth(320);

        VBox boite = new VBox(10, puce, labelTitre, labelAide);
        boite.setAlignment(Pos.CENTER);
        boite.getStyleClass().add("vide-etat");
        return boite;
    }

    /** Placeholder d'un tableau vide — y compris quand un filtre ne renvoie rien. */
    public static void surTable(TableView<?> table, Node icone, String titre, String aide) {
        table.setPlaceholder(creer(icone, titre, aide));
    }
}
