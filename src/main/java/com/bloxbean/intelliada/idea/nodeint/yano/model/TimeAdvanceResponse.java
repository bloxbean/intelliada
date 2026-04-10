package com.bloxbean.intelliada.idea.nodeint.yano.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TimeAdvanceResponse {
    private String message;
    private long newSlot;
    private long newBlockNumber;
    private int blocksProduced;
}
