package com.bloxbean.intelliada.idea.joust.wizard;

/**
 * The templates of {@code joust new -t} (ADR-004 D9, F27). Only Gradle takes a package; the Node and web templates go
 * through {@code npm} and {@code node} and need the network.
 */
public enum JoustTemplate {
    GRADLE("gradle", "Gradle (Java build)"),
    NODE("node", "Node (npm script)"),
    WEB("web", "Web (browser page, npm)");

    private final String cliName;
    private final String label;

    JoustTemplate(String cliName, String label) {
        this.cliName = cliName;
        this.label = label;
    }

    /** The value passed to {@code -t}. */
    public String cliName() {
        return cliName;
    }

    /** True when {@code -p <package>} may be passed (Gradle only; F27). */
    public boolean takesPackage() {
        return this == GRADLE;
    }

    /** True when {@code joust new} runs {@code npm} and {@code node} for this template and needs the network. */
    public boolean usesNpm() {
        return this != GRADLE;
    }

    @Override
    public String toString() {
        return label;
    }
}
