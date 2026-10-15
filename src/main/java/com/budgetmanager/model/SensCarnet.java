package com.budgetmanager.model;

/** Le sens d'une écriture du carnet : l'argent est entré chez moi, ou en est sorti. */
public enum SensCarnet {

    EMPRUNT("Je dois"),
    PRET("On me doit");

    private final String libelle;

    SensCarnet(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() { return libelle; }

    @Override
    public String toString() { return libelle; }

    /** Convertit la valeur stockée en base ; EMPRUNT si absente ou inconnue. */
    public static SensCarnet depuisBase(String valeur) {
        if (valeur == null) return EMPRUNT;
        try {
            return valueOf(valeur);
        } catch (IllegalArgumentException e) {
            return EMPRUNT;
        }
    }
}
