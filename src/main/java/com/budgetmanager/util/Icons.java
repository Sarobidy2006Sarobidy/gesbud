package com.budgetmanager.util;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

/**
 * Icônes vectorielles dessinées à la main avec l'API {@code Shape} de JavaFX
 * (Circle, Line, Rectangle, Polygon, Arc, Polyline...). Aucune bibliothèque
 * externe, aucune image, aucun emoji : uniquement du JavaFX standard
 * ("100% Java").
 *
 * Chaque icône est construite dans un espace de coordonnées 24x24 (comme un
 * viewBox SVG classique) puis mise à l'échelle vers la taille demandée, ce
 * qui garantit des proportions cohérentes entre toutes les icônes.
 */
public final class Icons {

    private Icons() {}

    private static final double CANVAS = 24;

    /** Permet de passer une icône (ex. {@code Icons::dashboard}) comme valeur, pour la redessiner à la demande. */
    @FunctionalInterface
    public interface IconFactory {
        Node create(double size, Color color);
    }

    /* =============================== Noyau =============================== */

    /**
     * Empile les formes, applique le style "trait" (contour) aux formes sans
     * remplissage et le style "plein" à celles qui en ont un, puis met
     * l'ensemble à l'échelle dans un conteneur de taille fixe.
     */
    private static Node build(double size, Color color, double strokeWidth, Shape... shapes) {
        for (Shape s : shapes) {
            if (s.getFill() == null) {
                s.setStroke(color);
                s.setStrokeWidth(strokeWidth);
                s.setStrokeLineCap(StrokeLineCap.ROUND);
                s.setStrokeLineJoin(StrokeLineJoin.ROUND);
            }
        }
        Group group = new Group((Node[]) shapes);
        double scale = size / CANVAS;
        group.setScaleX(scale);
        group.setScaleY(scale);

        StackPane holder = new StackPane(group);
        holder.setPrefSize(size, size);
        holder.setMinSize(size, size);
        holder.setMaxSize(size, size);
        holder.setAlignment(Pos.CENTER);
        holder.setPickOnBounds(false);
        holder.setMouseTransparent(true);
        return holder;
    }

    private static Line line(double x1, double y1, double x2, double y2) {
        return new Line(x1, y1, x2, y2);
    }

    private static Circle circleOutline(double cx, double cy, double r) {
        Circle c = new Circle(cx, cy, r);
        c.setFill(null);
        return c;
    }

    private static Circle circleFilled(double cx, double cy, double r, Color color) {
        Circle c = new Circle(cx, cy, r);
        c.setFill(color);
        return c;
    }

