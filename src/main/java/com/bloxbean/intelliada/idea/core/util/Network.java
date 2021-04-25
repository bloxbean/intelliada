package com.bloxbean.intelliada.idea.core.util;

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
}
