package com.budgetmanager.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Écriture d'un CSV : le format est minimal mais conforme RFC 4180, et surtout
 * ouvrable dans Excel en français — séparateur point-virgule, encodage UTF-8
 * avec BOM (sinon Excel relit « Dépense » en « DÃ©pense »).
 *
 * <p>La construction du texte est séparée de l'écriture du fichier : les
 * échappements (virgule, point-virgule, guillemet, saut de ligne) se vérifient
 * sans avoir à ouvrir de dialogue de sauvegarde.
 */
public final class ExportCsv {

    private ExportCsv() {}

    /** Séparateur choisi pour Excel en locale française (la virgule est le décimal). */
    public static final char SEPARATEUR = ';';

    /** Construit le contenu complet : en-tête puis une ligne par opération. */
    public static String construire(List<String[]> lignes) {
        StringBuilder texte = new StringBuilder();
        for (String[] ligne : lignes) {
            ecrireLigne(texte, ligne);
        }
        return texte.toString();
    }

    private static void ecrireLigne(StringBuilder texte, String[] valeurs) {
        for (int i = 0; i < valeurs.length; i++) {
            if (i > 0) texte.append(SEPARATEUR);
            echapper(texte, valeurs[i]);
        }
        texte.append("\r\n");
    }

    /** Entoure une valeur qui contient déjà le séparateur, un guillemet ou un saut de ligne. */
    private static void echapper(StringBuilder texte, String valeur) {
        String v = valeur == null ? "" : valeur;
        boolean aEchapper = v.indexOf(SEPARATEUR) >= 0 || v.indexOf('"') >= 0
                || v.indexOf(',') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0
                || v.startsWith(" ") || v.endsWith(" ");
        if (!aEchapper) {
            texte.append(v);
            return;
        }
        texte.append('"').append(v.replace("\"", "\"\"")).append('"');
    }

    /**
     * Écrit le CSV en UTF-8 avec BOM : sans ces trois octets, Excel ouvre le
     * fichier en ANSI et les accents des descriptions deviennent illisibles.
     */
    public static void ecrire(java.io.File fichier, List<String[]> lignes) throws IOException {
        byte[] contenu = construire(lignes).getBytes(StandardCharsets.UTF_8);
        try (java.io.OutputStream sortie = new java.io.FileOutputStream(fichier)) {
            sortie.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            sortie.write(contenu);
        }
    }
}
