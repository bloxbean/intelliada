package com.bloxbean.intelliada.idea.nodeint.model;

import com.bloxbean.cardano.blockfrost.model.GenesisContent;
import com.bloxbean.intelliada.idea.util.JsonUtil;

public class Genesis  {
    private GenesisContent genesisContent;

    private Genesis(GenesisContent genesisContent) {
        this.genesisContent = genesisContent;
    }
    public static Genesis buildFromGenesisContent(GenesisContent genesisContent) {
        return new Genesis(genesisContent);
    }

    public String toJson() {
        if(genesisContent == null) return null;

        return JsonUtil.getPrettyJson(genesisContent);
    }
}
