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
 * ADR-004 D4: when a project opens whose roots contain a configured tool path (or its real target), the JOUST servers
 * stop and the user is told. Nothing is executed: this is the text and real-path check only.
 */
public final class JoustProjectOpenCheck implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        JoustToolsState state = JoustToolsState.getInstance();
        if (!state.looksConfigured()) return Unit.INSTANCE;
        List<Path> roots = JoustForbiddenRoots.of(project);
        JoustPaths.Flavor flavor = JoustPaths.Flavor.current();
        var node = JoustToolValidator.checkNode(state.getState().nodePath, roots, flavor);
        var lsp = JoustToolValidator.resolveJoustLsp(state.getState().joustLspPath, roots, flavor);
        String problem = !node.ok() ? node.error() : !lsp.ok() ? lsp.error() : null;
        if (problem != null && (problem.contains("inside an open project") || problem.contains("node_modules/.bin"))) {
            JoustServerControl.stopAll("a configured JOUST tool is inside " + project.getName());
            JoustNotifications.show(project, "JOUST language server stopped", problem, NotificationType.WARNING);
        }
        return Unit.INSTANCE;
    }
}
