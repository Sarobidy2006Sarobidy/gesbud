package com.budgetmanager.util;

import java.util.regex.Pattern;

/**
 * Noms et prénoms d'un compte : des lettres, pas un code (« 123 ») ni des signes (« @@@ »).
 */
public final class NomPersonne {

    /** Au moins une lettre ; le reste peut être espace, tiret ou apostrophe. */
    private static final Pattern NOM = Pattern.compile(
            "^[\\p{L}][\\p{L} .'\\-]{0,48}$");

    private NomPersonne() {}

    /**
     * {@code null} si le nom est acceptable, sinon le message à afficher.
     *
     * @param champ « nom » ou « prénom », pour le message
     */
    public static String valider(String valeur, String champ) {
        if (valeur == null || valeur.isBlank()) {
            return "Le " + champ + " est obligatoire.";
        }
        String texte = valeur.trim();
        if (!NOM.matcher(texte).matches()) {
            return "Le " + champ + " « " + texte + " » n'est pas un nom : "
                    + "utilisez des lettres (ex. Rakoto), pas des chiffres ni des signes.";
        }
        return null;
    }

    /** Un nom de catégorie : au moins une lettre, pour refuser « 123 » et « @@@ ». */
    public static String validerCategorie(String nom) {
        if (nom == null || nom.isBlank()) {
            return "Une catégorie a besoin d'un nom.";
        }
        if (!nom.chars().anyMatch(Character::isLetter)) {
            return "Le nom « " + nom.trim()
                    + " » doit contenir au moins une lettre. « 123 » ou « @@@ » ne conviennent pas.";
        }
        return null;
    }
}
