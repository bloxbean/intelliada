package com.bloxbean.intelliada.idea.core.util;

import java.util.Objects;

public class Network {
    private String name;
    private String networkId;
    private String protocolMagic;

    public Network(String name, String networkId, String protocolMagic) {
        this.name = name;
        this.networkId = networkId;
        this.protocolMagic = protocolMagic;
    }

    public String getNetworkId() {
        return networkId;
    }

    public String getProtocolMagic() {
        return protocolMagic;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Network network = (Network) o;
        return name.equals(network.name) && networkId.equals(network.networkId) && protocolMagic.equals(network.protocolMagic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, networkId, protocolMagic);
    }

    @Override
    public String toString() {
        return name;
    }
}
