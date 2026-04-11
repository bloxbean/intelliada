package com.bloxbean.intelliada.idea.julc.editor;

import com.bloxbean.intelliada.idea.julc.annotator.validate.JulcVmBridge;
import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

/**
 * Shows a warning banner at the top of the editor when a julc validator's
 * compiled script size approaches the 16 KB on-chain limit.
 */
public class JulcScriptSizeNotificationProvider implements EditorNotificationProvider {

    private static final int WARNING_THRESHOLD = 12 * 1024; // 12 KB
    private static final int DANGER_THRESHOLD = 15 * 1024;  // 15 KB

    @Override
    public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(
            @NotNull Project project, @NotNull VirtualFile file) {

        if (!"java".equalsIgnoreCase(file.getExtension())) return null;

        JulcTomlService tomlService = JulcTomlService.getInstance(project);
        if (tomlService == null || !tomlService.isJulcProject()) return null;

        if (!JulcVmBridge.isCompilerAvailable()) return null;

        // Quick check: does the file contain validator annotations?
        try {
            String content = new String(file.contentsToByteArray());
            if (!content.contains("@Validator") && !content.contains("@SpendingValidator")
                    && !content.contains("@MintingValidator") && !content.contains("@MultiValidator")) {
                return null;
            }

            // Compile to check size
            JulcVmBridge.CompileInfo info = JulcVmBridge.compile(content);
            if (info == null || info.hasErrors || info.scriptSizeBytes < WARNING_THRESHOLD) {
                return null;
            }

            int sizeBytes = info.scriptSizeBytes;
            String sizeText = formatSize(sizeBytes);

            return fileEditor -> {
                EditorNotificationPanel panel = new EditorNotificationPanel(
                        sizeBytes > DANGER_THRESHOLD ? EditorNotificationPanel.Status.Error
                                : EditorNotificationPanel.Status.Warning);
                panel.setText("julc: Script size " + sizeText + " / 16 KB — "
                        + (sizeBytes > DANGER_THRESHOLD ? "critically close to limit!" : "consider optimizing"));
                panel.createActionLabel("Dismiss", () -> panel.setVisible(false));
                return panel;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        return kb < 10 ? String.format("%.1f KB", kb) : String.format("%.0f KB", kb);
    }
}
