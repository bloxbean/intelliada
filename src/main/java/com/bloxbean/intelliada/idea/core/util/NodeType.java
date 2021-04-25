package com.bloxbean.intelliada.idea.core.util;

public enum NodeType {
    EMPTY(""),
    CARDANO_WALLET("Cardano Wallet"),
    BLOCKFROST_TESTNET("Blockfrost Testnet"),
    BLOCKFROST_MAINNET("Blockfrost Mainnet");

    private String name;

    NodeType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
