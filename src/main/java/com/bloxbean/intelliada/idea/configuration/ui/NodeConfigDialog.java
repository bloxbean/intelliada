package com.bloxbean.intelliada.idea.configuration.ui;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class NodeConfigDialog extends DialogWrapper {

    private CardanoNodeConfigDialog cardanoNodeConfigDialog;

    public NodeConfigDialog(@Nullable Project project, RemoteNode remoteNode) {
        super(project, true);
        setTitle("Cardano Remote Node Configuration");
        cardanoNodeConfigDialog = new CardanoNodeConfigDialog(project, remoteNode);
        init();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        return cardanoNodeConfigDialog.doValidate();
    }

    public NodeConfigurator getNodeConfigurator() {
        return cardanoNodeConfigDialog.getNodeConfigurator();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return cardanoNodeConfigDialog.getMainPanel();
    }
}
