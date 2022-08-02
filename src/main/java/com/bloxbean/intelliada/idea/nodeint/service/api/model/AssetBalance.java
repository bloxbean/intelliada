package com.bloxbean.intelliada.idea.nodeint.service.api.model;

import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.intelliada.idea.util.AdaConversionUtil;
import com.intellij.openapi.util.text.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

import static com.bloxbean.intelliada.idea.util.AdaConversionUtil.LOVELACE;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AssetBalance {
    private String unit;
    private String fingerPrint;
    private BigInteger quantity;

    public String getFingerPrint() {
        if (!StringUtil.isEmpty(fingerPrint))
            return fingerPrint;
        else {
            if (unit == null || LOVELACE.equals(unit))
                return unit;

            Tuple<String, String> pidAssetName = AssetUtil.getPolicyIdAndAssetName(unit);
            fingerPrint = AssetUtil.calculateFingerPrint(pidAssetName._1, pidAssetName._2);
            return fingerPrint;
        }
    }

    public String toString() {
        String result = "";
        if (quantity != null && LOVELACE.equals(unit)) {
            String formattedAmount = AdaConversionUtil.toAssetDecimalAmtFormatted(quantity, (int) 6);
            result += formattedAmount + " Ada";
            result += " (" + AdaConversionUtil.toAssetDecimalAmtFormatted(quantity, 0) + " lovelace)";
        } else if (quantity != null) {
            result += AdaConversionUtil.toAssetDecimalAmtFormatted(quantity, 0);
            result += " - " + getFingerPrint();
        }

        return result;
    }

}
