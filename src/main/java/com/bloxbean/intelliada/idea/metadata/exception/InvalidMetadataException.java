package com.bloxbean.intelliada.idea.metadata.exception;

public class InvalidMetadataException extends Exception {
    public InvalidMetadataException(String msg) {
        super(msg);
    }

    public InvalidMetadataException(String msg, Exception ex) {
        super(msg, ex);
    }
}
