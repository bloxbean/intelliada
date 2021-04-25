package com.bloxbean.intelliada.idea.core.util;

public class Networks {
    public static Network mainnet() {
        Network mainnet = new Network("mainnet", "1", "764824073");
        return mainnet;
    }

    public static Network testnet() {
        Network testnet = new Network("testnet", "0", "1097911063");
        return testnet;
    }
}
