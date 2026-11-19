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
 * the selected and real paths are checked against the new project's roots ({@link #conflict}).
 */
public final class JoustProjectOpenCheck implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        check(project);
        return Unit.INSTANCE;
    }

    /** The check itself, run when a project opens. */
    public static void check(Project project) {
        JoustToolsState state = JoustToolsState.getInstance();
        if (!state.looksConfigured()) return;
        String problem = conflict(state.getState().nodePath, state.getState().joustLspPath, JoustForbiddenRoots.of(project),
                JoustPaths.Flavor.current());
        if (problem != null) {
            JoustServerControl.stopAll("a configured JOUST tool is inside " + project.getName());
            JoustNotifications.show(project, "JOUST language server stopped", problem, NotificationType.WARNING);
        }
        return;
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
        String l = conflictOf("joust-lsp", lspPath, executionTarget(lspPath, windowsLayout), roots, flavor);
        if (l != null) return l;
        // The paths a running server was actually started with, whatever the configuration says now (M1-CDX-23).
        for (Path launched : com.bloxbean.intelliada.idea.joust.lsp.JoustStreamConnectionProvider.launchedPaths()) {
            String w = JoustToolValidator.locationProblem(launched, launched, roots, flavor);
            if (w != null) return "a running JOUST server " + w;
        }
        return null;
    }

    /**
     * The file that runs for a configured joust-lsp, found the way the launch finds it but without requiring its package
     * to be valid: on Windows, the package layout beside the launcher; elsewhere, the selected path's real target.
     */
    static Path executionTarget(String lspPath, boolean windowsLayout) {
        if (lspPath == null || lspPath.isBlank()) return null;
        try {
            Path selected = Path.of(lspPath.trim());
            if (windowsLayout && JoustPaths.fileName(selected.toString()).equalsIgnoreCase("joust-lsp") && selected.getParent() != null) {
                Path js = selected.getParent().resolve("node_modules").resolve("@julclang").resolve("joust").resolve("bin").resolve("joust-lsp.js");
                return java.nio.file.Files.exists(js) ? js.toRealPath() : null;
            }
            return selected.toRealPath();
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
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
