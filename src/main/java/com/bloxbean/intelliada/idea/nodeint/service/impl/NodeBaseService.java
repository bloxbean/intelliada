package com.bloxbean.intelliada.idea.nodeint.service.impl;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.factory.BackendFactory;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.intellij.openapi.diagnostic.Logger;

public class NodeBaseService {
    private static Logger LOG = Logger.getInstance(NodeBaseService.class);

    protected LogListener logListener;
    protected BackendService backendService;

    public NodeBaseService(RemoteNode node) {
        this(node, new LogListener() {
            @Override
            public void info(String msg) {
                LOG.info(msg);
            }

            @Override
            public void error(String msg) {
                LOG.info(msg);
            }

            @Override
            public void warn(String msg) {
                LOG.info(msg);
            }

            @Override
            public void error(String msg, Throwable t) {
                LOG.error(msg, t);
            }

            @Override
            public void warn(String msg, Throwable t) {
                LOG.warn(msg, t);
            }
        });
    }

    public NodeBaseService(RemoteNode remoteNode, LogListener logListener) {
        this.logListener = logListener;

        if(NodeType.BLOCKFROST_TESTNET.equals(remoteNode.getNodeType()) ||
            NodeType.BLOCKFROST_MAINNET.equals(remoteNode.getNodeType())) {
            backendService = BackendFactory.getBlockfrostBackendService(remoteNode.getApiEndpoint(), remoteNode.getAuthKey());
        }
    }
}
