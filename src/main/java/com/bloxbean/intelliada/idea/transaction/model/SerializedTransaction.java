package com.bloxbean.intelliada.idea.transaction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//Serialize to Json
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SerializedTransaction {
    public final static String TX_MARY_ERA = "Tx MaryEra";
    private String type;
    private String description = "";
    private String cborHex;
}
