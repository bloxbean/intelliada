package com.bloxbean.intelliada.idea.configuration.model;

import com.bloxbean.intelliada.idea.core.util.NodeType;

import java.util.Map;
import java.util.Objects;

public class RemoteNode {
    private String id;
    private String name;
    private NodeType nodeType;
    private String apiEndpoint;
    private String authKey;
    private String network;
    private String networkId;
    private String protocolMagic;
    private Map<String, String> headers;
    private int timeout;

    //local node properties
    private String home;
    private String version;

    public RemoteNode() {

    }

    public RemoteNode(String id, String name, NodeType nodeType, String apiEndpoint) {
        this.id = id;
        this.name = name;
        this.nodeType = nodeType;
        this.apiEndpoint = apiEndpoint;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public String getAuthKey() {
        return authKey;
    }

    public void setAuthKey(String authKey) {
        this.authKey = authKey;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    public String getProtocolMagic() {
        return protocolMagic;
    }

    public void setProtocolMagic(String protocolMagic) {
        this.protocolMagic = protocolMagic;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getHome() {
        return home;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void updateValues(RemoteNode node) { //Update everything except id
        if (node == null) return;

        this.setName(node.getName());
        this.setApiEndpoint(node.getApiEndpoint());
        this.setNodeType(node.getNodeType());
        this.setAuthKey(node.getAuthKey());
        this.setNetwork(node.getNetwork());
        this.setNetworkId(node.getNetworkId());
        this.setProtocolMagic(node.getProtocolMagic());
        this.setHeaders(node.getHeaders());
        this.setTimeout(node.getTimeout());
        this.setHome(node.getHome());
        this.setVersion(node.getVersion());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemoteNode that = (RemoteNode) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
