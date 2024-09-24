
package com.bloxbean.intelliada.idea.aiken.compile.action;

import com.bloxbean.intelliada.idea.aiken.compile.AikenCompileService;
import com.bloxbean.intelliada.idea.aiken.compile.CompilationResultListener;
import com.bloxbean.intelliada.idea.aiken.compile.SDKNotConfigured;
import com.bloxbean.intelliada.idea.aiken.configuration.AikenConfigurationAction;
import com.bloxbean.intelliada.idea.aiken.configuration.AikenConfigurationHelperService;
import com.bloxbean.intelliada.idea.aiken.configuration.AikenSDK;
import com.bloxbean.intelliada.idea.aiken.lang.psi.AikenFile;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class AikenFormatAction extends AnAction {
    private final static Logger LOG = Logger.getInstance(AikenFormatAction.class);

    public AikenFormatAction() {
        super(AllIcons.Actions.ReformatCode);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);

        PsiFile file = e.getDataContext().getData(CommonDataKeys.PSI_FILE);

        if (file != null && file instanceof AikenFile) {
            e.getPresentation().setEnabled(true);
        } else {
            e.getPresentation().setVisible(false);
            e.getPresentation().setEnabled(false);
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (LOG.isDebugEnabled())
            LOG.debug("Format Aiken File");

        Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
        if (project == null)
            return;

        final Module module = LangDataKeys.MODULE.getData(e.getDataContext());
        if (module == null)
            return;

        FileDocumentManager.getInstance().saveAllDocuments();

        final CardanoConsole console = CardanoConsole.getConsole(project);
        console.clearAndshow();

        //TODO -- If sdk not set, check in default location
        AikenSDK localSDK = AikenConfigurationHelperService.getCompilerLocalSDK(project);

        if (localSDK == null) {
            IdeaUtil.showNotification(project, "Format configuration",
                    "Aikon SDK  is not set for this module.", NotificationType.ERROR, AikenConfigurationAction.ACTION_ID);
            return;
        }
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (!(psiFile instanceof AikenFile)) {
            Messages.showErrorDialog("Not a Aiken fille", "Aiken Formatting");
            return;
        }

        VirtualFile fileToRefresh = psiFile.getVirtualFile();

        CompilationResultListener compilationResultListener = new CompilationResultListener() {
            @Override
            public void attachProcess(OSProcessHandler handler) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        console.getView().attachToProcess(handler);
                    } catch (IncorrectOperationException ex) {
                        //This should not happen
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
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (fileToRefresh != null) {
                        fileToRefresh.refresh(false, false);
                        System.out.println(fileToRefresh.getCanonicalPath());
                        Optional.ofNullable(FileDocumentManager.getInstance().getDocument(fileToRefresh))
                                .ifPresent(document -> FileDocumentManager.getInstance().reloadFromDisk(document));
                    }
                });
            }

            @Override
            public void onFailure(String sourceFile, Throwable t) {
                console.showErrorMessage(String.format("Formatting failed for %s", sourceFile), t);
                IdeaUtil.showNotification(project, "Aiken Formatting", "Formatting failed", NotificationType.ERROR, null);
            }
        };

        Task.Backgroundable task = new Task.Backgroundable(project, "Aiken Formatting") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                AikenCompileService compileService = null;
                if (localSDK != null) {
                    try {
                        compileService = new AikenCompileService(project);
                    } catch (SDKNotConfigured sdkNotConfigured) {
                        Messages.showErrorDialog("Aiken SDK is not set for this module.", "Aiken Formatting");
                        return;
                    }
                }

                compileService.format(fileToRefresh.getCanonicalPath(), compilationResultListener);
            }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }
}
