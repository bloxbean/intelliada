package com.bloxbean.intelliada.idea.configuration.action;

import com.bloxbean.intelliada.idea.configuration.model.CLIProvider;
import com.bloxbean.intelliada.idea.configuration.service.ConfigurationHelperService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class UpdateCLIProviderAction extends AnAction {
    private CLIProvider provider;

    public UpdateCLIProviderAction(CLIProvider provider) {
        super("Edit", "Edit this Cardano Node", AllIcons.General.Settings);
        this.provider = provider;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();

        if(provider != null)
            ConfigurationHelperService.createOrUpdateLocalSDKConfiguration(project, provider);
    }
}
