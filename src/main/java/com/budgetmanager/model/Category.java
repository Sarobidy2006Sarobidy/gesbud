package com.budgetmanager.model;

import javafx.beans.property.*;

import java.time.LocalDateTime;

/**
 * Modèle d'une catégorie avec JavaFX Properties.
 */
public class Category {

    private final IntegerProperty idCategorie       = new SimpleIntegerProperty();
    private final IntegerProperty idUser            = new SimpleIntegerProperty();
    private final StringProperty  nom               = new SimpleStringProperty();
    private final ObjectProperty<CategoryType>     type         = new SimpleObjectProperty<>();
    /** Plafond mensuel de dépense ; null = aucun plafond fixé. */
    private final ObjectProperty<Double>           budget       = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime>    dateCreation = new SimpleObjectProperty<>();

    public Category() {}

    public Category(int idCategorie, int idUser, String nom, CategoryType type, LocalDateTime dateCreation) {
        setIdCategorie(idCategorie);
        setIdUser(idUser);
        setNom(nom);
        setType(type);
        setDateCreation(dateCreation);
    }

    public Category(int idUser, String nom, CategoryType type) {
        setIdUser(idUser);
        setNom(nom);
        setType(type);
    }

    // --- idCategorie ---
    public int getIdCategorie()                     { return idCategorie.get(); }
    public void setIdCategorie(int v)               { idCategorie.set(v); }
    public IntegerProperty idCategorieProperty()    { return idCategorie; }

    // --- idUser ---
    public int getIdUser()                          { return idUser.get(); }
    public void setIdUser(int v)                    { idUser.set(v); }
    public IntegerProperty idUserProperty()         { return idUser; }

    // --- nom ---
    public String getNom()                          { return nom.get(); }
    public void setNom(String v)                    { nom.set(v); }
    public StringProperty nomProperty()             { return nom; }

    // --- type ---
    public CategoryType getType()                               { return type.get(); }
    public void setType(CategoryType v)                         { type.set(v); }
    public ObjectProperty<CategoryType> typeProperty()          { return type; }

    // --- budget (plafond mensuel de dépense) ---
    public Double getBudget()                               { return budget.get(); }
    public void setBudget(Double v)                         { budget.set(v); }
    public ObjectProperty<Double> budgetProperty()          { return budget; }

    // --- dateCreation ---
    public LocalDateTime getDateCreation()                          { return dateCreation.get(); }
    public void setDateCreation(LocalDateTime v)                    { dateCreation.set(v); }
    public ObjectProperty<LocalDateTime> dateCreationProperty()     { return dateCreation; }

    @Override
    public String toString() {
        return getNom(); // pour affichage direct dans les ComboBox
    }
}
