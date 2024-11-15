package com.bloxbean.intelliada.idea.aiken.action;

import com.bloxbean.intelliada.idea.aiken.common.AikenIcons;
import com.bloxbean.intelliada.idea.aiken.module.AikenModuleType;
import com.bloxbean.intelliada.idea.aiken.module.pkg.AikenTomlService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AikenActionGroup extends DefaultActionGroup {
    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            event.getPresentation().setVisible(false);
            return;
        }

        DataContext dataContext = event.getDataContext();

        // Wrap logic in ReadAction.nonBlocking or ReadAction.compute to move off EDT
        boolean isAikenModule = ReadAction.compute(() -> {
            final Module module = LangDataKeys.MODULE.getData(dataContext);
            if (module == null) return false;

            final ModuleType moduleType = ModuleType.get(module);
            return moduleType instanceof AikenModuleType;
        });

        // For non-Aiken modules, check if aiken.toml is available in background
        if (!isAikenModule) {
            isAikenModule = ReadAction.nonBlocking(() -> {
                AikenTomlService aikenTomlService = AikenTomlService.getInstance(project);
                return aikenTomlService != null && aikenTomlService.isAikenProject();
            }).executeSynchronously();
        }

        event.getPresentation().setVisible(isAikenModule);
        event.getPresentation().setIcon(AikenIcons.AIKEN_ICON);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
