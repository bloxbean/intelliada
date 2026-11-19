package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.core.JoustConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.redhat.devtools.lsp4ij.LanguageServerManager;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.ServerStatus;

/**
 * Restarts JOUST language servers through LSP4IJ's {@link LanguageServerManager} (ADR-004 D5, I4, I20): on a trust
 * change, a settings change, or the user's request. The server ignores {@code joust/trust} from clients other than VS
 * Code (F15), so a restart is how a mode change reaches it.
 */
public final class JoustServerControl {

    private static final Logger LOG = Logger.getInstance(JoustServerControl.class);

    private JoustServerControl() {
    }

    public static void restartAll(String reason) {
        for (Project p : ProjectManager.getInstance().getOpenProjects()) restart(p, reason);
    }

    /**
     * Restarts the project's JOUST server if it is running: a new process with a newly planned command line. A stopped
     * server stays stopped; it starts again when a .jsc file is opened.
     */
    public static void restart(Project project, String reason) {
        if (project.isDisposed()) return;
        for (LanguageServerWrapper w : LanguageServiceAccessor.getInstance(project).getStartedServers()) {
            if (JoustConstants.SERVER_ID.equals(w.getServerDefinition().getId())) {
                LOG.info("JOUST language server restart: " + reason);
                w.restart();
            }
        }
    }

    /** The server's current status in the project. */
    public static ServerStatus status(Project project) {
        return LanguageServerManager.getInstance(project).getServerStatus(JoustConstants.SERVER_ID);
    }

    public static void stopAll(String reason) {
        for (Project p : ProjectManager.getInstance().getOpenProjects()) {
            if (p.isDisposed()) continue;
            LOG.info("JOUST language server stop (" + reason + ")");
            LanguageServerManager.getInstance(p).stop(JoustConstants.SERVER_ID);
        }
    }
}
