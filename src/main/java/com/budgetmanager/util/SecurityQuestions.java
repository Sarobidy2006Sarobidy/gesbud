package com.budgetmanager.util;

import java.util.List;

/** Questions de sécurité proposées à l'inscription (et dans les paramètres). */
public final class SecurityQuestions {

    private SecurityQuestions() {}

    public static final List<String> QUESTIONS = List.of(
            "Nom de votre premier animal ?",
            "Ville où vous êtes né(e) ?",
            "Prénom de votre ami(e) d'enfance ?",
            "Nom de votre école primaire ?",
            "Votre plat préféré ?",
            "Votre surnom d'enfance ?",
            "Nom de la rue de votre enfance ?"
    );
}
