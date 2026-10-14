package com.budgetmanager.util;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Apparition en cascade des blocs d'une page.
 * <p>Réservé aux premières visites : une vue qui revient du cache doit
 * réapparaître instantanément, sans re-jouer l'animation à chaque navigation.</p>
 */
public final class AnimationEntree {

    /** Blocs qui entrent l'un après l'autre, dans l'ordre du document. */
    private static final List<String> BLOCS = List.of(
            "form-card", "table-card", "kpi-card", "chart-card", "auth-card-premium");

    private static final Duration PAS = Duration.millis(45);
    private static final Duration DUREE = Duration.millis(280);
    private static final double GLISSEMENT = 14;

    private AnimationEntree() {}

    public static void enCascade(Parent racine) {
        // Sans CSS appliquée, les skins (viewport du ScrollPane) n'existent pas
        // encore : la parcours s'arrêterait avant les cartes de la page.
        racine.applyCss();
        List<Node> blocs = new ArrayList<>();
        collecter(racine, blocs);
        for (int index = 0; index < blocs.size(); index++) {
            animer(blocs.get(index), PAS.multiply(index));
        }
    }

    private static void animer(Node bloc, Duration retard) {
        bloc.setOpacity(0);
        bloc.setTranslateY(GLISSEMENT);

        FadeTransition fondu = new FadeTransition(DUREE, bloc);
        fondu.setFromValue(0);
        fondu.setToValue(1);
        fondu.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition glissement = new TranslateTransition(DUREE, bloc);
        glissement.setFromY(GLISSEMENT);
        glissement.setToY(0);
        glissement.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition entree = new ParallelTransition(bloc, fondu, glissement);
        entree.setDelay(retard);
        entree.play();
    }

    private static void collecter(Node noeud, List<Node> vers) {
        for (String classe : BLOCS) {
            if (noeud.getStyleClass().contains(classe)) {
                vers.add(noeud);
                break;
            }
        }
        if (noeud instanceof Parent parent) {
            for (Node enfant : parent.getChildrenUnmodifiable()) {
                collecter(enfant, vers);
            }
        }
    }
}
