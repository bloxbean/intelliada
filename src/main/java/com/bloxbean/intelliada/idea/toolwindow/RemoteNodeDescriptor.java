package com.bloxbean.intelliada.idea.toolwindow;

import com.bloxbean.intelliada.idea.common.CardanoIcons;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;

public class RemoteNodeDescriptor extends NodeDescriptor {

    private boolean isDefaultNode;
    private RemoteNode node;

    public RemoteNodeDescriptor(final Project project, final NodeDescriptor parentDescriptor, RemoteNode node, String defaultNodeId) {
        super(project, parentDescriptor);
        this.node = node;

        myName = node.getName() + " [" + StringUtil.trimLog(node.getApiEndpoint(), 40) + "]";
        myColor = JBColor.GREEN;
        myClosedIcon = CardanoIcons.CARDANO_ICON_16x16;

        if(node.getId() != null) {
            if(node.getId().equals(defaultNodeId)) {
                myName += " (default)";
                this.isDefaultNode = true;
                myClosedIcon = CardanoIcons.CARDANO_ICON_16x16_DEFAULT;
            } else {
                this.isDefaultNode = false;
            }
        } else {
            this.isDefaultNode = false;
        }
    }

    @Override
    public boolean update() {
        return false;
    }

    @Override
    public Object getElement() {
        return node;
    }

    public RemoteNode getNode() {
        return node;
    }

    public boolean isDefaultNode() {
        return isDefaultNode;
    }

}
