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
    private static final Key<Boolean> UNSUPPORTED_SHOWN = Key.create("intelliada.joust.unsupportedShown");

    /** For tests, which share one project: forget that the notifications were shown. */
    public static void resetForTests(Project project) {
        project.putUserData(SHOWN, null);
        project.putUserData(UNSUPPORTED_SHOWN, null);
    }

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (!JoustServerAccess.isJsc(file)) return;
        Project project = source.getProject();
        // ADR-004 §12.2 E22 (UP-DES-6): an unsupported LSP4IJ is said first, once per project session; it outranks the
        // configuration, which cannot help.
        String unsupported = com.bloxbean.intelliada.idea.joust.lsp.JoustLsp4jCompat.problem();
        if (unsupported != null) {
            if (Boolean.TRUE.equals(project.getUserData(UNSUPPORTED_SHOWN))) return;
            project.putUserData(UNSUPPORTED_SHOWN, Boolean.TRUE);
            JoustNotifications.show(project, "JOUST language server unavailable", unsupported, NotificationType.WARNING);
            return;
        }
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
