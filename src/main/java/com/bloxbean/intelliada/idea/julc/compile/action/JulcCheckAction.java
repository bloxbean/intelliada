package com.bloxbean.intelliada.idea.julc.compile.action;

import com.bloxbean.intelliada.idea.aiken.compile.CompilationResultListener;
import com.bloxbean.intelliada.idea.julc.compile.JulcCompileService;
import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class JulcCheckAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(JulcCheckAction.class);

    public JulcCheckAction() {
        super(AllIcons.RunConfigurations.TestState.Run);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setVisible(false);
            return;
        }

        JulcTomlService tomlService = JulcTomlService.getInstance(project);
        boolean isJulc = tomlService != null && tomlService.isJulcProject();
        e.getPresentation().setVisible(isJulc);
        e.getPresentation().setEnabled(isJulc);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
        if (project == null) return;

        FileDocumentManager.getInstance().saveAllDocuments();

        final CardanoConsole console = CardanoConsole.getConsole(project);
        console.clearAndshow();

        CompilationResultListener listener = JulcBuildAction.createListener(project, console, null);

        Task.Backgroundable task = new Task.Backgroundable(project, "julc Tests") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                JulcCompileService compileService = new JulcCompileService(project);
                console.showInfoMessage("Running julc tests...");
                compileService.check(listener);
            }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }

    @NotNull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
