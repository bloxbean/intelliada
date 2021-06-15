package com.bloxbean.intelliada.idea.scripts.exception;

public class JsonParsingException extends RuntimeException {
    public JsonParsingException(String message) {
        super(message);
    }

    public JsonParsingException(String message, Exception ex) {
        super(message, ex);
    }
}
