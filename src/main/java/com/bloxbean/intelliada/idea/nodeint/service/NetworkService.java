package com.bloxbean.intelliada.idea.nodeint.service;

import com.bloxbean.cardano.blockfrost.api.CardanoLedgerApi;
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.model.GenesisContent;

public class NetworkService {

    public Object getNetworkInfo() throws Exception {
        ApiClient apiClient = new ApiClient();
        apiClient.setHost("cardano-testnet.blockfrost.io");
        apiClient.setBasePath("/api/v0");
        apiClient.setPort(443);
        apiClient.setScheme("https");
        apiClient.setRequestInterceptor(reqBuilder -> reqBuilder.setHeader("project_id", "Z9JP6nrZp5xtaFnopu9aknGxdQd3pZHG"));
//
        CardanoLedgerApi ledgerApi = new CardanoLedgerApi(apiClient);
        GenesisContent genesisContent = ledgerApi.genesisGet();
        System.out.println(genesisContent);
        return null;
    }

    public static void main(String[] args) throws Exception {
        new NetworkService().getNetworkInfo();
    }
}
