package com.budgetmanager.util;

import java.util.regex.Pattern;

/**
 * Règles du pseudo (nom d'utilisateur) : 3 à 30 caractères parmi lettres (sans accents),
 * chiffres, point, tiret et tiret bas. Le « @ » est interdit : c'est ce qui permet de
 * distinguer sans ambiguïté un pseudo d'une adresse email à la connexion.
 */
public final class PseudoUtil {

    public static final int LONGUEUR_MIN = 3;
    public static final int LONGUEUR_MAX = 30;
    private static final Pattern MOTIF = Pattern.compile("^[A-Za-z0-9._-]{" + LONGUEUR_MIN + "," + LONGUEUR_MAX + "}$");

    private PseudoUtil() {}

    /** @return null si le pseudo est valide, sinon le message d'erreur à afficher. */
    public static String valider(String pseudo) {
        if (pseudo == null || pseudo.isBlank()) return "Le pseudo est obligatoire.";
        if (!MOTIF.matcher(pseudo).matches()) {
            return "Le pseudo doit contenir de " + LONGUEUR_MIN + " à " + LONGUEUR_MAX
                    + " caractères : lettres, chiffres, point, tiret ou tiret bas (sans espace ni @).";
        }
        return null;
    }
}
