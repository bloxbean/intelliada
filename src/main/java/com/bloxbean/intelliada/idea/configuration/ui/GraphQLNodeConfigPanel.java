package com.bloxbean.intelliada.idea.configuration.ui;

import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.intelliada.idea.configuration.common.HeaderParserUtil;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.Networks;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.api.NetworkInfoService;
import com.bloxbean.intelliada.idea.nodeint.service.impl.NetworkServiceImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TextFieldWithAutoCompletion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class GraphQLNodeConfigPanel implements NodeConfigurator {
    private JPanel mainPanel;
    private JTextField nameTf;
    private JTextField headersTf;
    private JTextField networkTf;
    private JTextField networkIdTf;
    private JTextField protocolMagicTf;
    private JButton testConnBtn;
    private JLabel connectionTestLabel;
    private TextFieldWithAutoCompletion gqlEndpointTf;
    private JCheckBox ignoreFetchNetworkInfoCB;
    private JTextField timeoutTf;
    private Project project;

    public GraphQLNodeConfigPanel(Project project) {
        this.project = project;
        testConnBtn.addActionListener(e -> {
            testNetworkConnection();
        });

        attachApiEndpointChangeHandler();
        timeoutTf.setText("120");
        timeoutTf.setToolTipText("Connection timeout");
    }

    private void attachApiEndpointChangeHandler() {
        gqlEndpointTf.addFocusListener(new FocusListener() {
            String oldUrl;
            @Override
            public void focusGained(FocusEvent e) {
                oldUrl = gqlEndpointTf.getText();
            }

            @Override
            public void focusLost(FocusEvent e) {
                if(oldUrl != null && oldUrl.equals(gqlEndpointTf.getText())) {
                    oldUrl = null;
                    return;
                }
                oldUrl = null; //reset old mnemonic
                //Reset network fields
                networkTf.setText("");
                networkIdTf.setText("");
                protocolMagicTf.setText("");
            }
        });
    }

    @Override
    public void setNodeData(RemoteNode node) {
        if(node != null) {
            nameTf.setText(node.getName());
            gqlEndpointTf.setText(node.getApiEndpoint());
            headersTf.setText(HeaderParserUtil.encodeHeaders(node.getHeaders()));
            networkTf.setText(node.getNetwork());
            networkIdTf.setText(node.getNetworkId());
            protocolMagicTf.setText(node.getProtocolMagic());
            timeoutTf.setText(String.valueOf(node.getTimeout()));
            timeoutTf.setEditable(true);
        }
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    @Override
    public String getName() {
        return nameTf.getText();
    }

    @Override
    public String getApiEndpoint() {
        return gqlEndpointTf.getText();
    }

    @Override
    public String getAuthKey() {
        return null;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.CARDANO_GRAPHQL;
    }

    @Override
    public String getNetwork() {
        return networkTf.getText().trim();
    }

    @Override
    public String getNetworkId() {
        return networkIdTf.getText().trim();
    }

    @Override
    public String getProtocolMagic() {
        return protocolMagicTf.getText().trim();
    }

    @Override
    public Map<String, String> getHeaders() {
        String headerStr = headersTf.getText();
        if(StringUtil.isEmpty(headerStr.trim()))
            return null;

        return HeaderParserUtil.parseHeaders(headerStr);
    }

    public int getTimeout() {
        try {
            return Integer.parseInt(timeoutTf.getText().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public @Nullable ValidationInfo doValidate() {
        if (StringUtil.isEmpty(getName())) {
            return new ValidationInfo("Please enter a valid name", nameTf);
        }

        if(StringUtil.isEmpty(getApiEndpoint())) {
            return new ValidationInfo("Please enter a valid api endpoint url", gqlEndpointTf);
        }

        try {
            getHeaders();
        } catch (Exception e) {
            return new ValidationInfo("Invalid headers. Please provide headers in correct format", headersTf);
        }

        if(getTimeout() == 0 && timeoutTf.isEditable()) {
            return new ValidationInfo("Timeout should be a positive integer", timeoutTf);
        }

        if(!ignoreFetchNetworkInfoCB.isSelected()) {
            if (StringUtil.isEmpty(networkIdTf.getText())) {
                return new ValidationInfo("Network Id is empty. Try to fetch it from network", networkIdTf);
            }

            if (StringUtil.isEmpty(protocolMagicTf.getText())) {
                return new ValidationInfo("ProtocolMagic is empty. Try to fetch it from network", protocolMagicTf);
            }
        }

        return null;
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
                remoteNode.setTimeout(10);

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
                    Result<Genesis> result = networkService.testAndGetNetworkInfo();

                    if(result.isSuccessful()) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            connectionTestLabel.setForeground(Color.black);
                            connectionTestLabel.setText("Successfully connected !!!");
                            Genesis genesis = result.getValue();
                            if (genesis != null) {
                                String protocolMagic = String.valueOf(genesis.getNetworkMagic());
                                protocolMagicTf.setText(protocolMagic);
                                //networkIfTf.setText(genesis.get);
                                if (Networks.mainnet().getProtocolMagic().equals(protocolMagic)) {
                                    networkIdTf.setText(Networks.mainnet().getNetworkId());
                                    networkTf.setText(Networks.mainnet().getName());
                                } else if (Networks.testnet().getProtocolMagic().equals(protocolMagic)) {
                                    networkIdTf.setText(Networks.testnet().getNetworkId());
                                    networkTf.setText(Networks.testnet().getName());
                                } else {
                                    networkIdTf.setText(Networks.testnet().getNetworkId());
                                    networkTf.setText("testnet-custom");
                                }
                            }
                        });
                    } else {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            connectionTestLabel.setForeground(Color.red);
                            String response = result.getResponse();
                            if (response != null && response.length() > 30)
                                response = response.substring(0, 27) + "...";

                            networkTf.setText("");
                            networkIdTf.setText("");
                            connectionTestLabel.setText("Could not connect to node : " + response);
                            connectionTestLabel.setToolTipText(result.getResponse());
                        });
                    }
                }catch (Exception exception) {
                    connectionTestLabel.setText("Could not connect to node. Reason: " + exception.getMessage());
                }
            }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        Collection<String> availableNodeOptions = new ArrayList<String>();
        availableNodeOptions.add("");
        availableNodeOptions.add("http://localhost:3100/graphql");
        availableNodeOptions.add("https://graphql-api.testnet.dandelion.link/");
        availableNodeOptions.add("https://graphql-api.mainnet.dandelion.link/");
        gqlEndpointTf =  TextFieldWithAutoCompletion.create(project, availableNodeOptions, true, "" );
    }

}
