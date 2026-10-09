package com.budgetmanager.model;

import javafx.beans.property.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Modèle d'un revenu avec JavaFX Properties.
 * Les colonnes de TableView se lient directement aux propriétés via
 * {@code cellData -> cellData.getValue().montantProperty()} sans réflexion.
 */
public class Income {

    private final IntegerProperty idRevenu          = new SimpleIntegerProperty();
    private final IntegerProperty idUser            = new SimpleIntegerProperty();
    private final IntegerProperty idCategorie       = new SimpleIntegerProperty();
    private final StringProperty  nomCategorie      = new SimpleStringProperty();
    private final DoubleProperty  montant           = new SimpleDoubleProperty();
    private final StringProperty  description       = new SimpleStringProperty();
    private final ObjectProperty<LocalDate>     dateRevenu          = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> dateCreation        = new SimpleObjectProperty<>();
    private final ObjectProperty<Frequency>     frequence           = new SimpleObjectProperty<>(Frequency.PONCTUEL);
    private final ObjectProperty<LocalDate>     dateFin             = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate>     derniereGeneration  = new SimpleObjectProperty<>();
    /** Null = ligne source récurrente, non-null = occurrence générée automatiquement. */
    private Integer idSource;

    public Income() {}

    public Income(int idUser, int idCategorie, double montant, String description, LocalDate dateRevenu) {
        setIdUser(idUser);
        setIdCategorie(idCategorie);
        setMontant(montant);
        setDescription(description);
        setDateRevenu(dateRevenu);
    }

    // --- idRevenu ---
    public int getIdRevenu()                    { return idRevenu.get(); }
    public void setIdRevenu(int v)              { idRevenu.set(v); }
    public IntegerProperty idRevenuProperty()   { return idRevenu; }

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

    // --- dateRevenu ---
    public LocalDate getDateRevenu()                        { return dateRevenu.get(); }
    public void setDateRevenu(LocalDate v)                  { dateRevenu.set(v); }
    public ObjectProperty<LocalDate> dateRevenuProperty()   { return dateRevenu; }

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

    // --- idSource (Integer nullable, pas de Property car valeur rare) ---
    public Integer getIdSource()                { return idSource; }
    public void setIdSource(Integer v)          { idSource = v; }

    /** Texte affiché dans la colonne « Fréquence » du tableau. */
    public String getLibelleFrequence() {
        return idSource != null ? "Automatique" : getFrequence().getLibelle();
    }
}
