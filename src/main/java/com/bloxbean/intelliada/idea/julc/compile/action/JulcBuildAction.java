package com.bloxbean.intelliada.idea.julc.compile.action;

import com.bloxbean.intelliada.idea.aiken.compile.CompilationResultListener;
import com.bloxbean.intelliada.idea.julc.compile.JulcCompileService;
import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class JulcBuildAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(JulcBuildAction.class);

    public JulcBuildAction() {
        super(AllIcons.Actions.Compile);
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

        final String projectDir = project.getBasePath();
        final VirtualFile folderToRefresh = projectDir != null
                ? VfsUtil.findFileByIoFile(new File(projectDir), true) : null;

        CompilationResultListener listener = createListener(project, console, folderToRefresh);

        Task.Backgroundable task = new Task.Backgroundable(project, "julc Build") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                JulcCompileService compileService = new JulcCompileService(project);
                console.showInfoMessage("Start julc build...");
                compileService.compile(listener);
            }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }

    @NotNull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    static CompilationResultListener createListener(Project project, CardanoConsole console, VirtualFile folderToRefresh) {
        return new CompilationResultListener() {
            @Override
            public void attachProcess(OSProcessHandler handler) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        console.getView().attachToProcess(handler);
                    } catch (IncorrectOperationException ex) {
                        console.showInfoMessage(ex.getMessage());
                        console.dispose();
                        console.getView().attachToProcess(handler);
                    }
                    handler.startNotify();
                });
            }

            @Override
            public void error(String message) {
                console.showErrorMessage(message);
            }

            @Override
            public void info(String message) {
                console.showInfoMessage(message);
            }

            @Override
            public void warn(String msg) {
                console.showWarningMessage(msg);
            }

            @Override
            public void onSuccessful(String sourceFile) {
                console.showSuccessMessage("Build Successful");
                if (folderToRefresh != null) {
                    folderToRefresh.refresh(false, false);
                }
                IdeaUtil.showNotification(project, "julc Build", "Build was successful", NotificationType.INFORMATION, null);
            }

            @Override
            public void onFailure(String sourceFile, Throwable t) {
                console.showErrorMessage(String.format("Build failed for %s", sourceFile), t);
                IdeaUtil.showNotification(project, "julc Build", "Build failed", NotificationType.ERROR, null);
            }
        };
    }
}
