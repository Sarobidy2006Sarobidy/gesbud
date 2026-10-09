package com.budgetmanager.util;

/**
 * Message à afficher une seule fois sur l'écran suivant
 * (ex. « Mot de passe modifié » affiché sur la page de connexion).
 */
public final class FlashMessage {

    private static String message;

    private FlashMessage() {}

    public static void definir(String texte) {
        message = texte;
    }

    /** Retourne le message en attente (ou {@code null}) et l'efface. */
    public static String consommer() {
        String courant = message;
        message = null;
        return courant;
    }
}
