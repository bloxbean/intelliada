package com.bloxbean.intelliada.idea.julc.annotator.validate;

/**
 * A diagnostic message with source location and optional suggestion.
 * Adapted from julc-compiler's CompilerDiagnostic for use in IntelliAda.
 * This class can be removed once julc-compiler is available as a JVM 21 compatible dependency.
 */
public class JulcDiagnostic {

    public enum Level { ERROR, WARNING, INFO }

    private final Level level;
    private final String message;
    private final String fileName;
    private final int line;
    private final int column;
    private final String suggestion;

    public JulcDiagnostic(Level level, String message, String fileName, int line, int column, String suggestion) {
        this.level = level;
        this.message = message;
        this.fileName = fileName;
        this.line = line;
        this.column = column;
        this.suggestion = suggestion;
    }

    public JulcDiagnostic(Level level, String message, String fileName, int line, int column) {
        this(level, message, fileName, line, column, null);
    }

    public Level level() { return level; }
    public String message() { return message; }
    public String fileName() { return fileName; }
    public int line() { return line; }
    public int column() { return column; }
    public String suggestion() { return suggestion; }

    public boolean isError() { return level == Level.ERROR; }
    public boolean hasSuggestion() { return suggestion != null && !suggestion.isEmpty(); }
}
