package com.budgetmanager.model;

/** Ce que l'utilisateur affirme du dossier : rien n'est calculé, c'est lui qui coche. */
public enum StatutCarnet {

    EN_COURS("En cours"),
    REGLE("Réglé");

    private final String libelle;

    StatutCarnet(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() { return libelle; }

    @Override
    public String toString() { return libelle; }

    /** Convertit la valeur stockée en base ; EN_COURS si absente ou inconnue. */
    public static StatutCarnet depuisBase(String valeur) {
        if (valeur == null) return EN_COURS;
        try {
            return valueOf(valeur);
        } catch (IllegalArgumentException e) {
            return EN_COURS;
        }
    }
}
