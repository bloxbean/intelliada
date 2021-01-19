package com.bloxbean.intelliada.idea.configuration.model;

import java.util.Objects;

public class RemoteNode {
    private String id;
    private String name;
    private String walletApiEndpoint;

    public RemoteNode() {

    }

    public RemoteNode(String id, String name, String walletApiEndpoint) {
        this.id = id;
        this.name = name;
        this.walletApiEndpoint = walletApiEndpoint;
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

    public String getWalletApiEndpoint() {
        return walletApiEndpoint;
    }

    public void setWalletApiEndpoint(String walletApiEndpoint) {
        this.walletApiEndpoint = walletApiEndpoint;
    }

    public void updateValues(RemoteNode node) { //Update everything except id
        if(node == null) return;

        this.setName(node.getName());
        this.setWalletApiEndpoint(node.getWalletApiEndpoint());
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
