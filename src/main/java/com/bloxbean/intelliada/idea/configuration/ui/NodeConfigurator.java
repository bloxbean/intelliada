package com.bloxbean.intelliada.idea.configuration.ui;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NodeType;

import java.util.Map;

public interface NodeConfigurator {

    public void setNodeData(RemoteNode node);

    String getName();

    String getApiEndpoint();

    String getAuthKey();

    NodeType getNodeType();

    String getNetwork();

    String getNetworkId();

    String getProtocolMagic();

    Map<String, String> getHeaders();

    public int getTimeout();
}
