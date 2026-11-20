package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.core.JoustConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.redhat.devtools.lsp4ij.LanguageServerManager;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.server.LanguageServerException;

/**
 * Restarts and stops JOUST language servers through LSP4IJ's {@link LanguageServerManager} (ADR-004 D5, I4, I20): on a
 * trust change, a settings change, or a conflict found when a project opens. The server ignores {@code joust/trust}
 * from clients other than VS Code (F15), so a restart is how a mode change reaches it.
 *
 * <ul>
 *   <li><b>The user's choice stands</b> (M1-CDX-6): only a server that is running or starting is restarted, and never
 *   with {@code willEnable}, so a server the user disabled or stopped stays so. A stop for a temporary conflict never
 *   uses {@code willDisable}.</li>
 *   <li><b>On the EDT</b> (M1-CDX-19): LSP4IJ clears editor caches when a server stops.</li>
 * </ul>
 */
public final class JoustServerControl {

    private static final Logger LOG = Logger.getInstance(JoustServerControl.class);

    private JoustServerControl() {
    }

    public static void restartAll(String reason) {
        for (Project p : ProjectManager.getInstance().getOpenProjects()) restart(p, reason);
    }

    /** Restarts the project's JOUST server if it is running or starting; a stopped or disabled server is left alone. */
    public static void restart(Project project, String reason) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            ServerStatus status = status(project);
            if (status != ServerStatus.started && status != ServerStatus.starting) return;
            LOG.info("JOUST language server restart: " + reason);
            JoustStatusCache.getInstance(project).clear();
            LanguageServerManager mgr = LanguageServerManager.getInstance(project);
            mgr.stop(JoustConstants.SERVER_ID, new LanguageServerManager.StopOptions().setWillDisable(false));
            try {
                mgr.start(JoustConstants.SERVER_ID, new LanguageServerManager.StartOptions().setWillEnable(false));
            } catch (RuntimeException e) {
                if (!(e instanceof LanguageServerException) && !(e.getCause() instanceof LanguageServerException)) throw e;
                LOG.info("JOUST language server not restarted: " + e.getMessage());
            }
        }, project.getDisposed());
    }

    /** Stops every project's JOUST server without disabling it: it starts again when a .jsc is opened and the conflict is gone. */
    public static void stopAll(String reason) {
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Project p : ProjectManager.getInstance().getOpenProjects()) {
                if (p.isDisposed()) continue;
                LOG.info("JOUST language server stop (" + reason + ")");
                JoustStatusCache.getInstance(p).clear();
                LanguageServerManager.getInstance(p).stop(JoustConstants.SERVER_ID,
                        new LanguageServerManager.StopOptions().setWillDisable(false));
            }
        });
    }

    /** The server's current status in the project. */
    public static ServerStatus status(Project project) {
        return LanguageServerManager.getInstance(project).getServerStatus(JoustConstants.SERVER_ID);
    }
}
