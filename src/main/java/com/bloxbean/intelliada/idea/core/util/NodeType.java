package com.bloxbean.intelliada.idea.core.util;

import java.util.HashMap;
import java.util.Map;

public enum NodeType {
    EMPTY(""),
    CARDANO_WALLET("Cardano Wallet"),
    BLOCKFROST_TESTNET("Blockfrost Testnet"),
    BLOCKFROST_MAINNET("Blockfrost Mainnet");

    private String displayName;

    NodeType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    private static final Map<String, NodeType> nameIndex =
            new HashMap<>(NodeType.values().length);
    static {
        for (NodeType nodeType : NodeType.values()) {
            nameIndex.put(nodeType.name(), nodeType);
        }
    }
    public static NodeType lookupByName(String name) {
        return nameIndex.get(name);
    }
}
