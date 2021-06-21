package com.bloxbean.intelliada.idea.configuration.action;

import com.bloxbean.intelliada.idea.configuration.service.ConfigurationHelperService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class CreateCLIProviderAction extends AnAction {
    public final static String ACTION_ID = CreateCLIProviderAction.class.getName();

    public CreateCLIProviderAction() {
        super("Add Cardano CLI Provider", "Add a Cardano CLI Provider", AllIcons.General.Add);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();

        ConfigurationHelperService.createOrUpdateLocalSDKConfiguration(project, null);
    }

    @Override
    public boolean isDumbAware() {
        return true;
    }
}
