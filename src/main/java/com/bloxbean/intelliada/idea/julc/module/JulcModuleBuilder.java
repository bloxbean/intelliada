package com.bloxbean.intelliada.idea.julc.module;

import com.bloxbean.intelliada.idea.julc.common.JulcIcons;
import com.bloxbean.intelliada.idea.julc.configuration.JulcConfigurationHelperService;
import com.bloxbean.intelliada.idea.julc.configuration.JulcDownloader;
import com.bloxbean.intelliada.idea.julc.configuration.JulcSDK;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleBuilderListener;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JulcModuleBuilder extends ModuleBuilder implements ModuleBuilderListener {
    private static final Logger LOG = Logger.getInstance(JulcModuleBuilder.class);

    private TemplateInputField templateField;
    private TextInputField groupField;
    private TextInputField artifactField;
    private TextInputField packageField;

    public JulcModuleBuilder() {
        addListener(this);
    }

    @Override
    public String getBuilderId() {
        return "Julc";
    }

    @Override
    public Icon getNodeIcon() {
        return JulcIcons.JULC_ICON;
    }

    @Override
    public String getDescription() {
        return "julc - Write Cardano Smart Contracts in Java";
    }

    @Override
    public String getPresentableName() {
        return "julc";
    }

    @Override
    public String getGroupName() {
        return "julc";
    }

    @Override
    public ModuleWizardStep[] createWizardSteps(com.intellij.ide.util.projectWizard.WizardContext wizardContext, ModulesProvider modulesProvider) {
        return new ModuleWizardStep[]{};
    }

    private static final ProjectSystemId GRADLE_SYSTEM_ID = new ProjectSystemId("GRADLE");

    @Override
    public void moduleCreated(@NotNull Module module) {
        Project project = module.getProject();
        String basePath = project.getBasePath();
        if (basePath == null) return;

        ApplicationManager.getApplication().invokeLater(() -> {
            // Refresh VFS so IntelliJ sees all scaffolded files
            VirtualFile baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath);
            if (baseDir != null) {
                VfsUtil.markDirtyAndRefresh(false, true, true, baseDir);
            }

            // Explicitly link and import as Gradle project
            try {
                VirtualFile buildFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(
                        basePath + "/build.gradle");
                if (buildFile != null) {
                    ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized(() -> {
                        try {
                            ExternalSystemUtil.refreshProject(
                                    basePath,
                                    new ImportSpecBuilder(project, GRADLE_SYSTEM_ID)
                            );
                            LOG.info("Triggered Gradle import for julc project at " + basePath);
                        } catch (Exception e) {
                            LOG.warn("Gradle import trigger failed. Please import manually via 'Load Gradle Project' notification.", e);
                        }
                    });
                }
            } catch (Exception e) {
                LOG.debug("Could not trigger Gradle import", e);
            }
        });
    }

    @Override
    public void setupRootModel(@NotNull ModifiableRootModel rootModel) throws ConfigurationException {
        rootModel.inheritSdk();

        String moduleName = rootModel.getModule().getName().toLowerCase();
        String template = templateField != null ? templateField.getValue() : "gradle";
        String group = groupField != null ? groupField.getValue() : "com.example";
        String artifact = artifactField != null ? artifactField.getValue() : moduleName;
        String pkg = packageField != null ? packageField.getValue() : group;

        Project project = rootModel.getProject();
        String basePath = project.getBasePath();

        ApplicationManager.getApplication().runWriteAction(() -> {
            JulcSDK sdk = JulcConfigurationHelperService.getCompilerLocalSDK(project);

            if (sdk == null) {
                // Offer to download julc CLI
                int result = Messages.showYesNoDialog(
                        "julc CLI is not configured. Would you like to download it now?",
                        "julc CLI Not Found",
                        "Download", "Cancel", Messages.getQuestionIcon());

                if (result == Messages.YES) {
                    // Choose install directory
                    JFileChooser fc = new JFileChooser();
                    fc.setDialogTitle("Select julc installation directory");
                    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    String defaultDir = System.getProperty("user.home") + File.separator + ".julc";
                    fc.setSelectedFile(new File(defaultDir));

                    if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        Path installDir = fc.getSelectedFile().toPath();
                        JulcDownloader downloader = new JulcDownloader(installDir);
                        downloader.install();
                        IdeaUtil.showNotification(project, "julc",
                                "julc CLI is being downloaded. Please re-create the project after download completes.",
                                NotificationType.INFORMATION, null);
                    }
                }
                return;
            }

            scaffoldWithCli(sdk, basePath, moduleName, template, group, artifact, pkg, project);
        });

        ContentEntry contentEntry = doAddContentEntry(rootModel);
        if (contentEntry != null) {
            List<Pair<String, String>> sourcePaths = getSourcePaths(template);
            for (Pair<String, String> sourcePath : sourcePaths) {
                new File(sourcePath.first).mkdirs();
                VirtualFile sourceRoot = LocalFileSystem.getInstance()
                        .refreshAndFindFileByPath(FileUtil.toSystemIndependentName(sourcePath.first));
                if (sourceRoot != null) {
                    contentEntry.addSourceFolder(sourceRoot, false, sourcePath.second);
                }
            }
        }
    }

    /**
     * Scaffold project using julc CLI's "new" command.
     * This generates proper build files, Gradle/Maven wrappers, and starter code.
     */
    private void scaffoldWithCli(JulcSDK sdk, String basePath, String moduleName,
                                  String template, String group, String artifact,
                                  String pkg, Project project) {
        List<String> commands = sdk.getJulcCommand();
        commands.add("new");
        commands.add(moduleName);
        commands.add("--template");
        commands.add(template);
        commands.add("--group");
        commands.add(group);
        commands.add("--artifact");
        commands.add(artifact);
        commands.add("--package");
        commands.add(pkg);

        try {
            var tempDir = Files.createTempDirectory("julc").toFile();
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.directory(tempDir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                IdeaUtil.showNotification(project, "Project creation",
                        "julc new exited with code " + exitCode, NotificationType.WARNING, null);
            }

            // Copy scaffolded project to basePath
            File srcDir = new File(tempDir, moduleName);
            if (srcDir.exists()) {
                FileUtil.copyDirContent(srcDir, new File(basePath));
            } else {
                // Some versions may scaffold directly into tempDir
                FileUtil.copyDirContent(tempDir, new File(basePath));
            }

        } catch (Exception e) {
            IdeaUtil.showNotification(project, "Project creation",
                    "Failed to create julc project: " + e.getMessage(),
                    NotificationType.ERROR, null);
            LOG.error("julc project creation failed", e);
        }
    }

    @Override
    protected List<WizardInputField<?>> getAdditionalFields() {
        if (templateField == null) {
            templateField = new TemplateInputField("template", "gradle");
        }
        if (groupField == null) {
            groupField = new TextInputField("group", "com.example", "Group ID");
        }
        if (artifactField == null) {
            artifactField = new TextInputField("artifact", "", "Artifact ID");
        }
        if (packageField == null) {
            packageField = new TextInputField("package", "com.example", "Package");
        }
        return Arrays.asList(templateField, groupField, artifactField, packageField);
    }

    @Override
    public ModuleType<?> getModuleType() {
        return ModuleTypeManager.getInstance().getDefaultModuleType();
    }

    private List<Pair<String, String>> getSourcePaths(String template) {
        List<Pair<String, String>> paths = new ArrayList<>();
        String base = getContentEntryPath();

        if ("basic".equals(template)) {
            @NonNls String srcPath = base + File.separator + "src";
            new File(srcPath).mkdirs();
            paths.add(Pair.create(srcPath, ""));
        } else {
            @NonNls String mainPath = base + File.separator + "src" + File.separator + "main" + File.separator + "java";
            @NonNls String testPath = base + File.separator + "src" + File.separator + "test" + File.separator + "java";
            new File(mainPath).mkdirs();
            new File(testPath).mkdirs();
            paths.add(Pair.create(mainPath, ""));
        }

        return paths;
    }

    static class TemplateInputField extends WizardInputField<JComboBox<String>> {
        private final JComboBox<String> combo;

        protected TemplateInputField(String id, String defaultValue) {
            super(id, defaultValue);
            combo = new JComboBox<>(new String[]{"gradle", "maven", "basic"});
            combo.setSelectedItem(defaultValue);
        }

        @Override
        public @NlsContexts.Label String getLabel() {
            return "Template";
        }

        @Override
        public JComboBox<String> getComponent() {
            return combo;
        }

        @Override
        public String getValue() {
            return (String) combo.getSelectedItem();
        }
    }

    static class TextInputField extends WizardInputField<JTextField> {
        private final JTextField textField;
        private final String label;

        protected TextInputField(String id, String defaultValue, String label) {
            super(id, defaultValue);
            this.label = label;
            textField = new JTextField(defaultValue);
        }

        @Override
        public @NlsContexts.Label String getLabel() {
            return label;
        }

        @Override
        public JTextField getComponent() {
            return textField;
        }

        @Override
        public String getValue() {
            return textField.getText();
        }
    }
}
