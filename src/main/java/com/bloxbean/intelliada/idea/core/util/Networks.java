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

    public static Network prepod() {
        Network testnet = new Network("prepod", "0", "1");
        return testnet;
    }

    public static Network preview() {
        Network testnet = new Network("preview", "0", "2");
        return testnet;
    }
}
