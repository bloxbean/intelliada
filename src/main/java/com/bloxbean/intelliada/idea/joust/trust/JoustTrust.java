package com.bloxbean.intelliada.idea.joust.trust;

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.project.Project;

/**
 * ADR-004 D1: IntelliAda's single trust check, the <b>public</b> {@code TrustedProjects.isProjectTrusted(Project)} only,
 * never the internal {@code isTrustedCheckDisabled()} (F13).
 */
public final class JoustTrust {
    private JoustTrust() {
    }

    public static boolean isTrusted(Project project) {
        return project != null && !project.isDisposed() && TrustedProjects.isProjectTrusted(project);
    }
}
