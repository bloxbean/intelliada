package com.bloxbean.intelliada.idea.configuration.action;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.configuration.service.ConfigurationHelperService;
import com.bloxbean.intelliada.idea.nodeint.service.NodeServiceFactory;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class UpdateRemoteNodeAction extends AnAction {
    private RemoteNode node;

    public UpdateRemoteNodeAction(RemoteNode node) {
        super("Edit", "Edit this Cardano Node", AllIcons.General.Settings);
        this.node = node;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();

        if(node != null) {
            ConfigurationHelperService.createOrUpdateRemoteNodeConfiguration(project, node);
            NodeServiceFactory.getInstance().nodeRemoved(node);
        }
    }
}
