package com.budgetmanager.util;

import com.budgetmanager.model.User;

public class SessionManager {

    private static User utilisateurConnecte;

    private SessionManager() {}

    public static void connecter(User user) {
        utilisateurConnecte = user;
    }

    public static User getUtilisateurConnecte() {
        return utilisateurConnecte;
    }

    public static void deconnecter() {
        utilisateurConnecte = null;
    }

    public static boolean estConnecte() {
        return utilisateurConnecte != null;
    }
}
