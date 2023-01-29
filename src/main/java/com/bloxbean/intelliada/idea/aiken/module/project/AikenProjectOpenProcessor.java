package com.bloxbean.intelliada.idea.aiken.module.project;

import com.bloxbean.intelliada.idea.aiken.module.AikenModuleConstant;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AikenProjectOpenProcessor extends ProjectOpenProcessor {
    @Override
    public @NotNull @Nls String getName() {
        return "Aiken";
    }

    @Override
    public boolean canOpenProject(@NotNull VirtualFile file) {
        VirtualFile aikenToml = file.findChild(AikenModuleConstant.AIKEN_TOML);
        return aikenToml != null? true : false;
    }

    @Override
    public @Nullable Project doOpenProject(@NotNull VirtualFile virtualFile, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
        if(virtualFile == null || !virtualFile.isDirectory())
            return null;

        ProjectOpenProcessor processor = PlatformProjectOpenProcessor.getInstance();
        Project project = processor.doOpenProject(virtualFile, projectToClose, forceOpenInNewFrame);

        if(project == null)
            return null;

        VirtualFile pkgJson = virtualFile.findChild(AikenModuleConstant.AIKEN_TOML);
        if(pkgJson == null)
            return null;

        VirtualFile validatorVFile = virtualFile.findChild("validators");
        if(validatorVFile == null || !validatorVFile.isDirectory())
            return null;

        Module module = ModuleUtil.findModuleForFile(validatorVFile, project);
        if(module == null)
            return null;

        final ModifiableRootModel modifiableModel = ModifiableModelsProvider.SERVICE.getInstance().getModuleModifiableModel(module);
        ContentEntry[] contentEntries = modifiableModel.getContentEntries();
        if(contentEntries != null && contentEntries.length > 0)
            return null;

        String basePath = project.getBasePath();
        //No content entry defined. Add one
        ApplicationManager.getApplication().runWriteAction(
                () -> {
                    try {
                        final VirtualFile moduleContentRoot =
                                LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath.replace('\\', '/'));

                        ContentEntry contentEntry = modifiableModel.addContentEntry(moduleContentRoot);
                        contentEntry.addSourceFolder(validatorVFile, false);

                        modifiableModel.commit();
                    } catch (Exception e) {
                        IdeaUtil.showNotification(project,
                                "Project creation",
                                "Sources Root could not be created or marked properly. You can manually create and mark a folder as Sources Root",
                                NotificationType.WARNING, null);
                    }
                });

        return project;
    }
}
