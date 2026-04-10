package com.bloxbean.intelliada.idea.nodeint.yano.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EpochShiftResponse {
    private String message;
    private long shiftMillis;
    private String newSystemStart;
    private long genesisSlot;
}
