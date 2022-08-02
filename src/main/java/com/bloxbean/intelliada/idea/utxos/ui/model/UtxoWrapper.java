package com.bloxbean.intelliada.idea.utxos.ui.model;

import com.bloxbean.cardano.client.api.model.Utxo;

/**
 * Wrapper class for UI only
 */
public class UtxoWrapper {
    private final Utxo utxo;

    public UtxoWrapper(Utxo utxo) {
        this.utxo = utxo;
    }

    public Utxo getUtxo() {
        return utxo;
    }

    @Override
    public String toString() {
        return utxo.getTxHash() + "#" + utxo.getOutputIndex();
    }
}
