package com.bloxbean.intelliada.idea.joust.editor;

import com.bloxbean.intelliada.idea.joust.lsp.JoustNotifications;
import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * ADR-004 D3: opening a .jsc while the JOUST tools are not configured starts nothing and shows one notification with
 * Configure JOUST… (once per project session).
 */
public final class JoustEditorListener implements FileEditorManagerListener {

    private static final Key<Boolean> SHOWN = Key.create("intelliada.joust.configureShown");

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (!JoustServerAccess.isJsc(file)) return;
        Project project = source.getProject();
        if (JoustToolsState.getInstance().looksConfigured() || Boolean.TRUE.equals(project.getUserData(SHOWN))) return;
        project.putUserData(SHOWN, Boolean.TRUE);
        var n = JoustNotifications.create("JOUST language server is not configured",
                ".jsc files get TextMate highlighting. For diagnostics, hover and navigation, install @julclang/joust "
                        + "and point IntelliAda at Node and joust-lsp.", NotificationType.INFORMATION);
        n.addAction(NotificationAction.createSimpleExpiring("Configure JOUST…",
                () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, "JOUST")));
        n.notify(project);
    }
}
