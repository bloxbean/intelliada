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
