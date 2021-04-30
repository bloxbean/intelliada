package com.bloxbean.intelliada.idea.nodeint.service.blockfrost;

import com.bloxbean.cardano.blockfrost.api.CardanoLedgerApi;
import com.bloxbean.cardano.blockfrost.model.GenesisContent;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.nodeint.model.Genesis;
import com.bloxbean.intelliada.idea.nodeint.model.Result;
import com.bloxbean.intelliada.idea.nodeint.service.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.NetworkInfoService;

public class BFNetworkInfoServiceImpl extends BFBaseService implements NetworkInfoService {

    public BFNetworkInfoServiceImpl(RemoteNode remoteNode, LogListener logListener) {
        super(remoteNode, logListener);
    }

    @Override
    public Result<Genesis> getNetworkInfo() {
        CardanoLedgerApi ledgerApi = new CardanoLedgerApi(apiClient);
        GenesisContent genesisContent = null;
        try {
            genesisContent = ledgerApi.genesisGet();
        } catch (Exception e) {
            logListener.error("Error getting genesis info", e);
            return Result.error("Error getting genesis info : " + e.getMessage());
        }

        Genesis genesis = Genesis.buildFromGenesisContent(genesisContent);
        Result result = Result.create(true, genesis.toJson()).withValue(genesis);

        return result;

    }
}
