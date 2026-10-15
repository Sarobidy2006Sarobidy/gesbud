package com.budgetmanager.model;

/**
 * Type de catégorie : soit pour un revenu, soit pour une dépense. Le nom technique
 * reste celui de la base ({@code name()}, utilisé par les requêtes) ; ce que lit
 * l'utilisateur est le libellé.
 */
public enum CategoryType {
    REVENU("Revenu"),
    DEPENSE("Dépense");

    private final String libelle;

    CategoryType(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() { return libelle; }

    @Override
    public String toString() { return libelle; }
}
