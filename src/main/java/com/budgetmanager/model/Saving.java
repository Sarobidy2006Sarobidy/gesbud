package com.budgetmanager.model;

import javafx.beans.property.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Modèle d'une économie avec JavaFX Properties.
 */
public class Saving {

    private final IntegerProperty idEconomie        = new SimpleIntegerProperty();
    private final IntegerProperty idUser            = new SimpleIntegerProperty();
    private final DoubleProperty  montant           = new SimpleDoubleProperty();
    private final StringProperty  description       = new SimpleStringProperty();
    private final ObjectProperty<LocalDate>     dateEconomie        = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> dateCreation        = new SimpleObjectProperty<>();
    private final ObjectProperty<Frequency>     frequence           = new SimpleObjectProperty<>(Frequency.PONCTUEL);
    private final ObjectProperty<LocalDate>     dateFin             = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate>     derniereGeneration  = new SimpleObjectProperty<>();
    private Integer idSource;

    public Saving() {}

    public Saving(int idUser, double montant, String description, LocalDate dateEconomie) {
        setIdUser(idUser);
        setMontant(montant);
        setDescription(description);
        setDateEconomie(dateEconomie);
    }

    // --- idEconomie ---
    public int getIdEconomie()                  { return idEconomie.get(); }
    public void setIdEconomie(int v)            { idEconomie.set(v); }
    public IntegerProperty idEconomieProperty() { return idEconomie; }

    // --- idUser ---
    public int getIdUser()                      { return idUser.get(); }
    public void setIdUser(int v)                { idUser.set(v); }
    public IntegerProperty idUserProperty()     { return idUser; }

    // --- montant ---
    public double getMontant()                  { return montant.get(); }
    public void setMontant(double v)            { montant.set(v); }
    public DoubleProperty montantProperty()     { return montant; }

    // --- description ---
    public String getDescription()              { return description.get(); }
    public void setDescription(String v)        { description.set(v); }
    public StringProperty descriptionProperty() { return description; }

    // --- dateEconomie ---
    public LocalDate getDateEconomie()                          { return dateEconomie.get(); }
    public void setDateEconomie(LocalDate v)                    { dateEconomie.set(v); }
    public ObjectProperty<LocalDate> dateEconomieProperty()     { return dateEconomie; }

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
