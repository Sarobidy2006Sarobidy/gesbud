package com.budgetmanager.controller;

import com.budgetmanager.util.Hexagon;
import com.budgetmanager.util.Icons;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Panneau décoratif gauche — animations 3D : badges hexagonaux avec rotation Y
 * (perspective simulée), flottement sinusoïdal, halo pulsant et particules orbitales.
 */
public class AuthHeroController {

    @FXML private Pane zoneHexagones;

    private static final List<Animation> ANIMATIONS = new ArrayList<>();
    private static final Random RNG = new Random(42);

    /** Définition d'un badge : position relative, taille, icône, phases. */
    private record Badge(double x, double y, double taille, Icons.IconFactory icone,
                         double flotteSec, double delaiSec, double rotAmplitude) {}

    private static final Badge[] BADGES = {
            new Badge(0.20, 0.13, 52, Icons::wallet,       3.6, 0.0,  22),
            new Badge(0.60, 0.10, 64, Icons::trendingUp,   4.3, 0.6,  28),
            new Badge(0.85, 0.22, 44, Icons::pieChart,     3.9, 1.1,  18),
            new Badge(0.42, 0.32, 72, Icons::dashboard,    4.8, 0.3,  32),
            new Badge(0.12, 0.45, 42, Icons::trendingDown, 4.1, 0.9,  20),
            new Badge(0.75, 0.48, 54, Icons::tag,          4.5, 1.4,  25),
            new Badge(0.30, 0.62, 48, Icons::barChart,     3.7, 0.5,  22),
            new Badge(0.62, 0.66, 40, Icons::shieldCheck,  4.2, 1.7,  15),
            new Badge(0.88, 0.72, 46, Icons::save,         3.5, 0.8,  20),
            new Badge(0.08, 0.78, 38, Icons::clock,        4.0, 1.2,  18),
    };

    @FXML
    public void initialize() {
        // Particules de fond (cercles lumineux flottants)
        ajouterParticules();

        for (Badge b : BADGES) {
            Node badge = Hexagon.badge(b.taille(), b.icone(), true);

            // Centrage relatif sur le panneau
            badge.layoutXProperty().bind(
                    zoneHexagones.widthProperty().multiply(b.x()).subtract(b.taille() / 2));
            badge.layoutYProperty().bind(
                    zoneHexagones.heightProperty().multiply(b.y()).subtract(b.taille() / 2));

            // --- Rotation Y 3D simulée via Rotate transform ---
            Rotate rotY = new Rotate(0, b.taille() / 2, b.taille() / 2, 0, Rotate.Y_AXIS);
            badge.getTransforms().add(rotY);

            // Inclinaison initiale aléatoire pour chaque badge
            rotY.setAngle(RNG.nextDouble() * 30 - 15);

            zoneHexagones.getChildren().add(badge);

            // 1) Flottement vertical (translateY sinusoïdal)
            TranslateTransition flotte = new TranslateTransition(
                    Duration.seconds(b.flotteSec()), badge);
            flotte.setFromY(-9);
            flotte.setToY(9);
            flotte.setAutoReverse(true);
            flotte.setCycleCount(Animation.INDEFINITE);
            flotte.setInterpolator(Interpolator.EASE_BOTH);
            flotte.setDelay(Duration.seconds(b.delaiSec()));
            flotte.play();
            ANIMATIONS.add(flotte);

            // 2) Rotation Y "flip 3D" (oscillation ±amplitude)
            double amp = b.rotAmplitude();
            Timeline rot3d = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(rotY.angleProperty(), -amp, Interpolator.EASE_BOTH)),
                    new KeyFrame(Duration.seconds(b.flotteSec() * 1.3),
                            new KeyValue(rotY.angleProperty(), amp, Interpolator.EASE_BOTH))
            );
            rot3d.setAutoReverse(true);
            rot3d.setCycleCount(Animation.INDEFINITE);
            rot3d.setDelay(Duration.seconds(b.delaiSec() + 0.2));
            rot3d.play();
            ANIMATIONS.add(rot3d);

