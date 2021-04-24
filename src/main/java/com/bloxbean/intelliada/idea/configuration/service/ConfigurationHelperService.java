package com.bloxbean.intelliada.idea.configuration.service;

import com.bloxbean.intelliada.idea.configuration.model.CLIProvider;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.configuration.ui.CLIProviderDialog;
import com.bloxbean.intelliada.idea.configuration.ui.NodeConfigDialog;
import com.bloxbean.intelliada.idea.core.messaging.CLIProvidersChangeNotifier;
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

    public static boolean deleteRemoteNodeConfiguration(RemoteNode node) {
        RemoteNodeState nodeStateService = RemoteNodeState.getInstance();

        if (nodeStateService == null)
            return false;

        nodeStateService.removeRemoteNode(node);
        RemoteNodeChangeNotifier nodeChangeNotifier = ApplicationManager.getApplication().getMessageBus().syncPublisher(RemoteNodeChangeNotifier.CHANGE_CARDANO_REMOTE_NODE_TOPIC);
        nodeChangeNotifier.nodeDeleted(node);

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

    public static CLIProvider createOrUpdateLocalSDKConfiguration(Project project, CLIProvider existingLocalSdk) {
        CLIProvidersState stateService = CLIProvidersState.getInstance();
        CLIProviderDialog CLIProviderDialog = new CLIProviderDialog(project, existingLocalSdk);
        boolean ok = CLIProviderDialog.showAndGet();
        if (ok) {
            //save and return
            CLIProvider cliProvider = new CLIProvider();

            if (existingLocalSdk == null) {
                cliProvider.setId(UUID.randomUUID().toString());
            } else {
                cliProvider.setId(existingLocalSdk.getId());
            }

            cliProvider.setHome(CLIProviderDialog.getHome());
            cliProvider.setName(CLIProviderDialog.getName());
            cliProvider.setVersion(CLIProviderDialog.getVersion());

            if (existingLocalSdk == null) {
                stateService.addCLIProvider(cliProvider);

                CLIProvidersChangeNotifier cliProvidersChangeNotifier = ApplicationManager.getApplication().getMessageBus().syncPublisher(CLIProvidersChangeNotifier.CHANGE_CLI_PROVIDERS_TOPIC);
                cliProvidersChangeNotifier.providerAdded(cliProvider);
            } else {
                stateService.updateCLIProvider(cliProvider);

                CLIProvidersChangeNotifier cliProvidersChangeNotifier = ApplicationManager.getApplication().getMessageBus().syncPublisher(CLIProvidersChangeNotifier.CHANGE_CLI_PROVIDERS_TOPIC);
                cliProvidersChangeNotifier.providerUpdated(cliProvider);
            }

            return cliProvider;
        } else {
            return null;
        }
    }

    public static void deleteCLIProviderConfiguration(CLIProvider sdk) {
        CLIProvidersState stateService = CLIProvidersState.getInstance();

        if(stateService == null)
            return;

        stateService.removeCLIProvider(sdk);

        CLIProvidersChangeNotifier providersChangeNotifier = ApplicationManager.getApplication().getMessageBus().syncPublisher(CLIProvidersChangeNotifier.CHANGE_CLI_PROVIDERS_TOPIC);
        providersChangeNotifier.providerDeleted(sdk);

    }

    public static void setDefaultCLIProvider(String defaultProviderId) {
        if(StringUtil.isEmpty(defaultProviderId))
            return;

        CLIProvidersState state = CLIProvidersState.getInstance();

        if(state != null) {
            state.setDefaultProvider(defaultProviderId);

            CLIProvidersChangeNotifier providersChangeNotifier = ApplicationManager.getApplication().getMessageBus().syncPublisher(CLIProvidersChangeNotifier.CHANGE_CLI_PROVIDERS_TOPIC);
            providersChangeNotifier.defaultProviderChanged(defaultProviderId);
        }
    }
}
