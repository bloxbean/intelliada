package com.bloxbean.intelliada.idea.aiken.module.project;

import com.bloxbean.intelliada.idea.aiken.common.AikenIcons;
import com.bloxbean.intelliada.idea.aiken.module.pkg.AikenTomlService;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

public class AikenDirectoryProjectGenerator extends WebProjectTemplate<ProjectCreateSettings> {
    private final static Logger LOG = Logger.getInstance(AikenDirectoryProjectGenerator.class);

    @Override
    public @NlsContexts.DetailedDescription String getDescription() {
        return "Aiken Smart Contract";
    }

    @Override
    public @NotNull @NlsContexts.Label String getName() {
        return "Aiken";
    }

    @Override
    public Icon getLogo() {
        return AikenIcons.MODULE;
    }

    @Override
    public void generateProject(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull ProjectCreateSettings settings, @NotNull Module module) {
        if(project == null) {
            IdeaUtil.showNotification(project,
                    "Project creation",
                    "Project creation failed. Unexpected error.",
                    NotificationType.ERROR, null);
            return;
        }

        String basePath = project.getBasePath();

        String moduleName = module.getName();
        String owner = System.getProperty("user.name");

        if (project != null) {

            ApplicationManager.getApplication().runWriteAction(
                    () -> {
                        try {
                            File libDir = new File(basePath + File.separator + "lib");
                            libDir.mkdirs();

                            File validators = new File(basePath + File.separator + "validators");
                            libDir.mkdirs();

                            final ModifiableRootModel modifiableModel = ModifiableModelsProvider.SERVICE.getInstance().getModuleModifiableModel(module);

                            final VirtualFile moduleContentRoot =
                                    LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath.replace('\\', '/'));

                            final VirtualFile libSourceRoot = LocalFileSystem.getInstance()
                                    .refreshAndFindFileByPath(FileUtil.toSystemIndependentName(libDir.getAbsolutePath()));
                            final VirtualFile validatorSourceRoot = LocalFileSystem.getInstance()
                                    .refreshAndFindFileByPath(FileUtil.toSystemIndependentName(validators.getAbsolutePath()));


                            ContentEntry contentEntry = modifiableModel.addContentEntry(moduleContentRoot);
                            contentEntry.addSourceFolder(libSourceRoot, false);
                            contentEntry.addSourceFolder(validatorSourceRoot, false);

                            modifiableModel.commit();

                            //Create aiken.toml
                            try {
                                AikenTomlService.getInstance(project).createAikenToml(owner, moduleName);
                            } catch (Exception e) {
                                IdeaUtil.showNotification(project,
                                        "Project creation",
                                        "aiken.toml could not be crated. Please create it " +
                                                "manually and restart the IDE.", NotificationType.WARNING, null);
                                if (LOG.isDebugEnabled()) {
                                    LOG.error("Unable to create aiken.toml", e);
                                    return;
                                }
                            }

                            //Create stateful contracts
                            if(settings instanceof ProjectCreateSettings) {
//                                ProjectCreateSettings projectCreateSettings = (ProjectCreateSettings) settings;
//                                if(!StringUtil.isEmpty(projectCreateSettings.contractName)) {
//                                    ProjectGeneratorUtil.createStatefulContractFiles(project, sourceRoot, projectCreateSettings.contractName,
//                                            projectCreateSettings.approvalProgram, projectCreateSettings.clearStateProgram);
//                                }
                            }

                        } catch (Exception e) {
                            IdeaUtil.showNotification(project,
                                    "Project creation",
                                    "Sources Root could not be created or marked properly. You can manually create and mark a folder as Sources Root",
                                    NotificationType.WARNING, null);
                        }
                    });
        }
    }
}
