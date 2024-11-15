package com.bloxbean.intelliada.idea.nodeint.service;

import com.bloxbean.intelliada.idea.account.service.AccountService;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.nodeint.CardanoNodeConfigurationHelper;
import com.bloxbean.intelliada.idea.nodeint.exception.TargetNodeNotConfigured;
import com.bloxbean.intelliada.idea.nodeint.service.api.CardanoAccountService;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.impl.AccountServiceImpl;
import com.bloxbean.intelliada.idea.nodeint.service.impl.NetworkServiceImpl;
import com.bloxbean.intelliada.idea.nodeint.service.impl.yaciprovider.YaciAccountServiceImpl;
import com.intellij.openapi.project.Project;

public class CardanoServiceFactory {

    public static CardanoAccountService getAccountService(Project project, LogListener logListener) throws TargetNodeNotConfigured {
        RemoteNode remoteNode = CardanoNodeConfigurationHelper.getTargetRemoteNode(project);
        if (remoteNode == null) {
            throw new TargetNodeNotConfigured("Please select a default node first");
        }

        if (remoteNode.getNodeType() == NodeType.YaciDevKit) {
            return new YaciAccountServiceImpl(remoteNode, logListener);
        } else {
            return new AccountServiceImpl(project, logListener);
        }
    }

    public static NetworkServiceImpl getNetworkService(Project project, LogListener logListener) throws TargetNodeNotConfigured {
        RemoteNode remoteNode = CardanoNodeConfigurationHelper.getTargetRemoteNode(project);
        if (remoteNode == null) {
            throw new TargetNodeNotConfigured("Please select a default node first");
        }

        return new NetworkServiceImpl(remoteNode, logListener);
    }
}
