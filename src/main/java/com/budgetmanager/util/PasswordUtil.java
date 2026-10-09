package com.budgetmanager.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Locale;

/**
 * Hachage des mots de passe et des réponses de sécurité, 100% JDK
 * (PBKDF2-HMAC-SHA256 de {@code javax.crypto}, aucune bibliothèque tierce).
 *
 * Format stocké : {@code pbkdf2$<itérations>$<sel base64>$<hash base64>}.
 *
 * Compatibilité : les comptes créés avant l'introduction du hachage ont un
 * mot de passe en clair. {@link #verifier} sait encore les comparer ; le
 * contrôleur de connexion les convertit en hash à la première connexion
 * réussie (voir {@link #estHache}).
 */
public final class PasswordUtil {

    private static final String PREFIXE = "pbkdf2";
    /** Itérations PBKDF2 des nouveaux mots de passe (les anciens hachages restent valides : leur nombre est stocké avec eux). */
    private static final int ITERATIONS = 310_000;

    /** Longueur minimale d'un mot de passe, partagée par l'inscription, la récupération et les paramètres. */
    public static final int LONGUEUR_MIN = 8;
    private static final int TAILLE_SEL = 16;
    private static final int TAILLE_CLE_BITS = 256;
    private static final SecureRandom ALEATOIRE = new SecureRandom();

    private PasswordUtil() {}

    /** Retourne le hash salé de {@code secret}, prêt à être stocké en base. */
    public static String hacher(String secret) {
        byte[] sel = new byte[TAILLE_SEL];
        ALEATOIRE.nextBytes(sel);
        byte[] hash = deriver(secret, sel, ITERATIONS);
        return PREFIXE + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(sel) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    /** Vérifie {@code secret} contre la valeur stockée (hash PBKDF2, ou ancien mot de passe en clair). */
    public static boolean verifier(String secret, String stocke) {
        if (secret == null || stocke == null) return false;

        if (!estHache(stocke)) {
            // Ancien format (clair) : comparaison à temps constant.
            return MessageDigest.isEqual(
                    secret.getBytes(StandardCharsets.UTF_8),
                    stocke.getBytes(StandardCharsets.UTF_8));
        }

        String[] parties = stocke.split("\\$");
        if (parties.length != 4) return false;
        try {
            int iterations = Integer.parseInt(parties[1]);
            byte[] sel = Base64.getDecoder().decode(parties[2]);
            byte[] attendu = Base64.getDecoder().decode(parties[3]);
            byte[] calcule = deriver(secret, sel, iterations);
            return MessageDigest.isEqual(attendu, calcule);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Vrai si la valeur stockée est déjà un hash PBKDF2 (et non un ancien mot de passe en clair). */
    /** Vrai si le mot de passe stocké est en clair ou haché avec moins d'itérations qu'aujourd'hui : à re-hacher après une connexion réussie. */
    public static boolean doitEtreRehache(String stocke) {
        if (!estHache(stocke)) return true;
        String[] parties = stocke.split("\\$");
        try {
            return parties.length < 2 || Integer.parseInt(parties[1]) < ITERATIONS;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    public static boolean estHache(String stocke) {
        return stocke != null && stocke.startsWith(PREFIXE + "$");
    }

    /* ----------------------- Réponses de sécurité ----------------------- */

    /**
     * Normalise une réponse de sécurité pour que « Antananarivo », « antananarivo »
     * ou « Antananarivo  » soient équivalents : minuscules, sans accents,
     * espaces superflus supprimés.
     */
    public static String normaliserReponse(String reponse) {
        if (reponse == null) return "";
        String sansAccents = Normalizer.normalize(reponse, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sansAccents.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    public static String hacherReponse(String reponse) {
        return hacher(normaliserReponse(reponse));
    }

    public static boolean verifierReponse(String reponse, String hashStocke) {
        return verifier(normaliserReponse(reponse), hashStocke);
    }

    /* ------------------------------ Interne ------------------------------ */

    private static byte[] deriver(String secret, byte[] sel, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), sel, iterations, TAILLE_CLE_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 indisponible sur cette JVM.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
