package com.bloxbean.intelliada.idea.nodeint.yano.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FundResponse {
    private String txHash;
    private int index;
    private long lovelace;
}
