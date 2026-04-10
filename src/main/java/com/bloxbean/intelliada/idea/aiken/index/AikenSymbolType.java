package com.bloxbean.intelliada.idea.aiken.index;

/**
 * Types of symbols that can be indexed in Aiken files
 */
public enum AikenSymbolType {
    FUNCTION("function"),
    TYPE("type"),
    CONSTRUCTOR("constructor"),
    VALIDATOR("validator"),
    CONSTANT("constant"),
    VARIABLE("variable"),
    TEST("test"),
    BENCHMARK("benchmark");

    private final String displayName;

    AikenSymbolType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}