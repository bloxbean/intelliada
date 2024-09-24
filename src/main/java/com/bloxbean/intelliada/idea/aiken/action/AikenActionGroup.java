package com.bloxbean.intelliada.idea.aiken.action;

import com.bloxbean.intelliada.idea.aiken.common.AikenIcons;
import com.bloxbean.intelliada.idea.aiken.module.AikenModuleType;
import com.bloxbean.intelliada.idea.aiken.module.pkg.AikenTomlService;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;

public class AikenActionGroup extends DefaultActionGroup {
    @Override
    public void update(AnActionEvent event) {
        Project project = event.getProject();

        DataContext dataContext = event.getDataContext();
        final Module module = LangDataKeys.MODULE.getData(dataContext);

        final ModuleType moduleType = module == null ? null : ModuleType.get(module);
        boolean isAikenModule = moduleType instanceof AikenModuleType;

        //Try to check if aiken.toml file available.
        //For non-Aiken modules
        if(!isAikenModule) {
            AikenTomlService aikenTomlService = AikenTomlService.getInstance(project);
            if (aikenTomlService != null)
                isAikenModule = aikenTomlService.isAikenProject();
        }

        if(isAikenModule) {
            event.getPresentation().setVisible(true);
            event.getPresentation().setIcon(AikenIcons.AIKEN_ICON);
        } else {
            event.getPresentation().setVisible(false);
            event.getPresentation().setIcon(AikenIcons.AIKEN_ICON);
        }
    }
}
