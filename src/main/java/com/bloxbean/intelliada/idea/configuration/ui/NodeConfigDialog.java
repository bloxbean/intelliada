package com.bloxbean.intelliada.idea.configuration.ui;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class NodeConfigDialog extends DialogWrapper {

    private RemoteNodeConfigPanel remoteNodeConfigPanel;
    public NodeConfigDialog(@Nullable Project project, RemoteNode remoteNode) {
        super(project, true);
        setTitle("Cardano Remote Node Configuration");
        remoteNodeConfigPanel = new RemoteNodeConfigPanel(remoteNode);
        init();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        return remoteNodeConfigPanel.doValidate();
    }

    public RemoteNodeConfigPanel getRemoteNodeConfigPanel() {
        return remoteNodeConfigPanel;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return remoteNodeConfigPanel.getMainPanel();
    }
}
