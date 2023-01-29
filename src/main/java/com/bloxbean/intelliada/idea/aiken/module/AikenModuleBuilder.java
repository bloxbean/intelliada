package com.bloxbean.intelliada.idea.aiken.module;

import com.bloxbean.intelliada.idea.aiken.common.AikenIcons;
import com.bloxbean.intelliada.idea.aiken.module.pkg.AikenTomlService;
import com.bloxbean.intelliada.idea.aiken.module.ui.AikenModuleWizardStep;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AikenModuleBuilder extends ModuleBuilder implements ModuleBuilderListener {
    private static final Logger LOG = Logger.getInstance(AikenModuleBuilder.class);

    private List<Pair<String,String>> mySourcePaths;

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
        return "Aiken Smart Contract";
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
                            ProjectGeneratorUtil.createNewContract(module.getProject(), srcRoot, AikenContractTemplates.AK_HELLOWORLD_TEMPLATE, module.getName() + ".ak");
//                            try {
//                                VirtualFile test = topFolder.createChildDirectory(this, "test");
//                                if (test != null) {
//                                    ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
//                                    ContentEntry entry = findContentEntry(model, test);
//                                    if (entry != null) {
//                                        entry.addSourceFolder(test, true);
//                                        model.commit();
//                                    }
//                                }
//                            } catch (IOException e) {
//                            }
                        }
                    });
                }
            }
        }
    }

    @Override
    public void setupRootModel(@NotNull ModifiableRootModel rootModel) throws ConfigurationException {
        rootModel.inheritSdk();

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

        String moduleName = rootModel.getModule().getName();
        String owner = System.getProperty("user.name");

        Project project = rootModel.getProject();
        if(project != null) {
            //Create aiken.toml
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    AikenTomlService.getInstance(project).createAikenToml(owner, moduleName);

//                    WriteCommandAction.runWriteCommandAction(project, () -> {
//                        ProjectGeneratorUtil.createStatefulContractFiles(project, srcRoots[0], statefulContractName,
//                                approvalProgramName, clearStateProgramName);
//                    });
                } catch (Exception e) {
                    IdeaUtil.showNotification(project,
                            "Project creation",
                            "aiken.toml could not be crated. Please create it " +
                                    "manually and restart the IDE.", NotificationType.WARNING, null);
                    if(LOG.isDebugEnabled()) {
                        LOG.error("Unable to create aiken.toml", e);
                    }
                }
            });
        }
    }

    @Override
    protected List<WizardInputField<?>> getAdditionalFields() {
        return Collections.emptyList();
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

    public void setSourcePaths(List<Pair<String, String>> sourcePaths) {
        mySourcePaths = sourcePaths != null ? new ArrayList<>(sourcePaths) : null;
    }

    public void addSourcePath(Pair<String, String> sourcePathInfo) {
        if (mySourcePaths == null) {
            mySourcePaths = new ArrayList<>();
        }
        mySourcePaths.add(sourcePathInfo);
    }
}
