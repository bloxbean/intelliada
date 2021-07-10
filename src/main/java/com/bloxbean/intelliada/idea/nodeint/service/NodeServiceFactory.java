package com.bloxbean.intelliada.idea.nodeint.service;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.factory.BackendFactory;
import com.bloxbean.cardano.client.backend.gql.GqlBackendService;
import com.bloxbean.cardano.client.backend.gql.adapter.AddHeadersInterceptor;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.nodeint.exception.InvalidNodeConfigurationException;
import com.intellij.openapi.diagnostic.Logger;
import okhttp3.OkHttpClient;

import java.time.Duration;
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
        if(instance == null)
            instance = new NodeServiceFactory();

        return instance;
    }

    public BackendService getBackendService(RemoteNode remoteNode) {
        if (remoteNode == null)
            return null;

        BackendService backendService = backendServiceMap.get(remoteNode.getId());
        if (backendService != null)
            return backendService;

        if (NodeType.BLOCKFROST_TESTNET.equals(remoteNode.getNodeType()) ||
                NodeType.BLOCKFROST_MAINNET.equals(remoteNode.getNodeType())) {
            backendService
                    = BackendFactory.getBlockfrostBackendService(remoteNode.getApiEndpoint(), remoteNode.getAuthKey());
            backendServiceMap.put(remoteNode.getId(), backendService);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Backend service created for the node : " + remoteNode);
            }
        } else if(NodeType.CARDANO_GRAPHQL.equals(remoteNode.getNodeType())) {
            if(remoteNode.getTimeout() != 0) {
                backendService = createGraphQLBackendServiceWithTimeout(remoteNode);
            } else {
                backendService = new GqlBackendService(remoteNode.getApiEndpoint(), remoteNode.getHeaders());
            }

            backendServiceMap.put(remoteNode.getId(), backendService);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Backend service created for the node : " + remoteNode);
            }
        } else {
            throw new InvalidNodeConfigurationException("Invalid node type : " + remoteNode.getNodeType());
        }

        return backendService;
    }

    private BackendService createGraphQLBackendServiceWithTimeout(RemoteNode remoteNode) {
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient().newBuilder();
        okHttpClientBuilder.callTimeout( Duration.ofSeconds(remoteNode.getTimeout()));
        okHttpClientBuilder.readTimeout(Duration.ofSeconds(remoteNode.getTimeout()));
        if(remoteNode.getHeaders() != null && remoteNode.getHeaders().size() > 0) {
            okHttpClientBuilder.addInterceptor(new AddHeadersInterceptor(remoteNode.getHeaders()));
        }

        return new GqlBackendService(remoteNode.getApiEndpoint(), okHttpClientBuilder.build());
    }

    public void nodeRemoved(RemoteNode node) {
        if(node != null) {
            backendServiceMap.remove(node.getId());
            if(LOG.isDebugEnabled()) {
                LOG.debug("Backend service removed for node : " + node);
            }
        }
    }
}
