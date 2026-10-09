package com.budgetmanager.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Limite les essais de connexion : après {@code maxEchecs} échecs consécutifs sur un même identifiant,
 * toute nouvelle tentative est refusée pendant {@code duree} millisecondes. Le compteur est en mémoire
 * (il repart à zéro quand l'application se ferme), ce qui suffit à ralentir un essai de mots de passe.
 */
public final class LoginGuard {

    public static final LoginGuard PARTAGE = new LoginGuard(5, 60_000, System::currentTimeMillis);

    private final int maxEchecs;
    private final long duree;
    private final LongSupplier horloge;
    private final Map<String, Integer> echecs = new HashMap<>();
    private final Map<String, Long> bloqueJusqua = new HashMap<>();

    public LoginGuard(int maxEchecs, long duree, LongSupplier horloge) {
        this.maxEchecs = maxEchecs;
        this.duree = duree;
        this.horloge = horloge;
    }

    private static String cle(String identifiant) {
        return identifiant == null ? "" : identifiant.trim().toLowerCase(Locale.ROOT);
    }

    /** @return le nombre de secondes à attendre, ou 0 si la connexion est permise. */
    public synchronized long secondesRestantes(String identifiant) {
        Long fin = bloqueJusqua.get(cle(identifiant));
        if (fin == null) return 0;
        long reste = fin - horloge.getAsLong();
        if (reste <= 0) {
            bloqueJusqua.remove(cle(identifiant));
            echecs.remove(cle(identifiant));
            return 0;
        }
        return (reste + 999) / 1000;
    }

    public synchronized void echec(String identifiant) {
        String k = cle(identifiant);
        int n = echecs.merge(k, 1, Integer::sum);
        if (n >= maxEchecs) bloqueJusqua.put(k, horloge.getAsLong() + duree);
    }

    public synchronized void reussite(String identifiant) {
        echecs.remove(cle(identifiant));
        bloqueJusqua.remove(cle(identifiant));
    }
}
