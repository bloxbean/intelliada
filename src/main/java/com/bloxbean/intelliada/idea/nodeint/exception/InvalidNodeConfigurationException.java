package com.bloxbean.intelliada.idea.nodeint.exception;

public class InvalidNodeConfigurationException extends RuntimeException {
    public InvalidNodeConfigurationException(String messge) {
        super(messge);
    }

    public InvalidNodeConfigurationException(String message, Exception e) {
        super(message, e);
    }
}
