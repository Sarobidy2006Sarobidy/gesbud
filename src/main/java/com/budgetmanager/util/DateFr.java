package com.budgetmanager.util;

import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Dates affichées et saisies au format français {@code jj/mm/aaaa}.
 * Sans ça, JavaFX reprend la locale Windows : 19 octobre 2026 devient 10/19/2026.
 */
public final class DateFr {

    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DateFr() {}

    public static String formater(LocalDate date) {
        return date == null ? "" : FORMAT.format(date);
    }

    public static void appliquer(DatePicker... champs) {
        StringConverter<LocalDate> convertisseur = new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return formater(date);
            }

            @Override
            public LocalDate fromString(String texte) {
                if (texte == null || texte.isBlank()) return null;
                try {
                    return LocalDate.parse(texte.trim(), FORMAT);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        };
        for (DatePicker champ : champs) {
            if (champ == null) continue;
            champ.setConverter(convertisseur);
            champ.setPromptText("jj/mm/aaaa");
        }
    }
}
