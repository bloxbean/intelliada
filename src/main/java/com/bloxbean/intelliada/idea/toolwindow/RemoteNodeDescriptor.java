package com.bloxbean.intelliada.idea.toolwindow;

import com.bloxbean.intelliada.idea.common.CardanoIcons;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;

public class RemoteNodeDescriptor extends NodeDescriptor {

    private RemoteNode node;

    public RemoteNodeDescriptor(final Project project, final NodeDescriptor parentDescriptor, RemoteNode node, String deploymentNodeId) {
        super(project, parentDescriptor);
        this.node = node;

        myName = node.getName() + " [" + StringUtil.trimLog(node.getWalletApiEndpoint(), 40) + "]";
        myColor = JBColor.GREEN;
        myClosedIcon = CardanoIcons.CARDANO_ICON_16x16;

        if(node.getId() != null) {
//            if (sdk.getId().equals(compilerNodeId)){
//                myClosedIcon = AlgoIcons.LOCALSDK_COMPILE;
//                isCompilerTarget = true;
//            } else {
//                myClosedIcon = AlgoIcons.LOCALSDK;
//            }
        } else {
//            myClosedIcon = AlgoIcons.LOCALSDK;
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

//    public boolean isCompilerTarget() {
//        return isCompilerTarget;
//    }

}
