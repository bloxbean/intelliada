package com.bloxbean.intelliada.idea.configuration.ui;

import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.Network;
import com.bloxbean.intelliada.idea.core.util.NetworkUrls;
import com.bloxbean.intelliada.idea.core.util.Networks;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.api.NetworkInfoService;
import com.bloxbean.intelliada.idea.nodeint.service.impl.NetworkServiceImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.UUID;

//Config panel for Blockfrost api
public class RemoteNodeConfigPanel implements NodeConfigurator {
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
    private JLabel connectionTestLabel;
    private boolean newConfig = true;

    public RemoteNodeConfigPanel() {
        this(null);
    }

    public RemoteNodeConfigPanel(RemoteNode node) {
        super();

        initialize();
    }

    private void initialize() {
        handleNodeTypeSelection();

        testConnectionBtn.addActionListener(e -> {
            testNetworkConnection();
        });
    }

    @Override
    public void setNodeData(RemoteNode node) {
        if (node != null) {
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

    private void handleNodeTypeSelection() {
        nodeTypesCB.addActionListener(e -> {
            if (NodeType.BLOCKFROST_TESTNET.equals(nodeTypesCB.getSelectedItem())
                    || NodeType.BLOCKFROST_MAINNET.equals(nodeTypesCB.getSelectedItem())
                    || NodeType.BLOCKFROST_PREPOD.equals(nodeTypesCB.getSelectedItem())
                    || NodeType.BLOCKFROST_PREVIEW.equals(nodeTypesCB.getSelectedItem())
                    || NodeType.BLOCKFROST_CUSTOM.equals(nodeTypesCB.getSelectedItem())
            ) {
                apiEndpointTf.setEnabled(false);
                authKeyLabel.setText("Project Id");

                if (NodeType.BLOCKFROST_TESTNET.equals(nodeTypesCB.getSelectedItem())) {
                    apiEndpointTf.setText(NetworkUrls.BLOCKFROST_TESTNET_BASEURL);
                    setNetwork(Networks.testnet());
                } else if (NodeType.BLOCKFROST_MAINNET.equals(nodeTypesCB.getSelectedItem())) {
                    apiEndpointTf.setText(NetworkUrls.BLOCKFROST_MAINNET_BASEURL);
                    setNetwork(Networks.mainnet());
                } else if (NodeType.BLOCKFROST_PREPOD.equals(nodeTypesCB.getSelectedItem())) {
                    apiEndpointTf.setText(NetworkUrls.BLOCKFROST_PREPOD_BASEURL);
                    setNetwork(Networks.prepod());
                } else if (NodeType.BLOCKFROST_PREVIEW.equals(nodeTypesCB.getSelectedItem())) {
                    apiEndpointTf.setText(NetworkUrls.BLOCKFROST_PREVIEW_BASEURL);
                    setNetwork(Networks.preview());
                } else if (NodeType.BLOCKFROST_CUSTOM.equals(nodeTypesCB.getSelectedItem())) {
                    apiEndpointTf.setEnabled(true);
                    setNetwork(Networks.testnet());
                }

            } else {
                apiEndpointTf.setEnabled(true);
                authKeyLabel.setText("Auth Key");
            }
        });
    }

    private void setNetwork(Network network) {
        if (network != null) {
            networkTf.setText(network.getName());
            networkIdTf.setText(network.getNetworkId());
            protocolMagicTf.setText(network.getProtocolMagic());
        }
    }

    public @Nullable ValidationInfo doValidate() {
        if (StringUtil.isEmpty(getName())) {
            return new ValidationInfo("Please enter a valid name", nameTf);
        }

        if (getNodeType() == null || getNodeType().equals(NodeType.EMPTY)) {
            return new ValidationInfo("Please select a valid node type", nodeTypesCB);
        }

        if (StringUtil.isEmpty(getApiEndpoint())) {
            return new ValidationInfo("Please enter a valid api endpoint url", apiEndpointTf);
        }

        if (getApiEndpoint().contains("blockfrost.io") && StringUtil.isEmpty(getAuthKey())) {
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
        return (NodeType) nodeTypesCB.getSelectedItem();
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

    @Override
    public Map<String, String> getHeaders() {
        return null;
    }

    @Override
    public int getTimeout() {
        return 120; //Not used for Blockfrost
    }

    private void testNetworkConnection() {

        Task.Backgroundable task = new Task.Backgroundable(null, "Network Info") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                RemoteNode remoteNode = new RemoteNode();
                remoteNode.setId(UUID.randomUUID().toString()); //Some random id
                remoteNode.setName(getName());
                remoteNode.setApiEndpoint(getApiEndpoint());
                remoteNode.setAuthKey(getAuthKey());
                remoteNode.setNodeType(getNodeType());
                remoteNode.setProtocolMagic(getProtocolMagic());
                remoteNode.setNetworkId(getNetworkId());

                LogListener logListener = new LogListener() {
                    @Override
                    public void info(String msg) {

                    }

                    @Override
                    public void error(String msg) {

                    }

                    @Override
                    public void warn(String msg) {

                    }
                };

                try {
                    //First remove
                    NetworkInfoService networkService = new NetworkServiceImpl(remoteNode, logListener);
                    Result result = networkService.testAndGetNetworkInfo();

                    if (result.isSuccessful()) {
                        connectionTestLabel.setForeground(Color.black);
                        connectionTestLabel.setText("Successfully connected !!!");
                    } else {
                        connectionTestLabel.setForeground(Color.red);
                        String response = result.getResponse();
                        if (response != null && response.length() > 30)
                            response = response.substring(0, 27) + "...";

                        connectionTestLabel.setText("Could not connect to node : " + response);
                        connectionTestLabel.setToolTipText(result.getResponse());
                    }
                } catch (Exception exception) {
                    connectionTestLabel.setText("Could not connect to node. Reason: " + exception.getMessage());
                }
            }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        nodeTypesCB = new ComboBox(new NodeType[]{NodeType.EMPTY,
                NodeType.BLOCKFROST_PREPOD,
                NodeType.BLOCKFROST_PREVIEW,
                NodeType.BLOCKFROST_TESTNET,
                NodeType.BLOCKFROST_MAINNET
        });
    }
}
