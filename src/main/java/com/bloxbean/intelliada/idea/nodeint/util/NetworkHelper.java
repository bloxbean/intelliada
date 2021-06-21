package com.bloxbean.intelliada.idea.nodeint.util;

import com.intellij.openapi.util.text.StringUtil;

import java.util.HashMap;
import java.util.Map;

public class NetworkHelper {
    public static final String MAINNET = "mainnet";
    public static final String TESTNET = "testnet";
    private static NetworkHelper instance;
    private Map<String, String> networkTOExplorerUrlMap;

    private NetworkHelper() {
        networkTOExplorerUrlMap = new HashMap<>();
        networkTOExplorerUrlMap.put(MAINNET, "https://explorer.cardano.org/en");
        networkTOExplorerUrlMap.put(TESTNET, "https://explorer.cardano-testnet.iohkdev.io/en");
    }

    public static NetworkHelper getInstance() {
        if(instance == null) {
            instance = new NetworkHelper();
        }

        return instance;
    }

    public String getExplorerBaseUrl(String network) {
        if(StringUtil.isEmpty(network))
            return null;
        return networkTOExplorerUrlMap.get(network);
    }

    public String getTxnHashUrl(String network, String txnHash) {
        String url = getExplorerBaseUrl(network);
        if(StringUtil.isEmpty(url)) return null;

        return url + "/transaction?id=" + txnHash;
    }

}
