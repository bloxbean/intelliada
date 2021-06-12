package com.bloxbean.intelliada.idea.scripts.exception;

public class ScriptGenerationException extends Exception {

    public ScriptGenerationException(String message) {
        super(message);
    }

    public ScriptGenerationException(String message, Exception ex) {
        super(message, ex);
    }
}
