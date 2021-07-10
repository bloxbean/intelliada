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
    private RemoteNodeConfigPanel blockfrostConfigPanel;
    private GraphQLNodeConfigPanel graphQLNodeConfigPanel;

    public CardanoNodeConfigDialog(Project project, RemoteNode remoteNode) {
        nodeTypesCB.addItem(CardanoNodeType.Blockfrost);
        nodeTypesCB.addItem(CardanoNodeType.CardanoGraphQL);

        initialize(project, remoteNode);
    }

    public void initialize(Project project, RemoteNode remoteNode) {
        blockfrostConfigPanel = new RemoteNodeConfigPanel(remoteNode);
        graphQLNodeConfigPanel = new GraphQLNodeConfigPanel(project);

        //contentPanel.setLayout(new CardLayout());
        contentPanel.add(blockfrostConfigPanel.getMainPanel(), CardanoNodeType.Blockfrost.toString());
        contentPanel.add(graphQLNodeConfigPanel.getMainPanel(), CardanoNodeType.CardanoGraphQL.toString());

        nodeTypesCB.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                CardLayout cl = (CardLayout)(contentPanel.getLayout());
                cl.show(contentPanel, ((CardanoNodeType)e.getItem()).toString());
            }
        });

        if(remoteNode != null && remoteNode.getNodeType() != null) {
           if(remoteNode.getNodeType().equals(NodeType.CARDANO_GRAPHQL)) {
                nodeTypesCB.setSelectedItem(CardanoNodeType.CardanoGraphQL);
                graphQLNodeConfigPanel.setNodeData(remoteNode);
            } else { //Default is Blocfrost
                nodeTypesCB.setSelectedItem(CardanoNodeType.Blockfrost); //default
                blockfrostConfigPanel.setNodeData(remoteNode);
            }

           nodeTypesCB.setEnabled(false);
        }
    }

    public ValidationInfo doValidate() {
        CardanoNodeType cardanoNodeType = (CardanoNodeType)nodeTypesCB.getSelectedItem();
        if(CardanoNodeType.Blockfrost.equals(cardanoNodeType)) {
            return blockfrostConfigPanel.doValidate();
        } else if(CardanoNodeType.CardanoGraphQL.equals(cardanoNodeType)) {
            return graphQLNodeConfigPanel.doValidate();
        } else {
            return null;
        }
    }

    public NodeConfigurator getNodeConfigurator() {
        CardanoNodeType cardanoNodeType = (CardanoNodeType)nodeTypesCB.getSelectedItem();
        if(CardanoNodeType.Blockfrost.equals(cardanoNodeType)) {
            return blockfrostConfigPanel;
        } else if(CardanoNodeType.CardanoGraphQL.equals(cardanoNodeType)) {
            return graphQLNodeConfigPanel;
        } else {
            return null;
        }
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }
}
