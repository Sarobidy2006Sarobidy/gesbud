package com.budgetmanager.model;

import javafx.beans.property.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Modèle d'une dépense avec JavaFX Properties.
 */
public class Expense {

    private final IntegerProperty idDepense         = new SimpleIntegerProperty();
    private final IntegerProperty idUser            = new SimpleIntegerProperty();
    private final IntegerProperty idCategorie       = new SimpleIntegerProperty();
    private final StringProperty  nomCategorie      = new SimpleStringProperty();
    private final DoubleProperty  montant           = new SimpleDoubleProperty();
    private final StringProperty  description       = new SimpleStringProperty();
    private final ObjectProperty<LocalDate>     dateDepense         = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> dateCreation        = new SimpleObjectProperty<>();
    private final ObjectProperty<Frequency>     frequence           = new SimpleObjectProperty<>(Frequency.PONCTUEL);
    private final ObjectProperty<LocalDate>     dateFin             = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate>     derniereGeneration  = new SimpleObjectProperty<>();
    private Integer idSource;

    public Expense() {}

    public Expense(int idUser, int idCategorie, double montant, String description, LocalDate dateDepense) {
        setIdUser(idUser);
        setIdCategorie(idCategorie);
        setMontant(montant);
        setDescription(description);
        setDateDepense(dateDepense);
    }

    // --- idDepense ---
    public int getIdDepense()                   { return idDepense.get(); }
    public void setIdDepense(int v)             { idDepense.set(v); }
    public IntegerProperty idDepenseProperty()  { return idDepense; }

    // --- idUser ---
    public int getIdUser()                      { return idUser.get(); }
    public void setIdUser(int v)                { idUser.set(v); }
    public IntegerProperty idUserProperty()     { return idUser; }

    // --- idCategorie ---
    public int getIdCategorie()                 { return idCategorie.get(); }
    public void setIdCategorie(int v)           { idCategorie.set(v); }
    public IntegerProperty idCategorieProperty(){ return idCategorie; }

    // --- nomCategorie ---
    public String getNomCategorie()             { return nomCategorie.get(); }
    public void setNomCategorie(String v)       { nomCategorie.set(v); }
    public StringProperty nomCategorieProperty(){ return nomCategorie; }

    // --- montant ---
    public double getMontant()                  { return montant.get(); }
    public void setMontant(double v)            { montant.set(v); }
    public DoubleProperty montantProperty()     { return montant; }

    // --- description ---
    public String getDescription()              { return description.get(); }
    public void setDescription(String v)        { description.set(v); }
    public StringProperty descriptionProperty() { return description; }

    // --- dateDepense ---
    public LocalDate getDateDepense()                           { return dateDepense.get(); }
    public void setDateDepense(LocalDate v)                     { dateDepense.set(v); }
    public ObjectProperty<LocalDate> dateDepenseProperty()      { return dateDepense; }

    // --- dateCreation ---
    public LocalDateTime getDateCreation()                          { return dateCreation.get(); }
    public void setDateCreation(LocalDateTime v)                    { dateCreation.set(v); }
    public ObjectProperty<LocalDateTime> dateCreationProperty()     { return dateCreation; }

    // --- frequence ---
    public Frequency getFrequence()                         { return frequence.get() == null ? Frequency.PONCTUEL : frequence.get(); }
    public void setFrequence(Frequency v)                   { frequence.set(v == null ? Frequency.PONCTUEL : v); }
    public ObjectProperty<Frequency> frequenceProperty()    { return frequence; }

    // --- dateFin ---
    public LocalDate getDateFin()                           { return dateFin.get(); }
    public void setDateFin(LocalDate v)                     { dateFin.set(v); }
    public ObjectProperty<LocalDate> dateFinProperty()      { return dateFin; }

    // --- derniereGeneration ---
    public LocalDate getDerniereGeneration()                            { return derniereGeneration.get(); }
    public void setDerniereGeneration(LocalDate v)                      { derniereGeneration.set(v); }
    public ObjectProperty<LocalDate> derniereGenerationProperty()       { return derniereGeneration; }

    // --- idSource ---
    public Integer getIdSource()                { return idSource; }
    public void setIdSource(Integer v)          { idSource = v; }

    /** Texte affiché dans la colonne « Fréquence » du tableau. */
    public String getLibelleFrequence() {
        return idSource != null ? "Automatique" : getFrequence().getLibelle();
    }
}
