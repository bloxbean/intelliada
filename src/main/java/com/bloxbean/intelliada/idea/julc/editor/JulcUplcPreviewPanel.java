package com.bloxbean.intelliada.idea.julc.editor;

import com.bloxbean.intelliada.idea.julc.annotator.validate.JulcVmBridge;
import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * UPLC Preview panel — shows generated UPLC alongside the Java source.
 * Updates on file save with a debounce delay.
 */
public class JulcUplcPreviewPanel {
    private static final Logger LOG = Logger.getInstance(JulcUplcPreviewPanel.class);

    private final Project project;
    private JPanel mainPanel;
    private JTextArea uplcTextArea;
    private JLabel statusLabel;
    private Timer debounceTimer;

    public JulcUplcPreviewPanel(Project project) {
        this.project = project;
        initComponents();
        listenForFileChanges();
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void initComponents() {
        mainPanel = new JPanel(new BorderLayout());

        // Status bar at top
        JPanel topPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Open a julc validator file to see UPLC output");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> compileCurrentFile());
        topPanel.add(statusLabel, BorderLayout.CENTER);
        topPanel.add(refreshBtn, BorderLayout.EAST);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // UPLC text area
        uplcTextArea = new JTextArea();
        uplcTextArea.setEditable(false);
        uplcTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        uplcTextArea.setBackground(new Color(30, 30, 30));
        uplcTextArea.setForeground(new Color(200, 200, 200));
        uplcTextArea.setCaretColor(Color.WHITE);
        mainPanel.add(new JScrollPane(uplcTextArea), BorderLayout.CENTER);
    }

    private void listenForFileChanges() {
        project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                if ("java".equalsIgnoreCase(file.getExtension())) {
                    scheduleCompile(file);
                }
            }
        });
    }

    private void scheduleCompile(VirtualFile file) {
        if (debounceTimer != null) debounceTimer.cancel();
        debounceTimer = new Timer();
        debounceTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                compileFile(file);
            }
        }, 500);
    }

    private void compileCurrentFile() {
        VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
        if (files.length > 0 && "java".equalsIgnoreCase(files[0].getExtension())) {
            compileFile(files[0]);
        }
    }

    private void compileFile(VirtualFile file) {
        if (!JulcVmBridge.isCompilerAvailable()) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("julc compiler not available");
                uplcTextArea.setText("");
            });
            return;
        }

        JulcTomlService tomlService = JulcTomlService.getInstance(project);
        if (tomlService == null || !tomlService.isJulcProject()) return;

        ApplicationManager.getApplication().runReadAction(() -> {
            Document doc = FileDocumentManager.getInstance().getDocument(file);
            if (doc == null) return;
            String source = doc.getText();

            if (!source.contains("@Validator") && !source.contains("@SpendingValidator")
                    && !source.contains("@MintingValidator") && !source.contains("@MultiValidator")) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Not a julc validator file");
                    uplcTextArea.setText("");
                });
                return;
            }

            JulcVmBridge.CompileInfo info = JulcVmBridge.compile(source);
            SwingUtilities.invokeLater(() -> {
                if (info == null) {
                    statusLabel.setText("Compilation returned null");
                    uplcTextArea.setText("");
                } else if (info.hasErrors) {
                    statusLabel.setText("Compilation errors (" + info.diagnostics.size() + ")");
                    StringBuilder sb = new StringBuilder("Compilation errors:\n\n");
                    for (var diag : info.diagnostics) {
                        sb.append("Line ").append(diag.line()).append(": ").append(diag.message()).append("\n");
                    }
                    uplcTextArea.setText(sb.toString());
                } else {
                    statusLabel.setText("✅ " + file.getName() + " — " + formatSize(info.scriptSizeBytes)
                            + (info.parameterized ? " (parameterized)" : ""));
                    statusLabel.setForeground(info.scriptSizeBytes > 14 * 1024 ? Color.RED
                            : info.scriptSizeBytes > 8 * 1024 ? new Color(200, 150, 0)
                            : new Color(0, 128, 0));
                    uplcTextArea.setText(info.uplcText != null ? info.uplcText : "(no UPLC output)");
                    uplcTextArea.setCaretPosition(0);
                }
            });
        });
    }

    private String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        return kb < 10 ? String.format("%.1f KB", kb) : String.format("%.0f KB", kb);
    }
}
