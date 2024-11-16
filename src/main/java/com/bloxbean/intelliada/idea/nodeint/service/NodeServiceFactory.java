package com.bloxbean.intelliada.idea.nodeint.service;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.nodeint.exception.InvalidNodeConfigurationException;
import com.intellij.openapi.diagnostic.Logger;

import java.util.HashMap;
import java.util.Map;

public class NodeServiceFactory {
    private Logger LOG = Logger.getInstance(NodeServiceFactory.class);

    private Map<String, BackendService> backendServiceMap;
    private static NodeServiceFactory instance;

    private NodeServiceFactory() {
        backendServiceMap = new HashMap<>();
    }

    public static NodeServiceFactory getInstance() {
        if (instance == null)
            instance = new NodeServiceFactory();

        return instance;
    }

    public BackendService getBackendService(RemoteNode remoteNode) {
        if (remoteNode == null)
            return null;

        BackendService backendService = backendServiceMap.get(remoteNode.getId());
        if (backendService != null)
            return backendService;

        if (NodeType.YaciDevKit.equals(remoteNode.getNodeType())) {
            backendService
                    = new BFBackendService(remoteNode.getApiEndpoint(), "Some dummy key");
            backendServiceMap.put(remoteNode.getId(), backendService);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Backend service created for the node : " + remoteNode);
            }
        } else if (NodeType.BLOCKFROST_MAINNET.equals(remoteNode.getNodeType()) ||
                NodeType.BLOCKFROST_PREPROD.equals(remoteNode.getNodeType()) ||
                NodeType.BLOCKFROST_PREVIEW.equals(remoteNode.getNodeType()) ||
                NodeType.BLOCKFROST_CUSTOM.equals(remoteNode.getNodeType())
        ) {
            backendService
                    = new BFBackendService(remoteNode.getApiEndpoint(), remoteNode.getAuthKey());
            backendServiceMap.put(remoteNode.getId(), backendService);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Backend service created for the node : " + remoteNode);
            }
        } else if (NodeType.KOIOS_MAINNET.equals(remoteNode.getNodeType()) ||
            NodeType.KOIOS_PREPROD.equals(remoteNode.getNodeType()) ||
            NodeType.KOIOS_CUSTOM.equals(remoteNode.getNodeType())
        ) {
            backendService = new KoiosBackendService(remoteNode.getApiEndpoint());
            backendServiceMap.put(remoteNode.getId(), backendService);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Backend service created for the node : " + remoteNode);
            }
        } else {
            throw new InvalidNodeConfigurationException("Invalid node type : " + remoteNode.getNodeType());
        }

        return backendService;
    }

    public void nodeRemoved(RemoteNode node) {
        if (node != null) {
            backendServiceMap.remove(node.getId());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Backend service removed for node : " + node);
            }
        }
    }
}
