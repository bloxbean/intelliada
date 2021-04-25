package com.bloxbean.intelliada.idea.configuration.ui;

import com.bloxbean.intelliada.idea.core.util.Network;
import com.bloxbean.intelliada.idea.core.util.NetworkUrls;
import com.bloxbean.intelliada.idea.core.util.Networks;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RemoteNodeConfigPanel {
    private JPanel mainPanel;
    private JTextField nameTf;
    private JButton testConnectionBtn;
    private JComboBox nodeTypesCB;
    private JTextField authKey;
    private JLabel authKeyLabel;
    private JTextField networkTf;
    private JTextField networkIdTf;
    private JTextField protocolMagicTf;
    private JTextField apiEndpointTf;
    private boolean newConfig = true;

    public RemoteNodeConfigPanel() {
        this(null);
    }

    public RemoteNodeConfigPanel(RemoteNode node) {
        super();

        initialize();
        if(node != null) {
            newConfig = false;
            nameTf.setText(node.getName());
            nodeTypesCB.setSelectedItem(node.getNodeType());
            apiEndpointTf.setText(node.getApiEndpoint());
            authKey.setText(node.getAuthKey());
            networkTf.setText(node.getNetwork());
            networkIdTf.setText(node.getNetworkId());
            protocolMagicTf.setText(node.getProtocolMagic());
        }
    }

    private void initialize() {
        handleNodeTypeSelection();
    }

    private void handleNodeTypeSelection() {
        nodeTypesCB.addActionListener(e -> {
            if(NodeType.BLOCKFROST_TESTNET.equals(nodeTypesCB.getSelectedItem())
                    || NodeType.BLOCKFROST_MAINNET.equals(nodeTypesCB.getSelectedItem())) {
                apiEndpointTf.setEnabled(false);
                authKeyLabel.setText("Project Id");

                if(NodeType.BLOCKFROST_TESTNET.equals(nodeTypesCB.getSelectedItem())) {
                    apiEndpointTf.setText(NetworkUrls.BLOCKFROST_TESTNET_BASEURL);
                    setNetwork(Networks.testnet());
                } else if(NodeType.BLOCKFROST_MAINNET.equals(nodeTypesCB.getSelectedItem())) {
                    apiEndpointTf.setText(NetworkUrls.BLOCKFROST_MAINNET_BASEURL);
                    setNetwork(Networks.mainnet());
                }

            } else {
                apiEndpointTf.setEnabled(true);
                authKeyLabel.setText("Auth Key");
            }
        });
    }

    private void setNetwork(Network network) {
        if(network != null) {
            networkTf.setText(network.getName());
            networkIdTf.setText(network.getNetworkId());
            protocolMagicTf.setText(network.getProtocolMagic());
        }
    }

    public @Nullable ValidationInfo doValidate() {
        if (StringUtil.isEmpty(getName())) {
            return new ValidationInfo("Please enter a valid name", nameTf);
        }

        if(getNodeType() == null || getNodeType().equals(NodeType.EMPTY)) {
            return new ValidationInfo("Please select a valid node type", nodeTypesCB);
        }

        if(StringUtil.isEmpty(getApiEndpoint())) {
            return new ValidationInfo("Please enter a valid api endpoint url", apiEndpointTf);
        }

        if(getApiEndpoint().contains("blockfrost.io") && StringUtil.isEmpty(getAuthKey())) {
            return new ValidationInfo("Please enter a valid project id", authKey);
        }

        return null;
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public String getName() {
        return StringUtil.trim(nameTf.getText());
    }

    public String getApiEndpoint() {
        return StringUtil.trim(apiEndpointTf.getText());
    }

    public String getAuthKey() {
        return authKey.getText();
    }

    public NodeType getNodeType() {
        return (NodeType)nodeTypesCB.getSelectedItem();
    }

    public String getNetwork() {
        return networkTf.getText();
    }

    public String getNetworkId() {
        return networkIdTf.getText();
    }

    public String getProtocolMagic() {
        return protocolMagicTf.getText();
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        nodeTypesCB = new ComboBox(new NodeType[]{NodeType.EMPTY, NodeType.BLOCKFROST_TESTNET, NodeType.BLOCKFROST_MAINNET});
    }
}
