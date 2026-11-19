package com.bloxbean.intelliada.idea.joust.tasks;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ADR-004 I20: every JOUST task IntelliAda starts for a project (a Gradle task, the project's npm CLI) is registered here.
 * On loss of trust, and when the project closes, all are cancelled and their late results discarded (a generation
 * counter: a result from an older generation is ignored). IntelliJ's shared Gradle daemon is not IntelliAda's.
 */
@Service(Service.Level.PROJECT)
public final class JoustTaskTracker implements Disposable {

    /** A running task. */
    public interface Handle {
        void cancel();
    }

    /**
     * A task's handle, registered <b>before</b> anything starts (M2-CDX-3). A cancellation that arrives before the
     * real process or Gradle task exists is remembered, and each canceller attached later runs at once.
     */
    public static final class Run implements Handle {
        private volatile boolean cancelled;
        private final java.util.List<Runnable> cancellers = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void cancel() {
            cancelled = true;
            for (Runnable r : cancellers) runQuietly(r);
        }

        /** Adds what cancels the real work; runs it now if this run is already cancelled. */
        public void attach(Runnable canceller) {
            cancellers.add(canceller);
            if (cancelled) runQuietly(canceller);
        }

        public boolean cancelled() {
            return cancelled;
        }

        private static void runQuietly(Runnable r) {
            try {
                r.run();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
    }

    /**
     * Registers a run for {@code guard}, then checks the guard: null (nothing may start) when it is no longer live.
     * Registering first means a cancellation between the check and the start still reaches the run.
     */
    public Run start(JoustRunGuard guard) {
        Run r = new Run();
        register(r);
        if (!guard.live()) {
            r.cancel();
            done(r);
            return null;
        }
        return r;
    }

    private final Set<Handle> running = ConcurrentHashMap.newKeySet();
    private final AtomicInteger generation = new AtomicInteger();

    public static JoustTaskTracker getInstance(Project project) {
        return project.getService(JoustTaskTracker.class);
    }

    public int generation() {
        return generation.get();
    }

    /** True when results of a task started in generation {@code g} may still be used. */
    public boolean current(int g) {
        return g == generation.get();
    }

    public void register(Handle h) {
        running.add(h);
    }

    public void done(Handle h) {
        running.remove(h);
    }

    public int runningCount() {
        return running.size();
    }

    /** Cancels everything running and discards late results. */
    public void cancelAll() {
        generation.incrementAndGet();
        for (Handle h : Set.copyOf(running)) {
            try {
                h.cancel();
            } catch (RuntimeException ignored) {
                // best effort
            }
            running.remove(h);
        }
    }

    @Override
    public void dispose() {
        cancelAll();
    }
}
