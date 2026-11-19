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
 * stop (without being disabled: they start again once the conflict is gone) and the user is told. The native
 * {@code joust} of D9 gets the same check, and the user is told that the wizard will not run it. Nothing is executed:
 * the selected and real paths are checked against the new project's roots ({@link #conflict}).
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

    /** Why a configured tool lies inside {@code roots} (selected or real path), or {@code null}. Executes nothing. */
    public static String conflict(String nodePath, String lspPath, List<Path> roots, JoustPaths.Flavor flavor) {
        return conflict(new String[][]{{"Node", nodePath}, {"joust-lsp", lspPath}}, roots, flavor);
    }

    static String conflict(String[][] tools, List<Path> roots, JoustPaths.Flavor flavor) {
        for (String[] tool : tools) {
            if (tool[1] == null || tool[1].isBlank() || !JoustPaths.isAbsolute(tool[1].trim(), flavor)) continue;
            Path selected = Path.of(tool[1].trim());
            Path real;
            try {
                real = selected.toRealPath();
            } catch (java.io.IOException e) {
                real = selected;
            }
            String where = JoustToolValidator.locationProblem(selected, real, roots, flavor);
            if (where != null) return tool[0] + " " + where;
        }
        return null;
    }
}
