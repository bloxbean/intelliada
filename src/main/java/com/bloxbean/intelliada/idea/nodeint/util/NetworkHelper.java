package com.bloxbean.intelliada.idea.nodeint.util;

import com.bloxbean.intelliada.idea.core.util.Networks;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.intellij.openapi.util.text.StringUtil;

import java.util.HashMap;
import java.util.Map;

public class NetworkHelper {
    private static NetworkHelper instance;
    private Map<String, String> networkTOExplorerUrlMap;

    private NetworkHelper() {
        networkTOExplorerUrlMap = new HashMap<>();
        networkTOExplorerUrlMap.put(Networks.mainnet().getName(), "https://cardanoscan.io");
        networkTOExplorerUrlMap.put(Networks.testnet().getName(), "https://testnet.cardanoscan.io");
        networkTOExplorerUrlMap.put(Networks.prepod().getName(), "https://preprod.cardanoscan.io");
        networkTOExplorerUrlMap.put(Networks.preview().getName(), "https://preview.cardanoscan.io");
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

    public String getTxnHashUrl(NodeType nodeType, String network, String txnHash) {
        if (NodeType.YaciDevKit.equals(nodeType)) {
            return "http://localhost:5173/transactions/" + txnHash;
        }

        String url = getExplorerBaseUrl(network);
        if (StringUtil.isEmpty(url)) return null;
        return url + "/transaction/" + txnHash;
    }

}
