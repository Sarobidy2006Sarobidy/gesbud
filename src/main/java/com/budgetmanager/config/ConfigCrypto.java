package com.budgetmanager.config;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Chiffrement AES-128-GCM des valeurs sensibles de database.properties.
 * Une valeur chiffrée a la forme « aes:BASE64(sel|iv|texte) » ; les valeurs
 * en clair restent acceptées telles quelles (adoption progressive).
 * La clé n'est jamais stockée sur disque : elle est dérivée (PBKDF2) au
 * lancement depuis la variable d'environnement BUDGET_DB_KEY. Le fichier de
 * configuration peut donc être versionné ou extrait du .jar décompilé sans
 * exposer le mot de passe — le secret reste dans l'environnement de la machine.
 */
public final class ConfigCrypto {

    private ConfigCrypto() {}

    private static final String PREFIXE = "aes:";
    private static final String VARIABLE_CLE = "BUDGET_DB_KEY";
    private static final int ITERATIONS_PBKDF2 = 210_000;
    private static final int TAILLE_CLE_BITS = 128; // AES-128, tag GCM de 128 bits
    private static final int TAILLE_SEL = 16;       // octets
    private static final int TAILLE_IV = 12;        // octets, taille recommandée en GCM

    /** {@code true} si la valeur porte le préfixe « aes: » et doit être déchiffrée. */
    public static boolean estChiffre(String valeur) {
        return valeur != null && valeur.startsWith(PREFIXE);
    }

    /**
     * Retourne la valeur utilisable : déchiffrée si elle porte le préfixe
     * « aes: », telle quelle sinon. Échoue franchement (jamais de repli
     * silencieux vers un mot de passe vide) si la clé est absente ou fausse.
     */
    public static String dechiffrer(String valeur) {
        if (!estChiffre(valeur)) return valeur;
        try {
            byte[] charge = Base64.getDecoder().decode(valeur.substring(PREFIXE.length()));
            if (charge.length <= TAILLE_SEL + TAILLE_IV) {
                throw new IllegalStateException("Valeur « aes: » trop courte dans la configuration.");
            }
            byte[] sel = Arrays.copyOfRange(charge, 0, TAILLE_SEL);
            byte[] iv = Arrays.copyOfRange(charge, TAILLE_SEL, TAILLE_SEL + TAILLE_IV);
            byte[] chiffre = Arrays.copyOfRange(charge, TAILLE_SEL + TAILLE_IV, charge.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriverCle(sel), new GCMParameterSpec(TAILLE_CLE_BITS, iv));
            return new String(cipher.doFinal(chiffre), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Déchiffrement de la configuration impossible : clé " + VARIABLE_CLE
                    + " incorrecte ou valeur « aes: » corrompue.", e);
        }
    }

    /** Chiffre une valeur en « aes:... » ; sel et IV neufs à chaque appel. */
    public static String chiffrer(String clair) {
        SecureRandom alea = new SecureRandom();
        byte[] sel = new byte[TAILLE_SEL];
        byte[] iv = new byte[TAILLE_IV];
        alea.nextBytes(sel);
        alea.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriverCle(sel), new GCMParameterSpec(TAILLE_CLE_BITS, iv));
            byte[] chiffre = cipher.doFinal(clair.getBytes(StandardCharsets.UTF_8));
            byte[] charge = new byte[TAILLE_SEL + TAILLE_IV + chiffre.length];
            System.arraycopy(sel, 0, charge, 0, TAILLE_SEL);
            System.arraycopy(iv, 0, charge, TAILLE_SEL, TAILLE_IV);
            System.arraycopy(chiffre, 0, charge, TAILLE_SEL + TAILLE_IV, chiffre.length);
            return PREFIXE + Base64.getEncoder().encodeToString(charge);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement de la configuration impossible.", e);
        }
    }

    private static SecretKey deriverCle(byte[] sel) throws GeneralSecurityException {
        String phrase = System.getenv(VARIABLE_CLE);
        if (phrase == null || phrase.isBlank()) {
            throw new IllegalStateException("La configuration contient une valeur chiffrée (aes:...) mais la variable "
                    + "d'environnement " + VARIABLE_CLE + ", qui porte la phrase secrète, n'est pas définie.");
        }
        SecretKeyFactory fabrique = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(phrase.toCharArray(), sel, ITERATIONS_PBKDF2, TAILLE_CLE_BITS);
        return new SecretKeySpec(fabrique.generateSecret(spec).getEncoded(), "AES");
    }

    /**
     * Outil manuel : avec BUDGET_DB_KEY définie, imprime la forme chiffrée
     * d'une valeur à coller dans database.properties.
     * <pre>java -cp target/classes com.budgetmanager.config.ConfigCrypto "motDePasse"</pre>
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage : définir la variable BUDGET_DB_KEY, puis");
            System.err.println("  java -cp target/classes com.budgetmanager.config.ConfigCrypto \"valeur à chiffrer\"");
            System.err.println("Coller ensuite le résultat (aes:...) après db.password= dans database.properties.");
            System.exit(1);
        }
        System.out.println(chiffrer(args[0]));
    }
}
