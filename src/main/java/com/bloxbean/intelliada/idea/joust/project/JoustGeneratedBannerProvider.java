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
        JoustProjectLayout.GradleModule module = JoustGeneratedSourcesFilter.moduleOf(file, project);
        if (module == null) return null;
        return editor -> {
            EditorNotificationPanel panel = new EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info);
            String source = file.getNameWithoutExtension() + ".jsc";
            panel.setText(PlainText.label("Generated from " + source + " by generateJsc: edit the .jsc", 200));
            panel.createActionLabel("Open " + PlainText.label(source, 100), () -> openSource(project, module, file, editor));
            return panel;
        };
    }

    /** Opens the {@code .jsc} position for the editor's caret line; true when opened. */
    static boolean openSource(Project project, JoustProjectLayout.GradleModule module, VirtualFile java, FileEditor editor) {
        int line = editor instanceof TextEditor te ? te.getEditor().getCaretModel().getLogicalPosition().line + 1 : 1;
        try {
            var target = JoustGeneratedSources.jscFor(module, java.toNioPath().toRealPath(), line);
            if (target == null) return false;
            VirtualFile jsc = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target.jsc());
            return jsc != null && JoustSafeOpen.open(project, jsc, target.line() - 1, target.column() - 1, true);
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }

    /** For tests and the action: the target for a generated file and line. */
    static JoustGeneratedSources.JscTarget targetFor(Project project, VirtualFile java, int line) throws java.io.IOException {
        JoustProjectLayout.GradleModule module = JoustGeneratedSourcesFilter.moduleOf(java, project);
        return module == null ? null : JoustGeneratedSources.jscFor(module, java.toNioPath().toRealPath(), line);
    }
}
