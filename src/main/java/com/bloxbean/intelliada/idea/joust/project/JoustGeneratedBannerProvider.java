package com.bloxbean.intelliada.idea.joust.project;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.editor.JoustSafeOpen;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * ADR-004 D7: the banner on generated Java, "Generated from Counter.jsc by generateJsc: edit the .jsc", with an action
 * that opens the {@code .jsc} line the caret's Java line came from ({@code Name.jscmap.json}), confined to the contracts
 * directory (I18).
 */
public final class JoustGeneratedBannerProvider implements EditorNotificationProvider {

    @Override
    public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                     @NotNull VirtualFile file) {
        JoustProjectLayout.SourceRoot root = JoustGeneratedSourcesFilter.rootOf(file, project);
        if (root == null) return null;
        return editor -> {
            EditorNotificationPanel panel = new EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info);
            String source = file.getNameWithoutExtension() + ".jsc";
            String by = root.gradle() != null
                    ? "generateJsc" + (root.gradle().pluginVersion() != null ? " (" + PLUGIN + " " + root.gradle().pluginVersion() + ")" : "")
                    : "joust build";
            panel.setText(PlainText.label("Generated from " + source + " by " + by + ": edit the .jsc", 200));
            panel.createActionLabel("Open " + PlainText.label(source, 100), () -> openSource(project, root, file, editor));
            return panel;
        };
    }

    static final String PLUGIN = JoustProjectLayout.PLUGIN_ID;

    /** Opens the {@code .jsc} position for the editor's caret line; true when opened. */
    static boolean openSource(Project project, JoustProjectLayout.SourceRoot root, VirtualFile java, FileEditor editor) {
        int line = editor instanceof TextEditor te ? te.getEditor().getCaretModel().getLogicalPosition().line + 1 : 1;
        try {
            Path base = com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots.baseReal(project);
            var target = JoustGeneratedSources.jscFor(root, base, java.toNioPath().toRealPath(), line);
            if (target == null) return false;
            VirtualFile jsc = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target.jsc());
            return jsc != null && JoustSafeOpen.open(project, jsc, target.line() - 1, target.column() - 1, true);
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }

    /** For tests and the action: the target for a generated file and line. */
    static JoustGeneratedSources.JscTarget targetFor(Project project, VirtualFile java, int line) throws java.io.IOException {
        JoustProjectLayout.SourceRoot root = JoustGeneratedSourcesFilter.rootOf(java, project);
        return root == null ? null : JoustGeneratedSources.jscFor(root, com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots.baseReal(project),
                java.toNioPath().toRealPath(), line);
    }
}
