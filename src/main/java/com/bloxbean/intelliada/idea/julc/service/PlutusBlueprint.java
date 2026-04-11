package com.bloxbean.intelliada.idea.julc.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * CIP-57 Plutus Blueprint model.
 * Parsed from build/plutus/plutus.json or META-INF/plutus/*.plutus.json
 */
@Data
public class PlutusBlueprint {
    private Preamble preamble;
    private List<ValidatorInfo> validators;

    @Data
    @AllArgsConstructor
    public static class Preamble {
        private String title;
        private String version;
    }

    @Data
    @AllArgsConstructor
    public static class ValidatorInfo {
        private String title;
        private String hash;
        private String compiledCode;
        private long sizeBytes;
        private boolean parameterized;
    }
}
