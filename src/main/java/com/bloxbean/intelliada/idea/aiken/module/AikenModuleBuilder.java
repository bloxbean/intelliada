package com.bloxbean.intelliada.idea.aiken.module;

import com.bloxbean.intelliada.idea.aiken.common.AikenIcons;
import com.bloxbean.intelliada.idea.aiken.compile.AikenCompileService;
import com.bloxbean.intelliada.idea.aiken.compile.CompilationResultListener;
import com.bloxbean.intelliada.idea.aiken.compile.CompileService;
import com.bloxbean.intelliada.idea.aiken.compile.SDKNotConfigured;
import com.bloxbean.intelliada.idea.aiken.configuration.AikenConfigurationHelperService;
import com.bloxbean.intelliada.idea.aiken.configuration.AikenSDK;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AikenModuleBuilder extends ModuleBuilder implements ModuleBuilderListener {
    private static final Logger LOG = Logger.getInstance(AikenModuleBuilder.class);

    private List<Pair<String,String>> mySourcePaths;

    private OwnerInputField ownerInputField;

    public AikenModuleBuilder() {
        addListener(this);
    }

    @Override
    public String getBuilderId() {
        return "Aiken";
    }

    @Override
    public Icon getNodeIcon() {
        return AikenIcons.AIKEN_ICON;
    }

    @Override
    public String getDescription() {
        return "Aiken - A Modern Smart Contract Platform For Cardano";
    }

    @Override
    public String getPresentableName() {
        return "Aiken";
    }

    @Override
    public String getGroupName() {
        return "Aiken";
    }

    @Override
    public ModuleWizardStep[] createWizardSteps(WizardContext wizardContext, ModulesProvider modulesProvider) {
        return new ModuleWizardStep[]{};
    }

    @Override
    public void moduleCreated(@NotNull Module module) {
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
        for (VirtualFile srcRoot: roots) {
            if (srcRoot.getName().equals("validators")) {
                final VirtualFile topFolder = srcRoot.getParent();
                if (topFolder != null) {
                    ApplicationManager.getApplication().runWriteAction(new Runnable() {
                        @Override
                        public void run() {
                            ProjectGeneratorUtil.createNewContract(module.getProject(), srcRoot, AikenContractTemplates.AK_HELLOWORLD_TEMPLATE, module.getName().toLowerCase() + ".ak");

                            build(module);
                        }
                    });
                }
            }
        }
    }

    public static void build(@NotNull Module module) {
        var project = module.getProject();
        String moduleDir = ModuleUtil.getModuleDirPath(module);
        final VirtualFile folderToRefresh = VfsUtil.findFileByIoFile(new File(moduleDir), true);

        final CardanoConsole console = CardanoConsole.getConsole(project);
        console.clearAndshow();

        CompilationResultListener compilationResultListener = new CompilationResultListener() {
            @Override
            public void attachProcess(OSProcessHandler handler) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        console.getView().attachToProcess(handler);
                    } catch (IncorrectOperationException ex) {
                        //This should not happen
                        //ex.printStackTrace();
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
                IdeaUtil.showNotification(project, "Aiken Compile", "Compilation was successful", NotificationType.INFORMATION, null);
            }

            @Override
            public void onFailure(String sourceFile, Throwable t) {
                console.showErrorMessage(String.format("Compilation failed for %s", sourceFile), t);
                IdeaUtil.showNotification(project, "Aiken Compile", "Compilation failed", NotificationType.ERROR, null);
            }
        };

        Task.Backgroundable task = new Task.Backgroundable(module.getProject(), "Aiken Compile") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                AikenSDK localSDK = AikenConfigurationHelperService.getCompilerLocalSDK(project);
                CompileService compileService = null;
                if (localSDK != null) {
                    try {
                        compileService = new AikenCompileService(project);
                    } catch (SDKNotConfigured sdkNotConfigured) {
                        Messages.showErrorDialog("Aiken SDK is not set for this module.", "Aiken Compilation");
                        return;
                    }
                }

                console.showInfoMessage("Start compilation ..");
                compileService.compile(moduleDir, compilationResultListener);
            }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }

    @Override
    public void setupRootModel(@NotNull ModifiableRootModel rootModel) throws ConfigurationException {
        rootModel.inheritSdk();

        String moduleName = rootModel.getModule().getName().toLowerCase();

        String ownerInputFieldVal= ownerInputField.getValue();
        String owner = ownerInputFieldVal != null? ownerInputFieldVal.trim().toLowerCase(): System.getProperty("user.name").toLowerCase();

        Project project = rootModel.getProject();
        String basePath = project.getBasePath();
        ApplicationManager.getApplication().runWriteAction(
                () -> {
                    var aikenSdk = AikenConfigurationHelperService.getCompilerLocalSDK(project);

                    if (aikenSdk == null) {
                        IdeaUtil.showNotification(
                                "Project creation",
                                "Aiken executable not found. Please make sure Aiken is installed and added to the PATH", NotificationType.ERROR, null);
                        return;
                    }

                    String aikenModuleName = moduleName != null? moduleName.toLowerCase(): moduleName;

                    List<String> commands = aikenSdk.getAikenCommand();
                    commands.add("new");
                    commands.add(owner + "/" + aikenModuleName);

                    try {
                        //create a temporay directory
                        var tempDir = Files.createTempDirectory("aiken").toFile();
                        ProcessBuilder pb = new ProcessBuilder(commands);
                        pb.directory(tempDir);
                        pb.redirectErrorStream(true);
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        Process process = pb.start();
                        process.waitFor();

                        FileUtil.copyDirContent(new File(tempDir, aikenModuleName), new File(basePath));
                    } catch (Exception e) {
                        IdeaUtil.showNotification(project,
                                "Project creation",
                                "Aiken executable not found. Please make sure Aiken is installed and added to the PATH",
                                NotificationType.ERROR, e.getMessage());
                        if (LOG.isDebugEnabled()) {
                            LOG.error("Aiken executable not found", e);
                        }
                    }
                });

        ContentEntry contentEntry = doAddContentEntry(rootModel);
        if (contentEntry != null) {
            final List<Pair<String,String>> sourcePaths = getSourcePaths();

            if (sourcePaths != null) {
                for (final Pair<String, String> sourcePath : sourcePaths) {
                    String first = sourcePath.first;
                    new File(first).mkdirs();
                    final VirtualFile sourceRoot = LocalFileSystem.getInstance()
                            .refreshAndFindFileByPath(FileUtil.toSystemIndependentName(first));
                    if (sourceRoot != null) {
                        contentEntry.addSourceFolder(sourceRoot, false, sourcePath.second);
                    }
                }
            }
        }

    }

    @Override
    protected List<WizardInputField<?>> getAdditionalFields() {
        if (ownerInputField == null)
            ownerInputField = new OwnerInputField("owner", System.getProperty("user.name"));
        return Collections.singletonList(ownerInputField);
    }

    @Override
    public ModuleType<?> getModuleType() {
        return new AikenModuleType();
    }


    public List<Pair<String, String>> getSourcePaths() throws ConfigurationException {
        if (mySourcePaths == null) {
            final List<Pair<String, String>> paths = new ArrayList<>();
            @NonNls final String validatorsPath = getContentEntryPath() + File.separator + "validators";
            new File(validatorsPath).mkdirs();
            paths.add(Pair.create(validatorsPath, ""));

            @NonNls final String libPath = getContentEntryPath() + File.separator + "lib";
            new File(libPath).mkdirs();
            paths.add(Pair.create(libPath, ""));
            return paths;
        }
        return mySourcePaths;
    }

    class OwnerInputField extends WizardInputField<JTextField> {
        private JTextField jt;
        protected OwnerInputField(String id, String defaultValue) {
            super(id, defaultValue);
            jt = new JTextField();
            jt.setText(defaultValue);
        }

        @Override
        public @NlsContexts.Label String getLabel() {
            return "Owner";
        }

        @Override
        public JTextField getComponent() {
            return jt;
        }

        @Override
        public String getValue() {
            return jt.getText();
        }
    }

}
