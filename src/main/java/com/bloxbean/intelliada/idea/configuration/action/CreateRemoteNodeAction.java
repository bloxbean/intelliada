package com.bloxbean.intelliada.idea.configuration.action;

import com.bloxbean.intelliada.idea.configuration.service.ConfigurationHelperService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class CreateRemoteNodeAction extends AnAction {
    public final static String ACTION_ID = CreateRemoteNodeAction.class.getName();

    public CreateRemoteNodeAction() {
        super("Add Remote Cardano Node", "Add a new Cardano Node", AllIcons.General.Add);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();

        ConfigurationHelperService.createOrUpdateRemoteNodeConfiguration(project, null);
    }

}
