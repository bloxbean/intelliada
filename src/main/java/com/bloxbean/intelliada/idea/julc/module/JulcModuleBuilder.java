package com.bloxbean.intelliada.idea.julc.module;

import com.bloxbean.intelliada.idea.julc.common.JulcIcons;
import com.bloxbean.intelliada.idea.julc.configuration.JulcConfigurationHelperService;
import com.bloxbean.intelliada.idea.julc.configuration.JulcDownloader;
import com.bloxbean.intelliada.idea.julc.configuration.JulcSDK;
import com.bloxbean.intelliada.idea.julc.configuration.service.JulcSDKState;
import com.bloxbean.intelliada.idea.julc.util.JulcSdkUtil;
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
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JulcModuleBuilder extends ModuleBuilder implements ModuleBuilderListener {
    private static final Logger LOG = Logger.getInstance(JulcModuleBuilder.class);
    private static final ProjectSystemId GRADLE_SYSTEM_ID = new ProjectSystemId("GRADLE");

    private JulcSdkInputField sdkField;
    private TemplateInputField templateField;
    private TextInputField groupField;
    private TextInputField packageField;

    public JulcModuleBuilder() {
        addListener(this);
    }

    @Override
    public String getBuilderId() { return "Julc"; }

    @Override
    public Icon getNodeIcon() { return JulcIcons.JULC_ICON; }

    @Override
    public String getDescription() { return "julc - Write Cardano Smart Contracts in Java"; }

    @Override
    public String getPresentableName() { return "julc"; }

    @Override
    public String getGroupName() { return "julc"; }

    @Override
    public ModuleWizardStep[] createWizardSteps(com.intellij.ide.util.projectWizard.WizardContext wizardContext, ModulesProvider modulesProvider) {
        return new ModuleWizardStep[]{};
    }

    @Override
    public void moduleCreated(@NotNull Module module) {
        Project project = module.getProject();
        String basePath = project.getBasePath();
        if (basePath == null) return;

        ApplicationManager.getApplication().invokeLater(() -> {
            VirtualFile baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath);
            if (baseDir != null) {
                VfsUtil.markDirtyAndRefresh(false, true, true, baseDir);
            }

            try {
                VirtualFile buildFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath + "/build.gradle");
                if (buildFile != null) {
                    ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized(() -> {
                        try {
                            ExternalSystemUtil.refreshProject(basePath, new ImportSpecBuilder(project, GRADLE_SYSTEM_ID));
                            LOG.info("Triggered Gradle import for julc project at " + basePath);
                        } catch (Exception e) {
                            LOG.warn("Gradle import trigger failed", e);
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
        String pkg = packageField != null ? packageField.getValue() : group;

        // Get SDK from the wizard field
        JulcSDK sdk = sdkField != null ? sdkField.getResolvedSDK() : null;
        if (sdk == null) {
            sdk = JulcConfigurationHelperService.getCompilerLocalSDK(rootModel.getProject());
        }

        if (sdk == null) {
            throw new ConfigurationException(
                    "julc CLI is not configured. Please select or download a julc SDK in the wizard.",
                    "julc SDK Required");
        }

        Project project = rootModel.getProject();
        String basePath = project.getBasePath();

        // Save SDK to state for future use
        final JulcSDK finalSdk = sdk;
        ApplicationManager.getApplication().runWriteAction(() -> {
            scaffoldWithCli(finalSdk, basePath, moduleName, template, group, pkg, project);
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

    private void scaffoldWithCli(JulcSDK sdk, String basePath, String moduleName,
                                  String template, String group, String pkg, Project project) {
        List<String> commands = sdk.getJulcCommand();
        commands.add("new");
        commands.add(moduleName);
        commands.add("--template");
        commands.add(template);
        commands.add("--group");
        commands.add(group);
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

            File srcDir = new File(tempDir, moduleName);
            if (srcDir.exists()) {
                FileUtil.copyDirContent(srcDir, new File(basePath));
            } else {
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
        if (sdkField == null) {
            sdkField = new JulcSdkInputField("julcSdk");
        }
        if (templateField == null) {
            templateField = new TemplateInputField("template", "gradle");
        }
        if (groupField == null) {
            groupField = new TextInputField("group", "com.example", "Group ID");
        }
        if (packageField == null) {
            packageField = new TextInputField("package", "com.example", "Package");
        }
        return Arrays.asList(sdkField, templateField, groupField, packageField);
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

    // ========== Custom Wizard Fields ==========

    /**
     * julc SDK selector field shown in the project wizard.
     * User must select or download a julc SDK before proceeding.
     */
    static class JulcSdkInputField extends WizardInputField<JPanel> {
        private JPanel panel;
        private JTextField pathTf;
        private JLabel statusLabel;
        private JulcSDK resolvedSdk;

        protected JulcSdkInputField(String id) {
            super(id, "");
            buildPanel();
            // Try to auto-detect existing SDK
            autoDetect();
        }

        private void buildPanel() {
            panel = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(2, 2, 2, 2);
            c.anchor = GridBagConstraints.WEST;

            pathTf = new JTextField(25);
            TextFieldWithBrowseButton browse = new TextFieldWithBrowseButton(pathTf, e -> {
                JFileChooser fc = new JFileChooser();
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                fc.setDialogTitle("Select folder containing 'julc' executable");
                if (fc.showDialog(panel, "Select") == JFileChooser.APPROVE_OPTION) {
                    pathTf.setText(fc.getSelectedFile().getAbsolutePath());
                    validatePath();
                }
            });
            c.gridx = 0; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
            panel.add(browse, c);

            JButton downloadBtn = new JButton("Download");
            downloadBtn.addActionListener(e -> downloadJulc());
            c.gridx = 1; c.fill = GridBagConstraints.NONE; c.weightx = 0;
            panel.add(downloadBtn, c);

            statusLabel = new JLabel(" ");
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));
            c.gridx = 0; c.gridy = 1; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
            panel.add(statusLabel, c);

            pathTf.addActionListener(e -> validatePath());
            pathTf.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    validatePath();
                }
            });
        }

        private void autoDetect() {
            // Check existing SDKs
            List<JulcSDK> sdks = JulcSDKState.getInstance().getSdks();
            if (!sdks.isEmpty()) {
                JulcSDK sdk = sdks.get(0);
                pathTf.setText(sdk.getPath());
                resolvedSdk = sdk;
                statusLabel.setText("Using: " + sdk.getName() + " (" + sdk.getVersion() + ")");
                statusLabel.setForeground(new Color(0, 128, 0));
                return;
            }

            // Check common locations
            String[] fallbacks = {
                    System.getProperty("user.home") + "/.julc/bin",
                    "/usr/local/bin"
            };
            String exe = JulcSdkUtil.getJulcExecutable();
            for (String dir : fallbacks) {
                if (new File(dir, exe).exists()) {
                    pathTf.setText(dir);
                    validatePath();
                    return;
                }
            }

            // Check ~/.intelliada/julc-cli/ (our download location)
            String downloadBase = System.getProperty("user.home") + "/.intelliada/julc-cli";
            String foundDir = JulcDownloader.findJulcBinDir(Path.of(downloadBase));
            if (foundDir != null) {
                pathTf.setText(foundDir);
                validatePath();
                return;
            }

            statusLabel.setText("No julc found. Browse to installation or click Download.");
            statusLabel.setForeground(Color.GRAY);
        }

        private void validatePath() {
            String path = pathTf.getText().trim();
            if (path.isEmpty()) {
                resolvedSdk = null;
                statusLabel.setText("Select julc installation path");
                statusLabel.setForeground(Color.GRAY);
                return;
            }

            String exe = JulcSdkUtil.getJulcExecutable();
            if (!new File(path, exe).exists()) {
                // Maybe path includes the executable itself
                File f = new File(path);
                if (f.isFile() && f.getName().startsWith("julc")) {
                    path = f.getParent();
                    pathTf.setText(path);
                }
                if (!new File(path, exe).exists()) {
                    resolvedSdk = null;
                    statusLabel.setText("'julc' not found in this directory");
                    statusLabel.setForeground(Color.RED);
                    return;
                }
            }

            // Try to get version
            String version = "unknown";
            try {
                version = JulcSdkUtil.getVersionString(path);
            } catch (Exception ex) {
                // ignore
            }

            final String resolvedPath = path;
            resolvedSdk = new JulcSDK(UUID.randomUUID().toString(), "julc", resolvedPath, version != null ? version : "unknown");

            // Save to SDK state for persistence
            JulcSDKState state = JulcSDKState.getInstance();
            boolean exists = state.getSdks().stream().anyMatch(s -> s.getPath().equals(resolvedPath));
            if (!exists) {
                state.addSdk(resolvedSdk);
            }

            statusLabel.setText("julc " + (version != null ? version : "") + " — " + resolvedPath);
            statusLabel.setForeground(new Color(0, 128, 0));
        }

        private void downloadJulc() {
            String defaultDir = System.getProperty("user.home") + File.separator + ".intelliada" + File.separator + "julc-cli";
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Select download directory for julc CLI");
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setSelectedFile(new File(defaultDir));

            if (fc.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) return;

            Path installDir = fc.getSelectedFile().toPath();
            statusLabel.setText("Downloading julc...");
            statusLabel.setForeground(new Color(200, 150, 0));

            JulcDownloader downloader = new JulcDownloader(installDir);
            downloader.setOnComplete(() -> {
                String binDir = JulcDownloader.findJulcBinDir(installDir);
                if (binDir != null) {
                    SwingUtilities.invokeLater(() -> {
                        pathTf.setText(binDir);
                        validatePath();
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Download complete. Browse to the julc executable directory.");
                        statusLabel.setForeground(new Color(200, 150, 0));
                    });
                }
            });
            downloader.install();
        }

        public JulcSDK getResolvedSDK() {
            return resolvedSdk;
        }

        @Override
        public @NlsContexts.Label String getLabel() { return "julc SDK"; }

        @Override
        public JPanel getComponent() { return panel; }

        @Override
        public String getValue() {
            return pathTf.getText();
        }
    }

    static class TemplateInputField extends WizardInputField<JComboBox<String>> {
        private final JComboBox<String> combo;

        protected TemplateInputField(String id, String defaultValue) {
            super(id, defaultValue);
            combo = new JComboBox<>(new String[]{"gradle", "maven", "basic"});
            combo.setSelectedItem(defaultValue);
        }

        @Override
        public @NlsContexts.Label String getLabel() { return "Template"; }

        @Override
        public JComboBox<String> getComponent() { return combo; }

        @Override
        public String getValue() { return (String) combo.getSelectedItem(); }
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
        public @NlsContexts.Label String getLabel() { return label; }

        @Override
        public JTextField getComponent() { return textField; }

        @Override
        public String getValue() { return textField.getText(); }
    }
}
