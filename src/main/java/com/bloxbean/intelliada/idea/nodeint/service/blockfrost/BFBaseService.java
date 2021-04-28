package com.bloxbean.intelliada.idea.nodeint.service.blockfrost;

import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState;
import com.bloxbean.intelliada.idea.nodeint.service.LogListener;
import com.intellij.idea.LoggerFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

public class BFBaseService {
    private static Logger LOG = Logger.getInstance(BFBaseService.class);

    protected ApiClient apiClient;
    protected LogListener logListener;

    public BFBaseService(RemoteNode remoteNode) {
        this(remoteNode, new LogListener() {
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

    public BFBaseService(RemoteNode remoteNode, LogListener logListener) {
        this.logListener = logListener;
        apiClient = new BFConnectionFactory(remoteNode.getApiEndpoint(), remoteNode.getAuthKey()).connect();
    }
}
