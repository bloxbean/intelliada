package com.bloxbean.intelliada.idea.joust.trust;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.lsp.JoustNotifications;
import com.bloxbean.intelliada.idea.joust.lsp.JoustServerControl;
import com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots;
import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * ADR-004 D4: when a project opens whose roots contain a configured tool path or its real target, the JOUST servers
 * stop (without being disabled: they start again once the conflict is gone) and the user is told. Nothing is executed:
 * the selected and real paths are checked against the new project's roots ({@link #conflict}). The native {@code joust}
 * of D9 gets the same check, and the user is told that the wizard will not run it.
 */
public final class JoustProjectOpenCheck implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        JoustToolsState state = JoustToolsState.getInstance();
        String cli = state.getState().joustCliPath;
        if (cli != null && !cli.isBlank()) {
            // ADR-004 D9: the native joust is held to D4's rule too; the wizard refuses it while this project is open.
            String c = conflict(new String[][]{{"joust", cli}}, JoustForbiddenRoots.of(project), JoustPaths.Flavor.current());
            if (c != null) JoustNotifications.show(project, "JOUST new-project wizard disabled", c, NotificationType.WARNING);
        }
        if (!state.looksConfigured()) return Unit.INSTANCE;
        String problem = conflict(state.getState().nodePath, state.getState().joustLspPath, JoustForbiddenRoots.of(project),
                JoustPaths.Flavor.current());
        if (problem != null) {
            JoustServerControl.stopAll("a configured JOUST tool is inside " + project.getName());
            JoustNotifications.show(project, "JOUST language server stopped", problem, NotificationType.WARNING);
        }
        return Unit.INSTANCE;
    }

    /**
     * Why a configured tool lies inside {@code roots}, or {@code null}. Executes nothing. For joust-lsp the path checked
     * is the one that runs, resolved as at launch (on Windows, by package layout), not only the selected launcher
     * (M1-CDX-23).
     */
    public static String conflict(String nodePath, String lspPath, List<Path> roots, JoustPaths.Flavor flavor) {
        return conflict(nodePath, lspPath, roots, flavor, flavor == JoustPaths.Flavor.WINDOWS);
    }

    public static String conflict(String nodePath, String lspPath, List<Path> roots, JoustPaths.Flavor flavor, boolean windowsLayout) {
        String n = conflictOf("Node", nodePath, null, roots, flavor);
        if (n != null) return n;
        Path target = null;
        if (lspPath != null && !lspPath.isBlank()) {
            var r = JoustToolValidator.resolveJoustLsp(lspPath, List.of(), flavor, windowsLayout);
            if (r.ok()) target = r.realPath();
        }
        return conflictOf("joust-lsp", lspPath, target, roots, flavor);
    }

    static String conflict(String[][] tools, List<Path> roots, JoustPaths.Flavor flavor) {
        for (String[] t : tools) {
            String c = conflictOf(t[0], t[1], null, roots, flavor);
            if (c != null) return c;
        }
        return null;
    }

    private static String conflictOf(String what, String path, Path executionTarget, List<Path> roots, JoustPaths.Flavor flavor) {
        if (path == null || path.isBlank() || !JoustPaths.isAbsolute(path.trim(), flavor)) return null;
        Path selected = Path.of(path.trim());
        Path real;
        try {
            real = selected.toRealPath();
        } catch (java.io.IOException e) {
            real = selected;
        }
        String where = JoustToolValidator.locationProblem(selected, real, roots, flavor);
        if (where == null && executionTarget != null) where = JoustToolValidator.locationProblem(executionTarget, executionTarget, roots, flavor);
        return where == null ? null : what + " " + where;
    }
}
