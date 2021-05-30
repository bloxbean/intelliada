package com.bloxbean.intelliada.idea.configuration.action;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.configuration.service.ConfigurationHelperService;
import com.bloxbean.intelliada.idea.nodeint.service.NodeServiceFactory;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class DeleteRemoteNodeAction extends AnAction {
    private RemoteNode node;

    public DeleteRemoteNodeAction(RemoteNode node) {
        super("Delete", "Delete this Cardano Node", AllIcons.General.Remove);
        this.node = node;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if(project == null) return;

        int result = Messages.showYesNoDialog("Do you really want to delete this Cardano node configuration ?", "Cardano Node Configuration", AllIcons.General.QuestionDialog);

        if(result == Messages.NO)
            return;

        if(node != null) {
            ConfigurationHelperService.deleteRemoteNodeConfiguration(node);
            NodeServiceFactory.getInstance().nodeRemoved(node);
        }
    }
}
