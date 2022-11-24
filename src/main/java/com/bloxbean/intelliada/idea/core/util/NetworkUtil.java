package com.bloxbean.intelliada.idea.core.util;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;

public class NetworkUtil {

    public static Network convertToCLNetwork(com.bloxbean.intelliada.idea.core.util.Network network) {
        com.bloxbean.cardano.client.common.model.Network clNetwork = null;
        if (Networks.mainnet().equals(network))
            clNetwork = com.bloxbean.cardano.client.common.model.Networks.mainnet();
        else if (Networks.testnet().equals(network))
            clNetwork = com.bloxbean.cardano.client.common.model.Networks.testnet();
        else if (Networks.prepod().equals(network))
            clNetwork = com.bloxbean.cardano.client.common.model.Networks.preprod();
        else if (Networks.preview().equals(network))
            clNetwork = com.bloxbean.cardano.client.common.model.Networks.preview();
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

    public static com.bloxbean.intelliada.idea.core.util.Network getNetworkType(RemoteNode node) {
        if (Networks.mainnet().getName().equals(node.getNetwork())) {
            return Networks.mainnet();
        } else if (Networks.testnet().getName().equals(node.getNetwork())) {
            return Networks.testnet();
        } else if (Networks.prepod().getName().equals(node.getNetwork())) {
            return Networks.prepod();
        } if (Networks.preview().getName().equals(node.getNetwork())) {
            return Networks.preview();
        } else
            return Networks.testnet();
    }

}
