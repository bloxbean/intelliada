package com.bloxbean.intelliada.idea.nodeint.yano.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RollbackResponse {
    private String message;
    private long slot;
    private long blockNumber;
}
