package com.budgetmanager.model;

import javafx.beans.property.*;

import java.time.LocalDateTime;

/**
 * Modèle d'un utilisateur avec JavaFX Properties.
 * Les écrans Paramètres peuvent se lier directement aux propriétés
 * pour que les champs texte reflètent instantanément les modifications.
 */
public class User {

    private final IntegerProperty idUser            = new SimpleIntegerProperty();
    private final StringProperty  nom               = new SimpleStringProperty();
    private final StringProperty  prenom            = new SimpleStringProperty();
    private final StringProperty  email             = new SimpleStringProperty();
    private final StringProperty  pseudo            = new SimpleStringProperty();
    private final StringProperty  motDePasse        = new SimpleStringProperty();
    private final StringProperty  devise            = new SimpleStringProperty();
    private final StringProperty  questionSecurite  = new SimpleStringProperty();
    private final StringProperty  reponseSecurite   = new SimpleStringProperty();
    private final ObjectProperty<LocalDateTime> dateCreation = new SimpleObjectProperty<>();

    public User() {}

    public User(int idUser, String nom, String prenom, String email,
                String motDePasse, String devise, LocalDateTime dateCreation) {
        setIdUser(idUser);
        setNom(nom);
        setPrenom(prenom);
        setEmail(email);
        setMotDePasse(motDePasse);
        setDevise(devise);
        setDateCreation(dateCreation);
    }

    public User(String nom, String prenom, String email, String motDePasse, String devise) {
        setNom(nom);
        setPrenom(prenom);
        setEmail(email);
        setMotDePasse(motDePasse);
        setDevise(devise);
    }

    // --- idUser ---
    public int getIdUser()                      { return idUser.get(); }
    public void setIdUser(int v)                { idUser.set(v); }
    public IntegerProperty idUserProperty()     { return idUser; }

    // --- nom ---
    public String getNom()                      { return nom.get(); }
    public void setNom(String v)                { nom.set(v); }
    public StringProperty nomProperty()         { return nom; }

    // --- prenom ---
    public String getPrenom()                   { return prenom.get(); }
    public void setPrenom(String v)             { prenom.set(v); }
    public StringProperty prenomProperty()      { return prenom; }

    // --- email ---
    public String getEmail()                    { return email.get(); }
    public void setEmail(String v)              { email.set(v); }
    public StringProperty emailProperty()       { return email; }

    // --- pseudo ---
    public String getPseudo()                   { return pseudo.get(); }
    public void setPseudo(String v)             { pseudo.set(v); }
    public StringProperty pseudoProperty()      { return pseudo; }

    // --- motDePasse ---
    public String getMotDePasse()               { return motDePasse.get(); }
    public void setMotDePasse(String v)         { motDePasse.set(v); }
    public StringProperty motDePasseProperty()  { return motDePasse; }

    // --- devise ---
    public String getDevise()                   { return devise.get(); }
    public void setDevise(String v)             { devise.set(v); }
    public StringProperty deviseProperty()      { return devise; }

    // --- questionSecurite ---
    public String getQuestionSecurite()             { return questionSecurite.get(); }
    public void setQuestionSecurite(String v)       { questionSecurite.set(v); }
    public StringProperty questionSecuriteProperty(){ return questionSecurite; }

    // --- reponseSecurite ---
    public String getReponseSecurite()              { return reponseSecurite.get(); }
    public void setReponseSecurite(String v)        { reponseSecurite.set(v); }
    public StringProperty reponseSecuriteProperty() { return reponseSecurite; }

    // --- dateCreation ---
    public LocalDateTime getDateCreation()                          { return dateCreation.get(); }
    public void setDateCreation(LocalDateTime v)                    { dateCreation.set(v); }
    public ObjectProperty<LocalDateTime> dateCreationProperty()     { return dateCreation; }

    // --- Méthodes utilitaires (inchangées) ---
    public boolean aUneQuestionSecurite() {
        String q = getQuestionSecurite(), r = getReponseSecurite();
        return q != null && !q.isBlank() && r != null && !r.isBlank();
    }

    public String getNomComplet() {
        return getPrenom() + " " + getNom();
    }
}
