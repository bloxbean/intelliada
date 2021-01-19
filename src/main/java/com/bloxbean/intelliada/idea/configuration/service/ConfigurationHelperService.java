package com.bloxbean.intelliada.idea.configuration.service;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.configuration.ui.NodeConfigDialog;
import com.bloxbean.intelliada.idea.core.messaging.RemoteNodeChangeNotifier;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

import java.util.UUID;

public class ConfigurationHelperService {

    public static RemoteNode createOrUpdateRemoteNodeConfiguration(Project project, RemoteNode remoteNode) {
        RemoteNodeState stateService = RemoteNodeState.getInstance();
        NodeConfigDialog nodeConfigDialog = new NodeConfigDialog(project, remoteNode);
        boolean ok = nodeConfigDialog.showAndGet();
        if (ok) {
            //save and return
            RemoteNode node = new RemoteNode();

            if (remoteNode == null) {
                node.setId(UUID.randomUUID().toString());
            } else {
                node.setId(remoteNode.getId());
            }

            node.setName(nodeConfigDialog.getRemoteNodeConfigPanel().getName());
            node.setWalletApiEndpoint(nodeConfigDialog.getRemoteNodeConfigPanel().getWalletApiEndPoint());

            if (remoteNode == null) {
                stateService.addRemoteNode(node);

                RemoteNodeChangeNotifier remoteNodeChangeNotifier = ApplicationManager.getApplication().getMessageBus().syncPublisher(RemoteNodeChangeNotifier.CHANGE_CARDANO_REMOTE_NODE_TOPIC);
                remoteNodeChangeNotifier.nodeAdded(node);
            } else {
                stateService.updateRemoteNode(node);

                RemoteNodeChangeNotifier remoteChangeNotifier = ApplicationManager.getApplication().getMessageBus().syncPublisher(RemoteNodeChangeNotifier.CHANGE_CARDANO_REMOTE_NODE_TOPIC);
                remoteChangeNotifier.nodeUpdated(node);
            }

            return node;
        } else {
            return null;
        }
    }

    public static boolean deleteAlgoNodeConfiguration(RemoteNode node) {
        RemoteNodeState nodeStateService = RemoteNodeState.getInstance();

        if (nodeStateService == null)
            return false;

        nodeStateService.removeRemoteNode(node);
        RemoteNodeChangeNotifier algoNodeChangeNotifier = ApplicationManager.getApplication().getMessageBus().syncPublisher(RemoteNodeChangeNotifier.CHANGE_CARDANO_REMOTE_NODE_TOPIC);
        algoNodeChangeNotifier.nodeDeleted(node);

        return true;
    }

    public static void setServerId(Project project, String serverId) {
        if(project == null || StringUtil.isEmpty(serverId))
            return;

        CardanoProjectState state = CardanoProjectState.getInstance(project);

        if(state != null) {
            state.getState().setServiceId(serverId);

           // notifyProjectNodeConfigChange(project);
        }
    }
}
