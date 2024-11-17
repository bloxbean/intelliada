package com.bloxbean.intelliada.idea.aiken.module.project;

import com.bloxbean.intelliada.idea.aiken.common.AikenIcons;
import com.bloxbean.intelliada.idea.aiken.configuration.AikenConfigurationHelperService;
import com.bloxbean.intelliada.idea.aiken.module.AikenContractTemplates;
import com.bloxbean.intelliada.idea.aiken.module.ProjectGeneratorUtil;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectGeneratorPeer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.bloxbean.intelliada.idea.aiken.module.AikenModuleBuilder.build;

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
        if (project == null) {
            IdeaUtil.showNotification(project,
                    "Project creation",
                    "Project creation failed. Unexpected error.",
                    NotificationType.ERROR, null);
            return;
        }

        String basePath = project.getBasePath();

        String moduleName = module.getName();
        String aikenModuleName = moduleName != null ? moduleName.toLowerCase() : moduleName;
        String owner = settings.getOwner();

        if (project != null) {

            ApplicationManager.getApplication().runWriteAction(
                    () -> {
                        var aikenSdk = AikenConfigurationHelperService.getCompilerLocalSDK(project);

                        if (aikenSdk == null) {
                            IdeaUtil.showNotification(project,
                                    "Project creation",
                                    "Aiken executable not found. Please make sure Aiken is installed and added to the PATH",
                                    NotificationType.ERROR, null);
                            return;
                        }

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
                                LOG.error("Aiken project creation error", e);
                                return;
                            }
                        }

                        try {
                            var validatorsFolder = VfsUtil.findFile(Path.of(basePath, "validators"), true);
                            if (validatorsFolder.exists()) {
                                ProjectGeneratorUtil.createNewContract(module.getProject(), validatorsFolder, AikenContractTemplates.AK_HELLOWORLD_TEMPLATE, module.getName().toLowerCase() + ".ak");
                            }

                            build(module);
                        } catch (Exception e) {
                            IdeaUtil.showNotification(project,
                                    "Aiken Contract",
                                    "Error while creating Aiken contract",
                                    NotificationType.ERROR, e.getMessage());
                        }

                        final ModifiableRootModel modifiableModel = ModifiableModelsProvider.getInstance().getModuleModifiableModel(module);
                        modifiableModel.commit();
                    });
        }
    }

    @Override
    public @NotNull ProjectGeneratorPeer<ProjectCreateSettings> createPeer() {
        return new AikenProjectGeneratorPeer();
    }
}
