package com.budgetmanager.util;

import com.budgetmanager.model.Saving;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calcule les points de la courbe d'économies : le total cumulé, regroupé
 * par jour, par semaine ou par mois. Les périodes sans économie sont
 * incluses pour que la courbe reste continue.
 */
public final class SavingsCurve {

    public enum Period {
        JOUR("Par jour", 60, "dd/MM"),
        SEMAINE("Par semaine", 52, "dd/MM"),
        MOIS("Par mois", 24, "MM/yyyy");

        private final String libelle;
        private final int pointsMax;
        private final DateTimeFormatter format;

        Period(String libelle, int pointsMax, String motif) {
            this.libelle = libelle;
            this.pointsMax = pointsMax;
            this.format = DateTimeFormatter.ofPattern(motif);
        }

        LocalDate debutPeriode(LocalDate date) {
            return switch (this) {
                case JOUR -> date;
                case SEMAINE -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MOIS -> date.withDayOfMonth(1);
            };
        }

        LocalDate suivante(LocalDate debut) {
            return switch (this) {
                case JOUR -> debut.plusDays(1);
                case SEMAINE -> debut.plusWeeks(1);
                case MOIS -> debut.plusMonths(1);
            };
        }

        @Override
        public String toString() { return libelle; }
    }

    /** Un point de la courbe : libellé de la période et total cumulé à la fin de celle-ci. */
    public record Point(String libelle, double cumul) {}

    private SavingsCurve() {}

    /**
     * @param economies les économies de l'utilisateur (ordre quelconque)
     * @param periode   le regroupement choisi
     * @param aujourdhui la date du jour ; ce qui est daté après reste hors du cumul
     * @return les derniers points de la courbe (le cumul inclut tout ce qui précède)
     */
    public static List<Point> construire(List<Saving> economies, Period periode, LocalDate aujourdhui) {
        List<Point> points = new ArrayList<>();
        if (economies == null || economies.isEmpty()) return points;

        Map<LocalDate, Double> sommes = new TreeMap<>();
        LocalDate plusAncienne = null;
        LocalDate plusRecente = aujourdhui;
        for (Saving e : economies) {
            // Une économie datée après aujourd'hui est « prévue » : elle reste au tableau
            // mais ne doit pas gonfler le cumul, comme partout ailleurs dans l'application.
            if (e.getDateEconomie().isAfter(aujourdhui)) continue;
            LocalDate debut = periode.debutPeriode(e.getDateEconomie());
            sommes.merge(debut, e.getMontant(), Double::sum);
            if (plusAncienne == null || e.getDateEconomie().isBefore(plusAncienne)) plusAncienne = e.getDateEconomie();
            if (e.getDateEconomie().isAfter(plusRecente)) plusRecente = e.getDateEconomie();
        }
        if (sommes.isEmpty()) return points;

        LocalDate fin = periode.debutPeriode(plusRecente);
        double cumul = 0;
        for (LocalDate p = periode.debutPeriode(plusAncienne); !p.isAfter(fin); p = periode.suivante(p)) {
            cumul += sommes.getOrDefault(p, 0.0);
            points.add(new Point(p.format(periode.format), cumul));
        }

        int trop = points.size() - periode.pointsMax;
        return trop > 0 ? new ArrayList<>(points.subList(trop, points.size())) : points;
    }
}
