package com.bloxbean.intelliada.idea.configuration.ui;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class NodeConfigDialog extends DialogWrapper {

    private RemoteNodeConfigPanel remoteNodeConfigPanel;
    public NodeConfigDialog(@Nullable Project project, RemoteNode remoteNode) {
        super(project, true);
        remoteNodeConfigPanel = new RemoteNodeConfigPanel(remoteNode);
        init();
    }

    public RemoteNodeConfigPanel getRemoteNodeConfigPanel() {
        return remoteNodeConfigPanel;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return remoteNodeConfigPanel.getMainPanel();
    }
}
