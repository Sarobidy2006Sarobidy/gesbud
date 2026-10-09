package com.budgetmanager.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fréquence d'un revenu : une seule fois, ou récurrent (chaque jour,
 * chaque semaine, chaque mois). Sert aussi à calculer les dates des
 * occurrences d'un revenu récurrent.
 */
public enum Frequency {
    PONCTUEL("Une seule fois"),
    QUOTIDIEN("Chaque jour"),
    HEBDOMADAIRE("Chaque semaine"),
    MENSUEL("Chaque mois");

    /** Garde-fou : nombre maximal d'occurrences générées en une seule fois. */
    private static final int MAX_OCCURRENCES = 20000;

    private final String libelle;

    Frequency(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() { return libelle; }

    public boolean estRecurrent() { return this != PONCTUEL; }

    @Override
    public String toString() { return libelle; }

    /**
     * Date de la n-ième occurrence après la date de début (n >= 1).
     * Calculée depuis le début (et non depuis l'occurrence précédente) pour
     * qu'un revenu mensuel du 31 retombe bien le 31 quand le mois le permet.
     */
    public LocalDate occurrence(LocalDate debut, int n) {
        return switch (this) {
            case QUOTIDIEN -> debut.plusDays(n);
            case HEBDOMADAIRE -> debut.plusWeeks(n);
            case MENSUEL -> debut.plusMonths(n);
            case PONCTUEL -> debut;
        };
    }

    /**
     * Dates des occurrences strictement après {@code apres} et au plus égales
     * à {@code limite} (la date de début elle-même n'est pas incluse : elle
     * correspond au revenu d'origine).
     */
    public List<LocalDate> occurrencesEntre(LocalDate debut, LocalDate apres, LocalDate limite) {
        List<LocalDate> dates = new ArrayList<>();
        if (!estRecurrent() || debut == null || limite == null) return dates;
        for (int n = 1; n <= MAX_OCCURRENCES; n++) {
            LocalDate d = occurrence(debut, n);
            if (d.isAfter(limite)) break;
            if (apres == null || d.isAfter(apres)) dates.add(d);
        }
        return dates;
    }

    /** Première échéance strictement après la date donnée (null pour une opération ponctuelle). */
    public LocalDate prochaineApres(LocalDate debut, LocalDate apres) {
        if (!estRecurrent() || debut == null || apres == null) return null;
        LocalDate d = debut;
        for (int n = 1; n <= MAX_OCCURRENCES; n++) {
            d = occurrence(debut, n);
            if (d.isAfter(apres)) return d;
        }
        return d;
    }

    /** Convertit la valeur stockée en base ; PONCTUEL si absente ou inconnue. */
    public static Frequency depuisBase(String valeur) {
        if (valeur == null) return PONCTUEL;
        try {
            return valueOf(valeur);
        } catch (IllegalArgumentException e) {
            return PONCTUEL;
        }
    }
}
