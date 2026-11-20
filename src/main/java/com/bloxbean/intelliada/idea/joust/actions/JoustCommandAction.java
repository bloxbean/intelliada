package com.bloxbean.intelliada.idea.joust.actions;

import com.bloxbean.intelliada.idea.joust.editor.JoustServerAccess;
import com.bloxbean.intelliada.idea.joust.lsp.JoustLanguageServerApi;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only JOUST command on the current .jsc file (ADR-004 D3). Every command answers any client and writes nothing
 * (F15); results are shown as plain text.
 */
abstract class JoustCommandAction extends AnAction {

    /** The request context: the project, the file's URI and the caret position. */
    record Context(Project project, VirtualFile file, String uri, Position position) {
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null
                && JoustServerAccess.isJsc(e.getData(CommonDataKeys.VIRTUAL_FILE)));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || !JoustServerAccess.isJsc(vf)) return;
        Position pos = editor == null ? new Position(0, 0)
                : LSPIJUtils.toPosition(editor.getCaretModel().getOffset(), editor.getDocument());
        new Task.Backgroundable(project, title(), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                JoustLanguageServerApi server = JoustServerAccess.server(project);
                if (server == null) {
                    var n = com.bloxbean.intelliada.idea.joust.lsp.JoustNotifications.create("JOUST language server unavailable",
                            "The .jsc language server is not configured or did not start.",
                            com.intellij.notification.NotificationType.WARNING);
                    n.addAction(com.intellij.notification.NotificationAction.createSimpleExpiring("Configure JOUST…",
                            () -> com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(project, "JOUST")));
                    n.notify(project);
                    return;
                }
                String uri = ReadAction.compute(() -> JoustServerAccess.uriOf(vf));
                perform(server, new Context(project, vf, uri, pos));
            }
        }.queue();
    }

    abstract String title();

    /** Runs on a background thread. */
    abstract void perform(JoustLanguageServerApi server, Context ctx);
}
