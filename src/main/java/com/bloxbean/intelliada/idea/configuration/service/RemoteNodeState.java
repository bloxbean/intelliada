package com.bloxbean.intelliada.idea.configuration.service;

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
            entry.setAttribute("walletApiEndPoint", StringUtil.notNullize(node.getWalletApiEndpoint()));
//            entry.setAttribute("version", StringUtil.notNullize(node.getVersion()));

            state.addContent(entry);
        }

        return state;
    }

    @Override
    public void loadState(@NotNull Element elm) {
        List<RemoteNode> list = new ArrayList<>();

        for (Element child : elm.getChildren("remoteNode")) {
            String id = child.getAttributeValue("id");
            String name = child.getAttributeValue("name");
            String home = child.getAttributeValue("walletApiEndPoint");

            RemoteNode node = new RemoteNode(id, name, home);

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
}
