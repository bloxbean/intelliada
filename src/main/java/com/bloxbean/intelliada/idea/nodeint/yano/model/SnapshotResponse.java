package com.bloxbean.intelliada.idea.nodeint.yano.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SnapshotResponse {
    private String name;
    private long slot;
    private long blockNumber;
    private String createdAt;
}
