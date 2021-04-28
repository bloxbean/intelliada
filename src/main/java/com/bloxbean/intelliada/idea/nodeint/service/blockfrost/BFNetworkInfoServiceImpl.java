package com.bloxbean.intelliada.idea.nodeint.service.blockfrost;

import com.bloxbean.cardano.blockfrost.api.CardanoLedgerApi;
import com.bloxbean.cardano.blockfrost.model.GenesisContent;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.nodeint.model.Result;
import com.bloxbean.intelliada.idea.nodeint.service.NetworkInfoService;

public class BFNetworkInfoServiceImpl extends BFBaseService implements NetworkInfoService {

    public BFNetworkInfoServiceImpl(RemoteNode remoteNode) {
        super(remoteNode);
    }

    @Override
    public Result<GenesisContent> getNetworkInfo() {
        CardanoLedgerApi ledgerApi = new CardanoLedgerApi(apiClient);
        GenesisContent genesisContent = null;
        try {
            genesisContent = ledgerApi.genesisGet();
        } catch (Exception e) {
            logListener.error("Error getting genesis info", e);
        }

        Result result = Result.create(true, genesisContent.toString()).withValue(genesisContent);

        return result;

    }
}
