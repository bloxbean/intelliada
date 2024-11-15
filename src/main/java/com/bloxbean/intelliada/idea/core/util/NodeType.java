package com.bloxbean.intelliada.idea.core.util;

import java.util.HashMap;
import java.util.Map;

//This node type is actually stored in the configuration
public enum NodeType {
    EMPTY(""),
    BLOCKFROST_MAINNET("Blockfrost Mainnet"),
    BLOCKFROST_PREPROD("Blockfrost Preprod"),
    BLOCKFROST_PREVIEW("Blockfrost Preview"),
    BLOCKFROST_CUSTOM("Blockfrost Custom"),
    KOIOS_PREPROD("Koios Preprod"),
    KOIOS_MAINNET("Koios Mainnet"),
    KOIOS_CUSTOM("Koios Custom"),
    YaciDevKit("Yaci DevKit");

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
