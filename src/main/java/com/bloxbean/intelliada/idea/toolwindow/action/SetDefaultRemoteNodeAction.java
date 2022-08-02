package com.bloxbean.intelliada.idea.toolwindow.action;

import com.bloxbean.intelliada.idea.configuration.service.ConfigurationHelperService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class SetDefaultRemoteNodeAction extends AnAction {
    private String defaultNode;

    public SetDefaultRemoteNodeAction(String defaultNode) {
        super("Set as default", "Set as default node", AllIcons.Actions.SetDefault);
        this.defaultNode = defaultNode;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ConfigurationHelperService.setDefaultRemoteNode(defaultNode);
    }

    @Override
    public boolean isDumbAware() {
        return true;
    }
}
