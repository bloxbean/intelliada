package com.bloxbean.intelliada.idea.aiken.module.project;

import com.bloxbean.intelliada.idea.aiken.common.AikenIcons;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGeneratorBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public class AikenDirectoryProjectGenerator extends DirectoryProjectGeneratorBase<ProjectCreateSettings> {
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
                        List<String> commands = Arrays.asList(System.getProperty("user.home") + File.separator + ".aiken" + File.separator + "bin" + File.separator + "aiken");
                        commands.add("new");
                        commands.add(owner + "/" + moduleName);
                        try {
                            ProcessBuilder pb = new ProcessBuilder(commands);
                            pb.directory(new File(basePath));
                            pb.redirectErrorStream(true);
                            Process process = pb.start();
                            process.waitFor();
                        } catch (Exception e) {
                            IdeaUtil.showNotification(project,
                                    "Project creation",
                                    "Aiken executable not found. Please make sure Aiken is installed and added to the PATH",
                                    NotificationType.ERROR, null);
                            if (LOG.isDebugEnabled()) {
                                LOG.error("Aiken executable not found", e);
                                return;
                            }
                        }

                        final ModifiableRootModel modifiableModel = ModifiableModelsProvider.getInstance().getModuleModifiableModel(module);
                        modifiableModel.commit();
                    });
        }
    }

}
