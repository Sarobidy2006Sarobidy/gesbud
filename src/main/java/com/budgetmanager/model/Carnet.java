package com.budgetmanager.model;

import javafx.beans.property.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Une écriture du carnet d'emprunts et de prêts. Le modèle est volontairement
 * sans lien avec les revenus, les dépenses et les économies : il note une
 * situation, il ne déplace aucun argent.
 */
public class Carnet {

    private final IntegerProperty idCarnet           = new SimpleIntegerProperty();
    private final IntegerProperty idUser             = new SimpleIntegerProperty();
    private final ObjectProperty<SensCarnet>   sens       = new SimpleObjectProperty<>(SensCarnet.EMPRUNT);
    private final StringProperty  personne           = new SimpleStringProperty("");
    private final StringProperty  motif              = new SimpleStringProperty("");
    private final DoubleProperty  montant            = new SimpleDoubleProperty();
    private final ObjectProperty<LocalDate>     dateDebut       = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate>     dateFin         = new SimpleObjectProperty<>();
    private final ObjectProperty<StatutCarnet>  statut          = new SimpleObjectProperty<>(StatutCarnet.EN_COURS);
    private final ObjectProperty<LocalDateTime> dateCreation    = new SimpleObjectProperty<>();

    public Carnet() {}

    public Carnet(int idUser, SensCarnet sens, String personne, String motif,
                  double montant, LocalDate dateDebut) {
        setIdUser(idUser);
        setSens(sens);
        setPersonne(personne);
        setMotif(motif);
        setMontant(montant);
        setDateDebut(dateDebut);
    }

    // --- idCarnet ---
    public int getIdCarnet()                  { return idCarnet.get(); }
    public void setIdCarnet(int v)            { idCarnet.set(v); }
    public IntegerProperty idCarnetProperty() { return idCarnet; }

    // --- idUser ---
    public int getIdUser()                  { return idUser.get(); }
    public void setIdUser(int v)            { idUser.set(v); }
    public IntegerProperty idUserProperty() { return idUser; }

    // --- sens ---
    public SensCarnet getSens()                  { return sens.get(); }
    public void setSens(SensCarnet v)            { sens.set(v); }
    public ObjectProperty<SensCarnet> sensProperty() { return sens; }

    // --- personne ---
    public String getPersonne()                { return personne.get(); }
    public void setPersonne(String v)          { personne.set(v == null ? "" : v); }
    public StringProperty personneProperty()   { return personne; }

    // --- motif ---
    public String getMotif()                   { return motif.get(); }
    public void setMotif(String v)             { motif.set(v == null ? "" : v); }
    public StringProperty motifProperty()      { return motif; }

    // --- montant ---
    public double getMontant()                 { return montant.get(); }
    public void setMontant(double v)           { montant.set(v); }
    public DoubleProperty montantProperty()    { return montant; }

    // --- dateDebut ---
    public LocalDate getDateDebut()                    { return dateDebut.get(); }
    public void setDateDebut(LocalDate v)              { dateDebut.set(v); }
    public ObjectProperty<LocalDate> dateDebutProperty() { return dateDebut; }

    // --- dateFin ---
    public LocalDate getDateFin()                      { return dateFin.get(); }
    public void setDateFin(LocalDate v)                { dateFin.set(v); }
    public ObjectProperty<LocalDate> dateFinProperty() { return dateFin; }

    // --- statut ---
    public StatutCarnet getStatut()                    { return statut.get(); }
    public void setStatut(StatutCarnet v)              { statut.set(v); }
    public ObjectProperty<StatutCarnet> statutProperty() { return statut; }

    // --- dateCreation ---
    public LocalDateTime getDateCreation()             { return dateCreation.get(); }
    public void setDateCreation(LocalDateTime v)       { dateCreation.set(v); }
    public ObjectProperty<LocalDateTime> dateCreationProperty() { return dateCreation; }
}
