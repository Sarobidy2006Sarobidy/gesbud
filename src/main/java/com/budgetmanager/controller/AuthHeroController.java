package com.budgetmanager.controller;

import com.budgetmanager.util.Hexagon;
import com.budgetmanager.util.Icons;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Panneau décoratif de gauche des écrans de connexion / inscription / mot de
 * passe oublié (auth-hero.fxml) : badges hexagonaux flottants, dans l'esprit
 * de la page « S2Biz ». Tout est dessiné en JavaFX (Polygon + Icons).
 */
public class AuthHeroController {

    @FXML private Pane zoneHexagones;

    /** Animations en cours : arrêtées à chaque changement d'écran (voir Main.changerScene). */
    private static final List<Animation> ANIMATIONS = new ArrayList<>();

    /** Position relative (0..1) dans le panneau, taille, icône, durée de flottement. */
    private record Badge(double x, double y, double taille, Icons.IconFactory icone, double secondes) {}

    private static final Badge[] BADGES = {
            new Badge(0.30, 0.17, 44, Icons::wallet,       3.4),
            new Badge(0.62, 0.12, 54, Icons::trendingUp,   4.1),
            new Badge(0.84, 0.27, 40, Icons::pieChart,     3.7),
            new Badge(0.46, 0.34, 62, Icons::dashboard,    4.6),
            new Badge(0.15, 0.40, 38, Icons::trendingDown, 3.9),
            new Badge(0.74, 0.50, 48, Icons::tag,          4.3),
            new Badge(0.28, 0.58, 42, Icons::barChart,     3.5),
            new Badge(0.58, 0.64, 36, Icons::shieldCheck,  4.0)
    };

    @FXML
    public void initialize() {
        for (Badge b : BADGES) {
            Node badge = Hexagon.badge(b.taille(), b.icone(), true);
            badge.layoutXProperty().bind(zoneHexagones.widthProperty().multiply(b.x()).subtract(b.taille() / 2));
            badge.layoutYProperty().bind(zoneHexagones.heightProperty().multiply(b.y()).subtract(b.taille() / 2));
            zoneHexagones.getChildren().add(badge);

            TranslateTransition flottement = new TranslateTransition(Duration.seconds(b.secondes()), badge);
            flottement.setFromY(-7);
            flottement.setToY(7);
            flottement.setAutoReverse(true);
            flottement.setCycleCount(Animation.INDEFINITE);
            flottement.setInterpolator(Interpolator.EASE_BOTH);
            flottement.play();
            ANIMATIONS.add(flottement);
        }
    }

    /** Arrête les animations des écrans précédents pour éviter qu'elles ne s'accumulent. */
    public static void arreterAnimations() {
        for (Animation a : ANIMATIONS) a.stop();
        ANIMATIONS.clear();
    }
}
