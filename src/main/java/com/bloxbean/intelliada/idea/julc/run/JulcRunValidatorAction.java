package com.bloxbean.intelliada.idea.julc.run;

import com.bloxbean.intelliada.idea.julc.annotator.validate.JulcVmBridge;
import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * "Run Validator" action — compiles and evaluates a julc validator locally.
 * Available from julc context menu and gutter icon.
 * Uses julc-compiler + julc-vm via runtime classloader bridge.
 */
public class JulcRunValidatorAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(JulcRunValidatorAction.class);

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setVisible(false);
            return;
        }
        JulcTomlService tomlService = JulcTomlService.getInstance(project);
        boolean visible = tomlService != null && tomlService.isJulcProject() && JulcVmBridge.isCompilerAvailable();
        e.getPresentation().setVisible(visible);
        e.getPresentation().setEnabled(visible);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile == null) return;

        String source = psiFile.getText();
        if (source == null || source.isBlank()) return;

        CardanoConsole console = CardanoConsole.getConsole(project);
        console.clearAndshow();

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Running julc Validator...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Compiling validator...");
                indicator.setFraction(0.2);

                JulcVmBridge.CompileInfo compileInfo = JulcVmBridge.compile(source);
                if (compileInfo == null) {
                    console.showErrorMessage("[julc Run] Compilation returned null — bridge may not be available");
                    return;
                }

                if (compileInfo.hasErrors) {
                    console.showErrorMessage("[julc Run] Compilation failed:");
                    for (var diag : compileInfo.diagnostics) {
                        console.showErrorMessage("  " + diag.line() + ":" + diag.column() + " " + diag.message());
                    }
                    return;
                }

                console.showInfoMessage("[julc Run] Compilation successful");
                console.showInfoMessage("[julc Run] Script size: " + formatSize(compileInfo.scriptSizeBytes));
                if (compileInfo.parameterized) {
                    console.showWarningMessage("[julc Run] Validator is parameterized (@Param) — evaluation uses default parameters");
                }

                if (compileInfo.scriptSizeBytes > 14 * 1024) {
                    console.showWarningMessage("[julc Run] ⚠️ Script size " + formatSize(compileInfo.scriptSizeBytes)
                            + " is approaching the 16 KB on-chain limit!");
                }

                // Evaluate
                indicator.setText("Evaluating validator...");
                indicator.setFraction(0.6);

                if (!JulcVmBridge.isVmAvailable()) {
                    console.showWarningMessage("[julc Run] VM not available — showing compile results only");
                    showUplcPreview(console, compileInfo);
                    return;
                }

                JulcVmBridge.EvalInfo evalInfo = JulcVmBridge.evaluate(compileInfo.program);
                if (evalInfo == null) {
                    console.showWarningMessage("[julc Run] Evaluation returned null");
                    showUplcPreview(console, compileInfo);
                    return;
                }

                indicator.setFraction(1.0);

                // Show results
                console.showInfoMessage("");
                if (evalInfo.success) {
                    console.showSuccessMessage("[julc Run] ✅ PASS");
                } else {
                    console.showErrorMessage("[julc Run] ❌ FAIL" +
                            (evalInfo.errorMessage != null ? ": " + evalInfo.errorMessage : ""));
                }

                console.showInfoMessage("[julc Run] Budget: CPU " + formatNumber(evalInfo.cpuSteps)
                        + " | Memory " + formatNumber(evalInfo.memoryUnits));

                // Show traces
                if (!evalInfo.traces.isEmpty()) {
                    console.showInfoMessage("[julc Run] Traces:");
                    for (String trace : evalInfo.traces) {
                        console.showInfoMessage("  → " + trace);
                    }
                }

                showUplcPreview(console, compileInfo);
            }
        });
    }

    private void showUplcPreview(CardanoConsole console, JulcVmBridge.CompileInfo info) {
        if (info.uplcText != null) {
            console.showInfoMessage("");
            console.showInfoMessage("[julc Run] UPLC output:");
            // Show first 500 chars to avoid flooding console
            String preview = info.uplcText.length() > 500
                    ? info.uplcText.substring(0, 500) + "... (" + info.uplcText.length() + " chars total)"
                    : info.uplcText;
            console.showInfoMessage(preview);
        }
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

    @NotNull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
