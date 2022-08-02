package com.bloxbean.intelliada.idea.core.util;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;

public class NetworkUtil {

    public static Network convertToCLNetwork(com.bloxbean.intelliada.idea.core.util.Network network) {
        com.bloxbean.cardano.client.common.model.Network clNetwork = null;
        if (Networks.mainnet().equals(network))
            clNetwork = com.bloxbean.cardano.client.common.model.Networks.mainnet();
        else
            clNetwork = com.bloxbean.cardano.client.common.model.Networks.testnet();

        return clNetwork;
    }

    public static boolean isMainnet(RemoteNode node) {
        if (String.valueOf(com.bloxbean.cardano.client.common.model.Networks.mainnet().getProtocolMagic()).equals(node.getProtocolMagic()))
            return true;
        else
            return false;
    }
}
