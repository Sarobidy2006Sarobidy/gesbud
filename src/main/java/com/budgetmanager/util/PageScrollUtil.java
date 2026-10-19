package com.budgetmanager.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;

public final class PageScrollUtil {

    private PageScrollUtil() {}

    public static void defilerVers(ScrollPane scrollPane, Node cible) {
        Platform.runLater(() -> {
            Node contenu = scrollPane.getContent();
            scrollPane.layout();
            if (contenu instanceof Parent parent) parent.layout();

            double hauteurDefilement = contenu.getLayoutBounds().getHeight()
                    - scrollPane.getViewportBounds().getHeight();
            if (hauteurDefilement <= 0) return;

            double positionY = contenu.sceneToLocal(cible.localToScene(cible.getBoundsInLocal())).getMinY();
            double valeur = Math.max(0, Math.min(1, positionY / hauteurDefilement));
            scrollPane.setVvalue(valeur);
        });
    }
}