    private static Rectangle rectOutline(double x, double y, double w, double h, double arc) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.setArcWidth(arc);
        r.setArcHeight(arc);
        r.setFill(null);
        return r;
    }

    private static Rectangle rectFilled(double x, double y, double w, double h, double arc, Color color) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.setArcWidth(arc);
        r.setArcHeight(arc);
        r.setFill(color);
        return r;
    }

    private static Polygon polygonOutline(double... points) {
        Polygon p = new Polygon(points);
        p.setFill(null);
        return p;
    }

    private static Polygon polygonFilled(Color color, double... points) {
        Polygon p = new Polygon(points);
        p.setFill(color);
        return p;
    }

    private static Polyline polylineOutline(double... points) {
        Polyline p = new Polyline(points);
        p.setFill(null);
        return p;
    }

    private static Arc arcOutline(double cx, double cy, double rx, double ry, double start, double extent) {
        Arc a = new Arc(cx, cy, rx, ry, start, extent);
        a.setType(ArcType.OPEN);
        a.setFill(null);
        return a;
    }

    /* ============================ Icônes de navigation ============================ */

    /** Grille 2x2 : tableau de bord. */
    public static Node dashboard(double size, Color color) {
        return build(size, color, 1.8,
                rectOutline(3, 3, 8, 8, 2),
                rectOutline(13, 3, 8, 8, 2),
                rectOutline(3, 13, 8, 8, 2),
                rectOutline(13, 13, 8, 8, 2));
    }

    /** Flèche montante : revenus. */
    public static Node trendingUp(double size, Color color) {
        return build(size, color, 1.8,
                polylineOutline(3, 17, 9, 11, 13, 15, 21, 6),
                polygonFilled(color, 21, 12, 21, 6, 15, 6));
    }

    /** Flèche descendante : dépenses. */
    public static Node trendingDown(double size, Color color) {
        return build(size, color, 1.8,
                polylineOutline(3, 7, 9, 13, 13, 9, 21, 18),
                polygonFilled(color, 21, 12, 21, 18, 15, 18));
    }

    /** Étiquette : catégories. */
    public static Node tag(double size, Color color) {
        return build(size, color, 1.8,
                polygonOutline(12.6, 3, 21, 3, 21, 11.4, 12.6, 20, 4, 11.4, 4, 3),
                circleFilled(8, 8, 1.4, color));
    }

    /** Horloge : historique. */
    public static Node clock(double size, Color color) {
        return build(size, color, 1.8,
                circleOutline(12, 12, 9),
                line(12, 12, 12, 6.5),
                line(12, 12, 16.5, 14));
    }

    /** Barres : statistiques. */
    public static Node barChart(double size, Color color) {
        return build(size, color, 1.8,
                rectFilled(4, 12, 4, 9, 1.5, color),
                rectFilled(10, 6, 4, 15, 1.5, color),
                rectFilled(16, 15, 4, 6, 1.5, color));
    }

    /** Curseurs de réglage : paramètres. */
    public static Node settings(double size, Color color) {
        return build(size, color, 1.8,
                line(3, 6, 21, 6), circleFilled(7, 6, 2, color),
                line(3, 12, 21, 12), circleFilled(16, 12, 2, color),
                line(3, 18, 21, 18), circleFilled(9, 18, 2, color));
    }

    /** Porte + flèche sortante : déconnexion. */
    public static Node logOut(double size, Color color) {
        return build(size, color, 1.8,
                polylineOutline(14, 3, 6, 3, 6, 21, 14, 21),
                line(10, 12, 21, 12),
                polygonFilled(color, 21, 12, 15, 8, 15, 16));
    }

    /* ============================= Icônes de champ ============================= */

    public static Node mail(double size, Color color) {
        return build(size, color, 1.7,
                rectOutline(3, 5, 18, 14, 2),
                polylineOutline(3, 6, 12, 13, 21, 6));
    }

    public static Node lock(double size, Color color) {
        return build(size, color, 1.7,
                rectOutline(5, 11, 14, 10, 2),
                arcOutline(12, 11, 4, 6, 0, 180));
    }

    public static Node userPlus(double size, Color color) {
        return build(size, color, 1.7,
                circleOutline(9, 8, 4),
                arcOutline(9, 21, 7, 7, 0, 180),
                line(19, 8, 19, 14),
                line(16, 11, 22, 11));
    }

    public static Node dollar(double size, Color color) {
        return build(size, color, 1.7,
                line(12, 2, 12, 22),
                polylineOutline(17, 6, 9.5, 6, 7.5, 7.5, 7.5, 10, 9, 11,
                        15, 13, 16.5, 14.5, 16.5, 17, 14.5, 18.5, 7, 18.5));
    }

    /* ============================ Icônes d'action ============================ */

    public static Node plusCircle(double size, Color color) {
        return build(size, color, 1.8,
                circleOutline(12, 12, 9),
                line(12, 7.5, 12, 16.5),
                line(7.5, 12, 16.5, 12));
    }

    public static Node edit(double size, Color color) {
        return build(size, color, 1.8,
                line(4, 20, 15, 9),
                polygonFilled(color, 15, 9, 18, 6, 21, 9, 18, 12),
                line(4, 20, 4.5, 16.5),
                line(4.5, 16.5, 8, 17));
    }

    public static Node trash(double size, Color color) {
        return build(size, color, 1.8,
                line(3, 6, 21, 6),
                rectOutline(6, 6, 12, 15, 2),
                line(10, 6, 10, 4),
                line(14, 6, 14, 4),
                line(10, 10, 10, 18),
                line(14, 10, 14, 18));
    }

    public static Node filter(double size, Color color) {
        return build(size, color, 1.8,
                polygonOutline(3, 4, 21, 4, 14, 13, 14, 20, 10, 18, 10, 13));
    }

    public static Node rotateCcw(double size, Color color) {
        return build(size, color, 1.8,
                arcOutline(12, 12, 8, 8, 30, 300),
                polygonFilled(color, 4.5, 5, 4.5, 10, 9.5, 10));
    }

    public static Node save(double size, Color color) {
        return build(size, color, 1.7,
                rectOutline(5, 3, 14, 18, 2),
                rectOutline(8, 3, 8, 6, 1),
                rectOutline(8, 13, 8, 8, 1));
    }

    public static Node key(double size, Color color) {
        return build(size, color, 1.7,
                circleOutline(7, 7, 4),
                line(10, 10, 20, 20),
                line(16, 16, 19, 13),
                line(18, 18, 21, 15));
    }

    public static Node logIn(double size, Color color) {
        return build(size, color, 1.8,
                polylineOutline(10, 3, 18, 3, 18, 21, 10, 21),
                line(14, 12, 3, 12),
                polygonFilled(color, 3, 12, 9, 8, 9, 16));
    }

    public static Node pieChart(double size, Color color) {
        Arc slice = new Arc(12, 12, 9, 9, 0, 110);
        slice.setType(ArcType.ROUND);
        slice.setFill(color);
        return build(size, color, 1.6, circleOutline(12, 12, 9), slice);
    }

    /** Portefeuille : logo de l'application (écrans de connexion / inscription). */
    public static Node wallet(double size, Color color) {
        return build(size, color, 1.7,
                rectOutline(3, 6, 18, 13, 3),
                polylineOutline(3, 9, 17, 9, 17, 9),
                circleFilled(17.5, 13.5, 1.3, color));
    }

    /* ======================= Icônes d'authentification / messages ======================= */

    /** Silhouette : champ « nom / prénom ». */
    public static Node user(double size, Color color) {
        return build(size, color, 1.7,
                circleOutline(12, 8, 4),
                arcOutline(12, 21, 8, 8, 0, 180));
    }

    /** Point d'interrogation dans un cercle : question de sécurité. */
    public static Node helpCircle(double size, Color color) {
        return build(size, color, 1.7,
                circleOutline(12, 12, 9),
                arcOutline(12, 9.5, 2.8, 2.6, -20, 220),
                polylineOutline(12, 12.2, 12, 14),
                circleFilled(12, 17, 1.0, color));
    }

    /** Bouclier : réponse de sécurité / protection du compte. */
    public static Node shield(double size, Color color) {
        return build(size, color, 1.7,
                polygonOutline(12, 3, 20, 6, 20, 12, 17.5, 17.5, 12, 21, 6.5, 17.5, 4, 12, 4, 6));
    }

    /** Bouclier coché : étape de vérification validée. */
    public static Node shieldCheck(double size, Color color) {
        return build(size, color, 1.7,
                polygonOutline(12, 3, 20, 6, 20, 12, 17.5, 17.5, 12, 21, 6.5, 17.5, 4, 12, 4, 6),
                polylineOutline(8.5, 12, 11, 14.5, 15.5, 9.5));
    }

    /** Flèche vers la gauche : retour. */
    public static Node arrowLeft(double size, Color color) {
        return build(size, color, 1.9,
                line(20, 12, 5, 12),
                polylineOutline(11, 6, 5, 12, 11, 18));
    }

    /** Coche simple : étape validée. */
    public static Node check(double size, Color color) {
        return build(size, color, 2.4,
                polylineOutline(5, 12.5, 10, 17.5, 19, 7));
    }

    /** Coche dans un cercle : succès. */
    public static Node checkCircle(double size, Color color) {
        return build(size, color, 1.8,
                circleOutline(12, 12, 9),
                polylineOutline(7.5, 12.5, 10.5, 15.5, 16.5, 9));
    }

    /** Point d'exclamation dans un cercle : erreur / avertissement. */
    public static Node alertCircle(double size, Color color) {
        return build(size, color, 1.8,
                circleOutline(12, 12, 9),
                line(12, 7.5, 12, 13),
                circleFilled(12, 16.4, 1.0, color));
    }

    /** « i » dans un cercle : information. */
    public static Node infoCircle(double size, Color color) {
        return build(size, color, 1.8,
                circleOutline(12, 12, 9),
                line(12, 11, 12, 16.5),
                circleFilled(12, 7.8, 1.0, color));
    }

    /** Œil ouvert : afficher le mot de passe. */
    public static Node eye(double size, Color color) {
        return build(size, color, 1.7,
                arcOutline(12, 17, 10, 9, 28, 124),
                arcOutline(12, 7, 10, 9, 208, 124),
                circleOutline(12, 12, 3));
    }

    /** Œil barré : masquer le mot de passe. */
    public static Node eyeOff(double size, Color color) {
        return build(size, color, 1.7,
                arcOutline(12, 17, 10, 9, 28, 124),
                arcOutline(12, 7, 10, 9, 208, 124),
                circleOutline(12, 12, 3),
                line(4, 4, 20, 20));
    }
}
