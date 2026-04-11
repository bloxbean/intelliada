package com.bloxbean.intelliada.idea.julc.trace;

import com.bloxbean.intelliada.idea.julc.annotator.validate.JulcVmBridge;
import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Execution trace panel showing:
 * - Compile result (script size, parameterized)
 * - Evaluation result (pass/fail, budget)
 * - User trace messages
 * - Budget delta from last run
 * - Budget hotspot summary
 */
public class JulcExecutionTracePanel {
    private static final Logger LOG = Logger.getInstance(JulcExecutionTracePanel.class);

    private final Project project;
    private JPanel mainPanel;
    private JLabel resultLabel;
    private JLabel budgetLabel;
    private JLabel sizeLabel;
    private JLabel deltaLabel;
    private JTextArea traceArea;
    private DefaultTableModel hotspotModel;

    public JulcExecutionTracePanel(Project project) {
        this.project = project;
        initComponents();
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void initComponents() {
        mainPanel = new JPanel(new BorderLayout(5, 5));

        // Top: Run button + status
        JPanel topPanel = new JPanel(new BorderLayout());
        JButton runBtn = new JButton("Run & Trace Current Validator");
        runBtn.addActionListener(e -> runCurrentFile());
        topPanel.add(runBtn, BorderLayout.WEST);

        JPanel statusPanel = new JPanel(new GridLayout(2, 2, 10, 2));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 4));

