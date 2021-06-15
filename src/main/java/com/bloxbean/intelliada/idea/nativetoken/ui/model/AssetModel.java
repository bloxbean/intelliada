package com.bloxbean.intelliada.idea.nativetoken.ui.model;

import com.bloxbean.intelliada.idea.util.AdaConversionUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetModel {
    private String name;
    private BigInteger quantity;

    public String toString() {
        return name + " - " + AdaConversionUtil.toAssetDecimalAmtFormatted(quantity, 0);
    }
}
