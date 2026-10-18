package com.budgetmanager.controller;

import com.budgetmanager.util.Hexagon;
import com.budgetmanager.util.Icons;
import javafx.animation.*;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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

    @FXML private StackPane puceAtout1;
    @FXML private StackPane puceAtout2;
    @FXML private StackPane puceAtout3;
    @FXML private VBox blocAccroche;
    @FXML private VBox blocAtouts;
    @FXML private Region filetAccroche;
    @FXML private Label titreAccroche;
    @FXML private Label sousTitreAccroche;

    /* Largeurs de panneau en dessous desquelles l'accroche perd des éléments. Elle
       ne garde que la place laissée par le formulaire, qui reste prioritaire. Mais
       elle se retire étage par étage — titre, puis sous-titre, puis atouts : tout
       couper d'un coup laissait le panneau vide à la taille d'ouverture même de
       l'application (le hero n'y mesure que ~400 px sur 1 040). */
    private static final double LARGEUR_TITRE = 330;
    private static final double LARGEUR_SOUS_TITRE = 400;
    private static final double LARGEUR_ATOUTS = 620;
    /* Paliers typographiques : en dessous, la colonne de texte tient sur deux lignes
       sans déborder ; au-dessus, elle a de l'air et peut grandir au lieu de laisser
       un quart du panneau vide à sa droite. */
    private static final double LARGEUR_SERRE = 560;
    private static final double LARGEUR_AMPLE = 860;

    /** Couleur des glyphes des atouts : lisible sur le bleu profond du panneau. */
    private static final Color COULEUR_ATOUT = Color.web("#9ad4ff");

    /* Les badges vivent dans la place laissée libre au-dessus de l'accroche : 5 % en
       haut, et pour plafond le sommet du bloc de texte moins une marge. Une fraction
       fixe de la hauteur ne suffisait pas—à 1220x760 l'accroche remontait plus haut
       que le bandeau et les badges la traversaient. */
    private static final double BADGES_TOP = 0.05;
    private static final double BADGES_MINIMUM = 0.30;
    /* Les marges du champ ne sont pas du décor : un badge est un disque de jusqu'à
       72 px, et la parallaxe peut encore le pousser de 26 px horizontalement et
       18 px verticalement. Une marge plus petite que rayon + dérive, et le badge
       était coupé par le bord de la fenêtre ou touchait le texte. */
    private static final double MARGE_HAUT_BADGES = 60;
    private static final double MARGE_BAS_BADGES = 66;

    private static final List<Animation> ANIMATIONS = new ArrayList<>();
    private static final Random RNG = new Random(42);

    /** Transition de parallaxe réutilisée à chaque déplacement de la souris. */
    private TranslateTransition parallaxe;

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

    /* Les ordonnées du tableau s'arrêtent à 0,78 : posées telles quelles dans la
       bande, elles laissaient un quart de celle-ci vide sous le badge le plus bas —
       une bande morte entre les badges et l'accroche en fenêtre maximisée. */
    private static final double Y_PLANCHER;
    private static final double Y_PLAFOND;

    static {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (Badge b : BADGES) {
            min = Math.min(min, b.y());
            max = Math.max(max, b.y());
        }
        Y_PLANCHER = min;
        Y_PLAFOND = max;
    }

    /** L'ordonnée du tableau, remise à plat sur toute la hauteur du champ. */
    private static double fractionY(double y) {
        return (y - Y_PLANCHER) / (Y_PLAFOND - Y_PLANCHER);
    }

    @FXML
    public void initialize() {
        // Particules de fond (cercles lumineux flottants)
        ajouterParticules();
        installerAtouts();
        adapterAccrocheALargeur();

        for (Badge b : BADGES) {
            Node badge = Hexagon.badge(b.taille(), b.icone(), true);

            // Centrage relatif sur le panneau
            badge.layoutXProperty().bind(
                    zoneHexagones.widthProperty().multiply(b.x()).subtract(b.taille() / 2));
            // Bande haute uniquement : l'accroche est calée en bas du panneau et
            // les badges la recouvraient dès qu'elle remontait plus haut qu'un
            // pourcentage fixe de la hauteur.
            badge.layoutYProperty().bind(Bindings.createDoubleBinding(
                    () -> hautBande() + (basBande() - hautBande()) * fractionY(b.y()) - b.taille() / 2,
                    zoneHexagones.heightProperty(),
                    blocAccroche.heightProperty(), blocAccroche.widthProperty(),
                    blocAccroche.managedProperty(), blocAccroche.visibleProperty()));

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

        installerParallaxe();
    }

    /**
     * Le champ de badges suit la souris de quelques pixels, en sens inverse :
     * c'est ce qui donne la sensation de profondeur par-dessus les rotations 3D.
     * Une seule Transition est réutilisée — la souris émet des dizaines
     * d'événements par seconde, en créer un par événement saturerait le pulse.
     */
    private void installerParallaxe() {
        Node panneau = zoneHexagones.getParent();
        if (panneau == null) return;

        parallaxe = new TranslateTransition(Duration.millis(500), zoneHexagones);
        parallaxe.setInterpolator(Interpolator.EASE_OUT);

        panneau.setOnMouseMoved(evenement -> viserParallaxe(
                evenement.getX(), evenement.getY(),
                panneau.getBoundsInLocal().getWidth(), panneau.getBoundsInLocal().getHeight()));
        panneau.setOnMouseExited(evenement -> viserParallaxe(0, 0, 0, 0));
    }

    private void viserParallaxe(double x, double y, double largeur, double hauteur) {
        double dx = largeur <= 0 ? 0 : x / largeur - 0.5;
        double dy = hauteur <= 0 ? 0 : y / hauteur - 0.5;
        // Repartir de la position courante : la souris bouge plus vite que les
        // 500 ms de glissement, sans cela chaque saut ramènerait le champ à zéro.
        parallaxe.setFromX(zoneHexagones.getTranslateX());
        parallaxe.setFromY(zoneHexagones.getTranslateY());
        parallaxe.setToX(dx * -26);
        parallaxe.setToY(dy * -18);
        parallaxe.play();
    }

    /**
     * Les trois puces d'atouts du bas du panneau : mêmes dessins que les badges,
     * posés sur une pastille ronde pour se distinguer du champ flottant.
     */
    private void installerAtouts() {
        puce(puceAtout1, Icons::trendingUp);
        puce(puceAtout2, Icons::barChart);
        puce(puceAtout3, Icons::shieldCheck);
    }

    /**
     * Le hero se comprime avant le formulaire quand la fenêtre rétrécit : au-delà
     * d'un certain point son texte passerait sur les badges. Il se retire étage par
     * étage, et pendant ce temps sa typographie suit la place réellement offerte.
     */
    private void adapterAccrocheALargeur() {
        if (blocAccroche == null || !(zoneHexagones.getParent() instanceof Region panneau)) {
            return;
        }
        titreAccroche.visibleProperty().bind(
                panneau.widthProperty().greaterThan(LARGEUR_TITRE));
        titreAccroche.managedProperty().bind(titreAccroche.visibleProperty());
        filetAccroche.visibleProperty().bind(titreAccroche.visibleProperty());
        filetAccroche.managedProperty().bind(titreAccroche.managedProperty());
        sousTitreAccroche.visibleProperty().bind(
                panneau.widthProperty().greaterThan(LARGEUR_SOUS_TITRE));
        sousTitreAccroche.managedProperty().bind(sousTitreAccroche.visibleProperty());
        blocAtouts.visibleProperty().bind(
                panneau.widthProperty().greaterThan(LARGEUR_ATOUTS));
        blocAtouts.managedProperty().bind(blocAtouts.visibleProperty());

        panneau.widthProperty().addListener((obs, avant, apres) ->
                appliquerPalierTypographique(apres.doubleValue()));
        appliquerPalierTypographique(panneau.getWidth());
    }

    /**
     * Le palier de taille est un style de plus sur le bloc, jamais une valeur posée à
     * la main : le CSS garde la main sur les polices et les largeurs de colonne.
     */
    private void appliquerPalierTypographique(double largeurPanneau) {
        List<String> attendu = largeurPanneau >= LARGEUR_AMPLE
                ? List.of("auth-hero-text", "auth-hero-ample")
                : largeurPanneau <= LARGEUR_SERRE
                        ? List.of("auth-hero-text", "auth-hero-serre")
                        : List.of("auth-hero-text");
        if (!blocAccroche.getStyleClass().equals(attendu)) {
            blocAccroche.getStyleClass().setAll(attendu);
        }
    }

    private void puce(StackPane conteneur, Icons.IconFactory icone) {
        if (conteneur == null) return;
        conteneur.getChildren().setAll(icone.create(15, COULEUR_ATOUT));
    }

    /** Sommet de la bande de badges : de l'air au-dessus du plus grand disque. */
    private double hautBande() {
        return Math.max(zoneHexagones.getHeight() * BADGES_TOP, MARGE_HAUT_BADGES);
    }

    /** Plancher de la bande : jamais au-dessus du texte de l'accroche, jamais trop bas. */
    private double basBande() {
        double hauteur = zoneHexagones.getHeight();
        // Le VBox de l'accroche occupe toute la hauteur du panneau (son contenu est
        // aligné en bas) : seule sa hauteur préférée mesure le bloc de texte réel.
        double accroche = blocAccroche.isManaged() && blocAccroche.isVisible()
                ? blocAccroche.prefHeight(blocAccroche.getWidth()) : 0;
        return Math.max(hauteur * BADGES_MINIMUM, hauteur - accroche - MARGE_BAS_BADGES);
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
