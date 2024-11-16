package com.bloxbean.intelliada.idea.configuration.ui;

import com.bloxbean.intelliada.idea.configuration.common.CardanoNodeType;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class CardanoNodeConfigDialog {
    private JPanel mainPanel;
    private JComboBox nodeTypesCB;
    private JPanel contentPanel;
    private DevKitNodeConfigPanel devKitNodeConfigPanel;
    private RemoteNodeConfigPanel blockfrostConfigPanel;
    private KoiosNodeConfigPanel koiosNodeConfigPanel;

    public CardanoNodeConfigDialog(Project project, RemoteNode remoteNode) {
        nodeTypesCB.addItem(CardanoNodeType.YaciDevKit);
        nodeTypesCB.addItem(CardanoNodeType.Blockfrost);
        nodeTypesCB.addItem(CardanoNodeType.Koios);

        initialize(project, remoteNode);
    }

    public void initialize(Project project, RemoteNode remoteNode) {
        devKitNodeConfigPanel = new DevKitNodeConfigPanel(remoteNode);
        blockfrostConfigPanel = new RemoteNodeConfigPanel(remoteNode);
        koiosNodeConfigPanel = new KoiosNodeConfigPanel(project);

        //contentPanel.setLayout(new CardLayout());
        contentPanel.add(devKitNodeConfigPanel.getMainPanel(), CardanoNodeType.YaciDevKit.toString());
        contentPanel.add(blockfrostConfigPanel.getMainPanel(), CardanoNodeType.Blockfrost.toString());
        contentPanel.add(koiosNodeConfigPanel.getMainPanel(), CardanoNodeType.Koios.toString());

        nodeTypesCB.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                CardLayout cl = (CardLayout) (contentPanel.getLayout());
                cl.show(contentPanel, ((CardanoNodeType) e.getItem()).toString());
            }
        });

        if (remoteNode != null && remoteNode.getNodeType() != null) {
            if (remoteNode.getNodeType().equals(NodeType.KOIOS_MAINNET) ||
                    remoteNode.getNodeType().equals(NodeType.KOIOS_PREPROD) ||
                    remoteNode.getNodeType().equals(NodeType.KOIOS_CUSTOM)) {
                nodeTypesCB.setSelectedItem(CardanoNodeType.Koios);
                koiosNodeConfigPanel.setNodeData(remoteNode);
            } else if (remoteNode.getNodeType().equals(NodeType.BLOCKFROST_MAINNET) ||
                    remoteNode.getNodeType().equals(NodeType.BLOCKFROST_PREPROD) ||
                    remoteNode.getNodeType().equals(NodeType.BLOCKFROST_PREVIEW) ||
                    remoteNode.getNodeType().equals(NodeType.BLOCKFROST_CUSTOM)) {
                nodeTypesCB.setSelectedItem(CardanoNodeType.Blockfrost); //default
                blockfrostConfigPanel.setNodeData(remoteNode);
            } else {
                nodeTypesCB.setSelectedItem(CardanoNodeType.YaciDevKit);
                devKitNodeConfigPanel.setNodeData(remoteNode);
            }

            nodeTypesCB.setEnabled(false);
        }
    }

    public ValidationInfo doValidate() {
        CardanoNodeType cardanoNodeType = (CardanoNodeType) nodeTypesCB.getSelectedItem();
        if (CardanoNodeType.YaciDevKit.equals(cardanoNodeType)) {
            return devKitNodeConfigPanel.doValidate();
        } else if (CardanoNodeType.Blockfrost.equals(cardanoNodeType)) {
            return blockfrostConfigPanel.doValidate();
        } else if (CardanoNodeType.Koios.equals(cardanoNodeType)) {
            return koiosNodeConfigPanel.doValidate();
        } else {
            return null;
        }
    }

    public NodeConfigurator getNodeConfigurator() {
        CardanoNodeType cardanoNodeType = (CardanoNodeType) nodeTypesCB.getSelectedItem();
        if (CardanoNodeType.YaciDevKit.equals(cardanoNodeType)) {
            return devKitNodeConfigPanel;
        } else if (CardanoNodeType.Blockfrost.equals(cardanoNodeType)) {
            return blockfrostConfigPanel;
        } else if (CardanoNodeType.Koios.equals(cardanoNodeType)) {
            return koiosNodeConfigPanel;
        } else {
            return null;
        }
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }
}
