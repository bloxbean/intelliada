package com.bloxbean.intelliada.idea.configuration.service;

import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
        name = "com.bloxbean.intelliada.RemoteNodeState",
        storages = {@Storage("cardano-remote-nodes.xml")}
)
public class RemoteNodeState implements PersistentStateComponent<Element> {
    private static final Logger LOG = Logger.getInstance(RemoteNodeState.class);

    public static RemoteNodeState getInstance() {
        return ServiceManager.getService(RemoteNodeState.class);
    }

    private List<RemoteNode> remoteNodes;
    private String defaultNode;

    public RemoteNodeState() {
        this.remoteNodes = new ArrayList<>();
    }

    @Nullable
    @Override
    public Element getState() {
        Element state = new Element("remoteNodes");

        for(RemoteNode node: remoteNodes) {
            Element entry = new Element("remoteNode");
            entry.setAttribute("id", node.getId());
            entry.setAttribute("name", node.getName());
            if(node.getNodeType() != null)
                entry.setAttribute("nodeType", node.getNodeType().name());
            else
                entry.setAttribute("nodeType", "");
            entry.setAttribute("apiEndPoint", StringUtil.notNullize(node.getApiEndpoint()));
            entry.setAttribute("authKey", StringUtil.notNullize(node.getAuthKey()));
            entry.setAttribute("network", StringUtil.notNullize(node.getNetwork()));
            entry.setAttribute("networkId", StringUtil.notNullize(node.getNetworkId()));
            entry.setAttribute("protocolMagic", StringUtil.notNullize(node.getProtocolMagic()));

//            entry.setAttribute("version", StringUtil.notNullize(node.getVersion()));

            state.addContent(entry);
        }

        if(defaultNode != null)
            state.setAttribute("defaultNode", defaultNode);

        return state;
    }

    @Override
    public void loadState(@NotNull Element elm) {
        List<RemoteNode> list = new ArrayList<>();

        defaultNode = elm.getAttributeValue("defaultNode");

        for (Element child : elm.getChildren("remoteNode")) {
            String id = child.getAttributeValue("id");
            String name = child.getAttributeValue("name");
            String nodeTypeStr = child.getAttributeValue("nodeType");
            String apiEndPoint = child.getAttributeValue("apiEndPoint");
            String authKey = child.getAttributeValue("authKey");
            String network = child.getAttributeValue("network");
            String networkId = child.getAttributeValue("networkId");
            String protocolMagic = child.getAttributeValue("protocolMagic");

            NodeType nodeType = null;
            if(!StringUtil.isEmpty(nodeTypeStr))
                nodeType = NodeType.lookupByName(nodeTypeStr);

            RemoteNode node = new RemoteNode(id, name, nodeType, apiEndPoint);
            node.setAuthKey(authKey);
            node.setNetwork(network);
            node.setNetworkId(networkId);
            node.setProtocolMagic(protocolMagic);

            list.add(node);
        }

        setRemoteNodes(list);
    }

    public List<RemoteNode> getRemoteNodes() {
        return remoteNodes;
    }

    public void addRemoteNode(RemoteNode node) {
        remoteNodes.add(node);
    }

    public void updateRemoteNode(RemoteNode node) {
        for(RemoteNode rnode: remoteNodes) {
            if(rnode.getId() != null && rnode.getId().equals(node.getId())) {
                rnode.updateValues(node);
                break;
            }
        }
    }

    private void setRemoteNodes(List<RemoteNode> list) {
        remoteNodes = list;
    }

    public void removeRemoteNode(RemoteNode node) {
        if(remoteNodes == null || node == null) return;
        remoteNodes.remove(node);
    }

    public String getDefaultNode() {
        return defaultNode;
    }

    public RemoteNode getDefaultRemoteNode() {
        if(StringUtil.isEmpty(defaultNode)) return null;
        if(remoteNodes == null || remoteNodes.size() == 0) return null;

        for(RemoteNode node: remoteNodes) {
            if(defaultNode.equals(node.getId()))
                return node;
        }
        return null;
    }

    public void setDefaultNode(String defaultNode) {
        this.defaultNode = defaultNode;
    }
}