        resultLabel = new JLabel("—");
        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.BOLD, 14f));
        budgetLabel = new JLabel("Budget: —");
        budgetLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        sizeLabel = new JLabel("Size: —");
        deltaLabel = new JLabel("");
        deltaLabel.setFont(deltaLabel.getFont().deriveFont(Font.ITALIC, 11f));

        statusPanel.add(resultLabel);
        statusPanel.add(sizeLabel);
        statusPanel.add(budgetLabel);
        statusPanel.add(deltaLabel);

        topPanel.add(statusPanel, BorderLayout.CENTER);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center: Split pane — traces + hotspots
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.6);

        // Trace messages
        traceArea = new JTextArea();
        traceArea.setEditable(false);
        traceArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        traceArea.setBackground(new Color(30, 30, 30));
        traceArea.setForeground(new Color(200, 200, 200));
        JPanel tracePanel = new JPanel(new BorderLayout());
        tracePanel.setBorder(BorderFactory.createTitledBorder("Execution Trace"));
        tracePanel.add(new JScrollPane(traceArea), BorderLayout.CENTER);
        splitPane.setTopComponent(tracePanel);

        // Budget hotspots
        hotspotModel = new DefaultTableModel(new String[]{"Operation", "CPU Steps", "% of Total"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable hotspotTable = new JTable(hotspotModel);
        JPanel hotspotPanel = new JPanel(new BorderLayout());
        hotspotPanel.setBorder(BorderFactory.createTitledBorder("Budget Hotspots"));
        hotspotPanel.add(new JScrollPane(hotspotTable), BorderLayout.CENTER);
        splitPane.setBottomComponent(hotspotPanel);

        mainPanel.add(splitPane, BorderLayout.CENTER);
    }

    private void runCurrentFile() {
        VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
        if (files.length == 0) {
            resultLabel.setText("No file open");
            resultLabel.setForeground(Color.GRAY);
            return;
        }

        VirtualFile file = files[0];
        if (!"java".equalsIgnoreCase(file.getExtension())) {
            resultLabel.setText("Not a Java file");
            resultLabel.setForeground(Color.GRAY);
            return;
        }

        JulcTomlService tomlService = JulcTomlService.getInstance(project);
        if (tomlService == null || !tomlService.isJulcProject()) {
            resultLabel.setText("Not a julc project");
            resultLabel.setForeground(Color.GRAY);
            return;
        }

        resultLabel.setText("Compiling...");
        resultLabel.setForeground(new Color(200, 150, 0));
        traceArea.setText("");
        hotspotModel.setRowCount(0);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Running julc Validator with Trace...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                String source = ApplicationManager.getApplication().runReadAction(
                        (com.intellij.openapi.util.Computable<String>) () -> {
                            Document doc = FileDocumentManager.getInstance().getDocument(file);
                            return doc != null ? doc.getText() : null;
                        });

                if (source == null || !JulcVmBridge.isCompilerAvailable()) {
                    SwingUtilities.invokeLater(() -> {
                        resultLabel.setText("Cannot compile — julc compiler not available");
                        resultLabel.setForeground(Color.RED);
                    });
                    return;
                }

                // Compile
                indicator.setText("Compiling...");
                JulcVmBridge.CompileInfo compileInfo = JulcVmBridge.compile(source);
                if (compileInfo == null) {
                    SwingUtilities.invokeLater(() -> {
                        resultLabel.setText("❌ Compilation returned null");
                        resultLabel.setForeground(Color.RED);
                    });
                    return;
                }

                if (compileInfo.hasErrors) {
                    SwingUtilities.invokeLater(() -> {
                        resultLabel.setText("❌ Compilation failed");
                        resultLabel.setForeground(Color.RED);
                        StringBuilder sb = new StringBuilder("Compilation errors:\n");
                        for (var d : compileInfo.diagnostics) {
                            sb.append("  Line ").append(d.line()).append(": ").append(d.message()).append("\n");
                        }
                        traceArea.setText(sb.toString());
                    });
                    return;
                }

                // Evaluate
                indicator.setText("Evaluating...");
                JulcVmBridge.EvalInfo evalInfo = null;
                if (JulcVmBridge.isVmAvailable()) {
                    evalInfo = JulcVmBridge.evaluate(compileInfo.program);
                }

                // Record budget delta
                String filePath = file.getPath();
                long cpu = evalInfo != null ? evalInfo.cpuSteps : 0;
                long mem = evalInfo != null ? evalInfo.memoryUnits : 0;
                JulcBudgetTracker.BudgetDelta delta = JulcBudgetTracker.record(filePath, cpu, mem, compileInfo.scriptSizeBytes);

                // Update UI on EDT
                final JulcVmBridge.EvalInfo finalEval = evalInfo;
                SwingUtilities.invokeLater(() -> updateUI(compileInfo, finalEval, delta));
            }
        });
    }

    private void updateUI(JulcVmBridge.CompileInfo compileInfo, JulcVmBridge.EvalInfo evalInfo,
                           JulcBudgetTracker.BudgetDelta delta) {
        // Result
        if (evalInfo != null) {
            if (evalInfo.success) {
                resultLabel.setText("✅ PASS");
                resultLabel.setForeground(new Color(0, 128, 0));
            } else {
                resultLabel.setText("❌ FAIL" + (evalInfo.errorMessage != null ? ": " + evalInfo.errorMessage : ""));
                resultLabel.setForeground(Color.RED);
            }
            budgetLabel.setText("Budget: CPU " + formatNumber(evalInfo.cpuSteps) + " | Memory " + formatNumber(evalInfo.memoryUnits));
        } else {
            resultLabel.setText("Compiled (VM not available)");
            resultLabel.setForeground(new Color(200, 150, 0));
            budgetLabel.setText("Budget: —");
        }

        // Size
        sizeLabel.setText("Size: " + formatSize(compileInfo.scriptSizeBytes)
                + (compileInfo.parameterized ? " (parameterized)" : ""));

        // Delta
        if (delta.hasChanged()) {
            StringBuilder db = new StringBuilder();
            if (!delta.formatCpuDelta().isEmpty()) db.append("CPU ").append(delta.formatCpuDelta()).append(" ");
            if (!delta.formatMemDelta().isEmpty()) db.append("Mem ").append(delta.formatMemDelta()).append(" ");
            if (!delta.formatSizeDelta().isEmpty()) db.append("Size ").append(delta.formatSizeDelta());
            deltaLabel.setText("Δ " + db.toString().trim());
            deltaLabel.setForeground(delta.cpuDelta() > 0 ? Color.RED : new Color(0, 128, 0));
        } else if (delta.previous() == null) {
            deltaLabel.setText("(first run)");
            deltaLabel.setForeground(Color.GRAY);
        } else {
            deltaLabel.setText("(no change)");
            deltaLabel.setForeground(Color.GRAY);
        }

        // Traces
        StringBuilder traces = new StringBuilder();
        if (evalInfo != null && !evalInfo.traces.isEmpty()) {
            traces.append("User Traces (Builtins.trace):\n");
            for (String t : evalInfo.traces) {
                traces.append("  → ").append(t).append("\n");
            }
            traces.append("\n");
        }

        if (compileInfo.uplcText != null) {
            traces.append("UPLC Output:\n");
            traces.append(compileInfo.uplcText);
        }

        traceArea.setText(traces.toString());
        traceArea.setCaretPosition(0);
    }

    private String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        return kb < 10 ? String.format("%.1f KB", kb) : String.format("%.0f KB", kb);
    }

    private String formatNumber(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return String.format("%.1fK", n / 1000.0);
        return String.format("%.2fM", n / 1_000_000.0);
    }
}
