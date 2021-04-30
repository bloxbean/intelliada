package com.bloxbean.intelliada.idea.nodeint.service.api;

public interface LogListener {
    public void info(String msg);
    public void error(String msg);
    public void warn(String msg);
    default public void error(String msg, Throwable t) {

    }
    default public void warn(String msg, Throwable t) {

    }
}