            // 3) Scale pulsant (badge respire)
            ScaleTransition pulse = new ScaleTransition(
                    Duration.seconds(b.flotteSec() * 0.8), badge);
            pulse.setFromX(0.92);
            pulse.setFromY(0.92);
            pulse.setToX(1.08);
            pulse.setToY(1.08);
            pulse.setAutoReverse(true);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.setInterpolator(Interpolator.EASE_BOTH);
            pulse.setDelay(Duration.seconds(b.delaiSec() + 0.5));
            pulse.play();
            ANIMATIONS.add(pulse);
        }

        // Animation de fond : rotation lente de l'ensemble
        RotateTransition tourPanneau = new RotateTransition(Duration.seconds(60), zoneHexagones);
        tourPanneau.setByAngle(3);
        tourPanneau.setAutoReverse(true);
        tourPanneau.setCycleCount(Animation.INDEFINITE);
        tourPanneau.setInterpolator(Interpolator.EASE_BOTH);
        tourPanneau.play();
        ANIMATIONS.add(tourPanneau);
    }

    /** Ajoute des cercles lumineux flottants en arrière-plan pour renforcer l'effet 3D. */
    private void ajouterParticules() {
        double[][] particules = {
                // x%,  y%,  rayon, opacité, durée
                {0.15, 0.25, 60, 0.08, 6.0},
                {0.70, 0.15, 90, 0.06, 8.0},
                {0.45, 0.55, 45, 0.10, 5.5},
                {0.85, 0.65, 70, 0.07, 7.0},
                {0.25, 0.75, 55, 0.09, 6.5},
                {0.60, 0.85, 40, 0.11, 5.0},
        };

        for (double[] p : particules) {
            Circle cercle = new Circle(p[2]);
            cercle.setFill(Color.web("#5daaff", p[3]));
            cercle.setEffect(new DropShadow(BlurType.GAUSSIAN,
                    Color.web("#3d8bff", 0.5), p[2] * 0.8, 0.2, 0, 0));
            cercle.layoutXProperty().bind(
                    zoneHexagones.widthProperty().multiply(p[0]));
            cercle.layoutYProperty().bind(
                    zoneHexagones.heightProperty().multiply(p[1]));
            zoneHexagones.getChildren().add(cercle);

            // Flottement des particules
            TranslateTransition flotte = new TranslateTransition(
                    Duration.seconds(p[4]), cercle);
            flotte.setFromY(-15);
            flotte.setToY(15);
            flotte.setAutoReverse(true);
            flotte.setCycleCount(Animation.INDEFINITE);
            flotte.setInterpolator(Interpolator.EASE_BOTH);
            flotte.setDelay(Duration.seconds(RNG.nextDouble() * 2));
            flotte.play();
            ANIMATIONS.add(flotte);

            // Pulse opacité
            FadeTransition fade = new FadeTransition(Duration.seconds(p[4] * 1.2), cercle);
            fade.setFromValue(0.4);
            fade.setToValue(1.0);
            fade.setAutoReverse(true);
            fade.setCycleCount(Animation.INDEFINITE);
            fade.setInterpolator(Interpolator.EASE_BOTH);
            fade.play();
            ANIMATIONS.add(fade);
        }
    }

    public static void arreterAnimations() {
        for (Animation a : ANIMATIONS) a.stop();
        ANIMATIONS.clear();
    }

    public static void suspendreAnimations() {
        for (Animation a : ANIMATIONS) {
            if (a.getStatus() == Animation.Status.RUNNING) a.pause();
        }
    }

    public static void reprendreAnimations() {
        for (Animation a : ANIMATIONS) {
            if (a.getStatus() == Animation.Status.PAUSED) a.play();
        }
    }

    /* ---- compatibilité imports ---- */
}
