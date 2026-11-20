package com.bloxbean.intelliada.idea.joust.trust;

import com.bloxbean.intelliada.idea.joust.lsp.JoustServerControl;
import com.intellij.ide.trustedProjects.TrustedProjectsListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * ADR-004 D1 and D5 (I4, I20): trust granted or revoked mid-session restarts the project's JOUST language server. In M1
 * the server is advisory either way; the restart is the mechanism later milestones rely on.
 */
public final class JoustTrustListener implements TrustedProjectsListener {

    @Override
    public void onProjectTrusted(@NotNull Project project) {
        JoustServerControl.restart(project, "the project became trusted");
    }

    @Override
    public void onProjectUntrusted(@NotNull Project project) {
        JoustServerControl.restart(project, "the project is no longer trusted");
    }
}
