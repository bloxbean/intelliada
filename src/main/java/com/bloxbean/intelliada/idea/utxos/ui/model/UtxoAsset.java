package com.bloxbean.intelliada.idea.utxos.ui.model;

import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.intelliada.idea.util.AdaConversionUtil;
import com.intellij.openapi.util.text.StringUtil;

import java.math.BigInteger;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

public class UtxoAsset {
    private String unit;
    private BigInteger quantity;
    private String fingerPrint;

    public UtxoAsset(String unit, BigInteger quantity) {
        this.unit = unit;
        this.quantity = quantity;
        calculateFingerprint();
    }

    private void calculateFingerprint() {
        if(StringUtil.isEmpty(unit) || LOVELACE.equals(unit))
            return;

        Tuple<String, String> pidAssetName = AssetUtil.getPolicyIdAndAssetName(unit);
        fingerPrint = AssetUtil.calculateFingerPrint(pidAssetName._1, pidAssetName._2);
    }

    public String getUnit() {
        return unit;
    }

    public BigInteger getQuantity() {
        return quantity;
    }

    public String getFingerPrint() {
        return fingerPrint;
    }

    @Override
    public String toString() {
        if(LOVELACE.equals(unit)) {
            if (quantity != null)
                return "Lovelace  (" + AdaConversionUtil.toAssetDecimalAmtFormatted(quantity, 0) + ")";
            else
                return "Lovelace  ( 0 )";
        } else {
            if (quantity != null)
                return fingerPrint + "  (" + AdaConversionUtil.toAssetDecimalAmtFormatted(quantity, 0) + ")";
            else
                return fingerPrint + "  ( 0 )";
        }
    }
}
