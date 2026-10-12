package com.budgetmanager.util;

import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exécuteur unique et partagé pour toutes les tâches de base de données.
 *
 * <p>Pourquoi un SingleThreadExecutor ?
 * <ul>
 *   <li>Toutes les requêtes SQL s'exécutent dans UN seul thread dédié,
 *       l'une après l'autre — impossible d'avoir deux écritures simultanées.</li>
 *   <li>Élimine les deadlocks et corruptions de données causés par des clics
 *       rapides (double-clic sur "Ajouter", synchronisation concurrente).</li>
 *   <li>L'UI JavaFX reste toujours fluide : le thread principal n'est jamais
 *       bloqué, les résultats arrivent via setOnSucceeded().</li>
 * </ul>
 *
 * <p>Utilisation :
 * <pre>
 *   Task&lt;List&lt;Income&gt;&gt; tache = new Task&lt;&gt;() { ... };
 *   DbExecutor.soumettre(tache);
 * </pre>
 */
public final class DbExecutor {

    /** Thread unique nommé, daemon pour ne pas bloquer la JVM à la fermeture. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "db-worker");
        t.setDaemon(true);
        return t;
    });

    /** Nombre de tâches actuellement en file ou en cours d'exécution. */
    private static final AtomicInteger TACHES_ACTIVES = new AtomicInteger(0);

    private DbExecutor() {}

    /**
     * Soumet une Task à l'exécuteur unique.
     * La Task sera exécutée après toutes celles déjà en attente.
     *
     * <p>Les handlers posés par le contrôleur juste avant l'appel
     * (navigation, rafraîchissement des labels) sont <b>chaînés</b> et non
     * remplacés : setOnSucceeded écrase le handler précédent, et sans ce
     * chaînage la vue ne réagissait jamais à la fin de la tâche (bouton
     * bloqué, app figée).</p>
     */
    public static void soumettre(Task<?> tache) {
        TACHES_ACTIVES.incrementAndGet();
        EventHandler<WorkerStateEvent> succesExistant = tache.getOnSucceeded();
        EventHandler<WorkerStateEvent> echecExistant  = tache.getOnFailed();
        EventHandler<WorkerStateEvent> annulExistant  = tache.getOnCancelled();
        tache.setOnSucceeded(e -> {
            TACHES_ACTIVES.decrementAndGet();
            if (succesExistant != null) succesExistant.handle(e);
        });
        tache.setOnFailed(e -> {
            TACHES_ACTIVES.decrementAndGet();
            if (echecExistant != null) echecExistant.handle(e);
        });
        tache.setOnCancelled(e -> {
            TACHES_ACTIVES.decrementAndGet();
            if (annulExistant != null) annulExistant.handle(e);
        });
        EXECUTOR.submit(tache);
    }

    /**
     * Vrai si au moins une tâche est en cours ou en attente.
     * Utilisé par Main pour bloquer la fermeture proprement.
     */
    public static boolean aTachesActives() {
        return TACHES_ACTIVES.get() > 0;
    }

    /**
     * Arrête proprement l'exécuteur.
     * Attend au maximum 5 secondes que les tâches en cours se terminent.
     * Appelé depuis Main.stop().
     */
    public static void arreter() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
