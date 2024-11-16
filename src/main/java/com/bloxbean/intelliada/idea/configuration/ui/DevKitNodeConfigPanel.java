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

//Config panel for YaciDevKit api
public class DevKitNodeConfigPanel implements NodeConfigurator {
    private JPanel mainPanel;
    private JTextField nameTf;
    private JButton testConnectionBtn;
    private JComboBox nodeTypesCB;
    private JTextField protocolMagicTf;
    private JTextField apiEndpointTf;
    private JLabel connectionTestLabel;
    private boolean newConfig = true;

    public DevKitNodeConfigPanel() {
        this(null);
    }

    public DevKitNodeConfigPanel(RemoteNode node) {
        super();

        initialize(node);
    }

    private void initialize(RemoteNode node) {
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
//            authKey.setText(node.getAuthKey());
//            networkTf.setText(node.getNetwork());
//            networkIdTf.setText(node.getNetworkId());
            protocolMagicTf.setText(node.getProtocolMagic());
        }
    }

    private void handleNodeTypeSelection() {
        nodeTypesCB.addActionListener(e -> {
                apiEndpointTf.setText(NetworkUrls.YACI_DEVKIT_BASEURL);
                apiEndpointTf.setEnabled(true);
        });
    }

    private void setNetwork(Network network) {
        if (network != null) {
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
        return "dummy key";
    }

    public NodeType getNodeType() {
        return NodeType.YaciDevKit;
    }

    public String getNetwork() {
        return "devkit_network";
    }

    public String getNetworkId() {
        return "devkit_network_id";
        //return networkIdTf.getText();
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
//                remoteNode.setAuthKey(getAuthKey());
                remoteNode.setNodeType(getNodeType());
                remoteNode.setProtocolMagic(getProtocolMagic());
               // remoteNode.setNetworkId(getNetworkId());

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
                    Long currentSlot = networkService.getCurrentSlot();

                    if (currentSlot != null && currentSlot > 0) {
                        connectionTestLabel.setForeground(Color.black);
                        connectionTestLabel.setText("Successfully connected !!!");
                    } else {
                        connectionTestLabel.setForeground(Color.red);
//                        String response = result.getResponse();
//                        if (response != null && response.length() > 30)
//                            response = response.substring(0, 27) + "...";

                        connectionTestLabel.setText("Could not connect to node " );
//                        connectionTestLabel.setToolTipText(result.getResponse());
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
        nodeTypesCB = new ComboBox(new NodeType[]{NodeType.YaciDevKit});
    }
}
