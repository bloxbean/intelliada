package com.bloxbean.intelliada.idea.configuration.ui;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;

public class RemoteNodeConfigPanel {
    private JPanel mainPanel;
    private JTextField nameTf;
    private JTextField walletApiEndpoint;
    private JButton testConnectionBtn;

    public RemoteNodeConfigPanel() {
        this(null);
    }

    public RemoteNodeConfigPanel(RemoteNode node) {
        super();

        if(node != null) {
            nameTf.setText(node.getName());
            walletApiEndpoint.setText(node.getWalletApiEndpoint());
        }
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public String getName() {
        return StringUtil.trim(nameTf.getText());
    }

    public String getWalletApiEndPoint() {
        return StringUtil.trim(walletApiEndpoint.getText());
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }
}
