package com.budgetmanager.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ComboBox avec bouton "+" intégré pour ajouter rapidement un élément.
 * Le bouton est à droite de la liste déroulante.
 *
 * @param <T> Type des éléments dans la combo
 */
public class AddButtonComboBox<T> extends HBox {

    private static final Logger log = LoggerFactory.getLogger(AddButtonComboBox.class);

    private final ComboBox<T> comboBox;
    private final Button boutonAjouter;
    private final ObjectProperty<Runnable> onAjouterCallback = new SimpleObjectProperty<>();

    public AddButtonComboBox() {
        this.comboBox = new ComboBox<>();
        this.boutonAjouter = createAjouterButton();

        this.setAlignment(Pos.CENTER);
        this.setSpacing(4);
        this.setPrefHeight(44);
        this.setMaxHeight(44);

        HBox.setHgrow(this.comboBox, Priority.ALWAYS);
        HBox.setHgrow(this.boutonAjouter, Priority.NEVER);

        this.getChildren().addAll(this.comboBox, this.boutonAjouter);

        // Styles CSS
        this.getStyleClass().add("add-button-combo");
        this.comboBox.getStyleClass().add("add-button-combo-box");
        this.boutonAjouter.getStyleClass().add("add-button-combo-btn");
    }

    /**
     * Crée le bouton "+" avec icône circulaire.
     */
    private Button createAjouterButton() {
        Button btn = new Button();
        btn.setPrefWidth(36);
        btn.setMinWidth(36);
        btn.setMaxWidth(36);
        btn.setPrefHeight(36);
        btn.setMinHeight(36);
        btn.setMaxHeight(36);

        // Dessin de l'icône + avec Circle et Line
        Circle cercle = new Circle(16);
        cercle.setFill(Color.web("#1e88e5"));
        cercle.setStroke(Color.web("#1565c0"));
        cercle.setStrokeWidth(2);

        Line ligneV = new Line(12, 8, 12, 24);
        ligneV.setStroke(Color.WHITE);
        ligneV.setStrokeWidth(3);

        Line ligneH = new Line(8, 16, 24, 16);
        ligneH.setStroke(Color.WHITE);
        ligneH.setStrokeWidth(3);

        btn.setGraphic(new javafx.scene.Group(cercle, ligneV, ligneH));

        Tooltip tooltip = new Tooltip("Ajouter");
        Tooltip.install(btn, tooltip);

        btn.setOnAction(evt -> {
            Runnable callback = onAjouterCallback.get();
            if (callback != null) {
                callback.run();
            } else {
                log.warn("Aucun callback pour le bouton Ajouter");
            }
        });

        return btn;
    }

    // === Getters/Setters ===

    public ComboBox<T> getComboBox() {
        return comboBox;
    }

    public Button getBoutonAjouter() {
        return boutonAjouter;
    }

    public ObjectProperty<Runnable> onAjouterProperty() {
        return onAjouterCallback;
    }

    public Runnable getOnAjouter() {
        return onAjouterCallback.get();
    }

    public void setOnAjouter(Runnable onAjouter) {
        this.onAjouterCallback.set(onAjouter);
    }

    // === Pass-throughComboBox methods ===

    public void setItems(javafx.collections.ObservableList<T> items) {
        this.comboBox.setItems(items);
    }

    public javafx.collections.ObservableList<T> getItems() {
        return this.comboBox.getItems();
    }

    public void setValue(T value) {
        this.comboBox.setValue(value);
    }

    public T getValue() {
        return this.comboBox.getValue();
    }

    public void setPromptText(String text) {
        this.comboBox.setPromptText(text);
    }

    public String getPromptText() {
        return this.comboBox.getPromptText();
    }
}
