package com.bloxbean.intelliada.idea.joust.wizard;

/** The contract languages of {@code joust new -l} (ADR-004 D9, F27). */
public enum JoustLanguage {
    JSC("jsc", ".jsc (JOUST language, preview)"),
    JAVA("java", "Java (@StateContract)");

    private final String cliName;
    private final String label;

    JoustLanguage(String cliName, String label) {
        this.cliName = cliName;
        this.label = label;
    }

    /** The value passed to {@code -l}. */
    public String cliName() {
        return cliName;
    }

    @Override
    public String toString() {
        return label;
    }
}
