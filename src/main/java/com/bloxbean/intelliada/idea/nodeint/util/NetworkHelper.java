package com.bloxbean.intelliada.idea.nodeint.util;

import com.bloxbean.intelliada.idea.core.util.Networks;
import com.intellij.openapi.util.text.StringUtil;

import java.util.HashMap;
import java.util.Map;

public class NetworkHelper {
    private static NetworkHelper instance;
    private Map<String, String> networkTOExplorerUrlMap;

    private NetworkHelper() {
        networkTOExplorerUrlMap = new HashMap<>();
        networkTOExplorerUrlMap.put(Networks.mainnet().getName(), "https://cexplorer.io");
        networkTOExplorerUrlMap.put(Networks.testnet().getName(), "https://testnet.cexplorer.io");
        networkTOExplorerUrlMap.put(Networks.prepod().getName(), "https://preprod.cexplorer.io");
        networkTOExplorerUrlMap.put(Networks.preview().getName(), "https://preview.cexplorer.io");
    }

    public static NetworkHelper getInstance() {
        if (instance == null) {
            instance = new NetworkHelper();
        }

        return instance;
    }

    public String getExplorerBaseUrl(String network) {
        if (StringUtil.isEmpty(network))
            return null;
        return networkTOExplorerUrlMap.get(network);
    }

    public String getTxnHashUrl(String network, String txnHash) {
        String url = getExplorerBaseUrl(network);
        if (StringUtil.isEmpty(url)) return null;

        return url + "/tx/" + txnHash;
    }

}
