package com.bloxbean.intelliada.idea.nodeint.service.impl.yaciprovider;

import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.Network;
import com.bloxbean.intelliada.idea.core.util.NetworkUtil;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.nodeint.CardanoNodeConfigurationHelper;
import com.bloxbean.intelliada.idea.nodeint.devkit.DevKitLifecycleService;
import com.bloxbean.intelliada.idea.nodeint.yano.YanoLifecycleService;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;
import com.bloxbean.intelliada.idea.nodeint.exception.TargetNodeNotConfigured;
import com.bloxbean.intelliada.idea.nodeint.service.NodeServiceFactory;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.util.NetworkHelper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

public class YaciBaseService {
    private static Logger LOG = Logger.getInstance(YaciBaseService.class);

    protected LogListener logListener;
    protected BackendService backendService;
    private RemoteNode remoteNode;
    protected String baseUrl;
    private Project project;

    public YaciBaseService(Project project) throws TargetNodeNotConfigured {
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

    public YaciBaseService(Project project, LogListener logListener) throws TargetNodeNotConfigured {
        RemoteNode remoteNode = CardanoNodeConfigurationHelper.getTargetRemoteNode(project);
        if (remoteNode == null) {
            throw new TargetNodeNotConfigured("Please select a default node first");
        }

        this.project = project;
        this.logListener = logListener;
        this.remoteNode = remoteNode;
        this.baseUrl = this.remoteNode.getApiEndpoint();

        // Auto-start DevKit if it's a LocalYaciDevKit node
        if (remoteNode.getNodeType() == NodeType.LocalYaciDevKit) {
            ensureDevKitRunning();
        }

        // Auto-start Yano if it's a Yano node
        if (remoteNode.getNodeType() == NodeType.Yano) {
            ensureYanoRunning();
        }

        this.backendService = new BFBackendService(remoteNode.getApiEndpoint(), "Dummy Key");
    }

    public YaciBaseService(RemoteNode node, LogListener logListener) throws TargetNodeNotConfigured {
        if (node == null)
            throw new TargetNodeNotConfigured("Target node cannot be null. Please select a valid remote node");

        this.remoteNode = node;
        this.logListener = logListener;
        this.baseUrl = this.remoteNode.getApiEndpoint();

        this.backendService = new BFBackendService(remoteNode.getApiEndpoint(), "Dummy Key");
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
                        Network network = NetworkUtil.getNetworkType(remoteNode);
                        if (network != null)
                            logListener.info("Check transaction details here : "
                                    + NetworkHelper.getInstance().getTxnHashUrl(remoteNode.getNodeType(), network.getName(), txnId));

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

    private void ensureYanoRunning() {
        if (project != null && remoteNode.getNodeType() == NodeType.Yano) {
            YanoLifecycleService service = YanoLifecycleService.getInstance();

            if (!service.isYanoRunning(project)) {
                logListener.info("Starting Yano devnet...");
                String yanoHome = remoteNode.getHome();

                if (StringUtil.isEmpty(yanoHome)) {
                    logListener.warn("Yano home directory not configured. Please configure it in node settings.");
                    return;
                }

                service.startYano(project, yanoHome)
                    .thenAccept(success -> {
                        if (success) {
                            logListener.info("Yano devnet started successfully");
                        } else {
                            logListener.error("Failed to start Yano devnet");
                        }
                    })
                    .exceptionally(throwable -> {
                        logListener.error("Error starting Yano devnet", throwable);
                        return null;
                    });
            } else {
                logListener.info("Yano devnet is already running");
            }
        }
    }

    private void ensureDevKitRunning() {
        if (project != null && remoteNode.getNodeType() == NodeType.LocalYaciDevKit) {
            DevKitLifecycleService service = DevKitLifecycleService.getInstance();
            
            if (!service.isDevKitRunning(project)) {
                logListener.info("Starting local Yaci DevKit...");
                String devKitHome = remoteNode.getHome();
                
                if (StringUtil.isEmpty(devKitHome)) {
                    logListener.warn("DevKit home directory not configured. Please configure it in node settings.");
                    return;
                }
                
                service.startDevKit(project, devKitHome)
                    .thenAccept(success -> {
                        if (success) {
                            logListener.info("Local Yaci DevKit started successfully");
                        } else {
                            logListener.error("Failed to start local Yaci DevKit");
                        }
                    })
                    .exceptionally(throwable -> {
                        logListener.error("Error starting local Yaci DevKit", throwable);
                        return null;
                    });
            } else {
                logListener.info("Local Yaci DevKit is already running");
            }
        }
    }
}
