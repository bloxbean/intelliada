package com.bloxbean.intelliada.idea.toolwindow.action;

import com.bloxbean.intelliada.idea.configuration.service.ConfigurationHelperService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class SetDefaultProviderAction extends AnAction {
    private String defaultProvider;

    public SetDefaultProviderAction(String defaultProvider) {
        super("Set as default", "Set as default provider", AllIcons.Actions.SetDefault);
        this.defaultProvider = defaultProvider;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ConfigurationHelperService.setDefaultCLIProvider(defaultProvider);
    }
}
