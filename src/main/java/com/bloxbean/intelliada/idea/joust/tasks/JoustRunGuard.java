package com.bloxbean.intelliada.idea.joust.tasks;

import com.bloxbean.intelliada.idea.joust.trust.JoustTrust;
import com.intellij.openapi.project.Project;

/**
 * ADR-004 D1, I2, I20: what a user action is bound to. The guard is captured when the user invokes the action (on the
 * EDT, with the project trusted). It is live while the project is open, trusted, and still in the same
 * {@link JoustTaskTracker} generation: revoking trust or closing the project ends it, and granting trust again does
 * not revive it (M2-CDX-2). Every launch and every continuation after a dialog checks it.
 */
public record JoustRunGuard(Project project, int generation) {

    /** The guard for an action invoked now. */
    public static JoustRunGuard capture(Project project) {
        return new JoustRunGuard(project, JoustTaskTracker.getInstance(project).generation());
    }

    public boolean live() {
        return project != null && !project.isDisposed() && JoustTrust.isTrusted(project)
                && JoustTaskTracker.getInstance(project).current(generation);
    }
}
