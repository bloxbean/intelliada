package com.bloxbean.intelliada.idea.nodeint.service.impl;

import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NetworkUtil;
import com.bloxbean.intelliada.idea.nodeint.CardanoNodeConfigurationHelper;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;
import com.bloxbean.intelliada.idea.nodeint.exception.TargetNodeNotConfigured;
import com.bloxbean.intelliada.idea.nodeint.service.NodeServiceFactory;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.util.NetworkHelper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

public class NodeBaseService {
    private static Logger LOG = Logger.getInstance(NodeBaseService.class);

    protected LogListener logListener;
    protected BackendService backendService;
    private RemoteNode remoteNode;

    public NodeBaseService(Project project) throws TargetNodeNotConfigured {
        this(project, new LogListener() {
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

    public NodeBaseService(Project project, LogListener logListener) throws TargetNodeNotConfigured {
        RemoteNode remoteNode = CardanoNodeConfigurationHelper.getTargetRemoteNode(project);
        if (remoteNode == null) {
            throw new TargetNodeNotConfigured("Please select a default node first");
        }

        this.logListener = logListener;
        this.remoteNode = remoteNode;
        backendService = NodeServiceFactory.getInstance().getBackendService(remoteNode);
    }

    public NodeBaseService(RemoteNode node, LogListener logListener) throws TargetNodeNotConfigured {
        if (node == null)
            throw new TargetNodeNotConfigured("Target node cannot be null. Please select a valid remote node");

        this.remoteNode = node;
        this.logListener = logListener;
        backendService = NodeServiceFactory.getInstance().getBackendService(node);
    }

    protected void printRemoteNodeDetails() {
        this.logListener.info("Connecting to " + remoteNode.getApiEndpoint());
        this.logListener.info("Node Name : " + remoteNode.getName());
        this.logListener.info("Node type : " + remoteNode.getNodeType());
        this.logListener.info("\n");
    }

    protected void waitForTransaction(String txnId) throws ApiCallException {
        if (StringUtil.isEmpty(txnId)) {
            logListener.error("Transaction id cannot be null");
            throw new ApiCallException("Transaction id cannot be null");
        }

        try {
            //logListener.info("Waiting for transaction to be mined ....");
            int count = 0;
            while (count < 60) {
                Result<TransactionContent> txnResult = backendService.getTransactionService()
                        .getTransaction(txnId);
                if (txnResult.isSuccessful()) {
                    logListener.info("");
                    logListener.info("Txn content :");
                    logListener.info(JsonUtil.getPrettyJson(txnResult.getValue()));

                    try {
                        if (NetworkUtil.isMainnet(remoteNode)) {
                            logListener.info("Check transaction details here : " + NetworkHelper.getInstance().getTxnHashUrl(NetworkHelper.MAINNET, txnId));
                        } else {
                            logListener.info("Check transaction details here : " + NetworkHelper.getInstance().getTxnHashUrl(NetworkHelper.TESTNET, txnId));
                        }
                    } catch (Exception e) {
                        //Ignore
                    }
                    return;
                } else {
                    logListener.printWait(count + " sec - " + " Waiting for transaction to be mined.");
                }

                count++;
                Thread.currentThread().sleep(1000);
            }
            logListener.info("");
            logListener.warn("Taking too long to mine the transaction. " +
                    "Please check transaction status in the Cardano explorer." +
                    "\nTransaction Id : " + txnId);

        } catch (Exception e) {
            logListener.error("Error getting transaction status", e);
            throw new ApiCallException("Error getting transaction status", e);
        }
    }

    protected RemoteNode getRemoteNode() {
        return remoteNode;
    }

    /**
     * Required only for test connection call.
     */
    protected void clearCachedBackendService() {
        NodeServiceFactory.getInstance().nodeRemoved(remoteNode);
    }
}
