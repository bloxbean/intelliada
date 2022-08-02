package com.bloxbean.intelliada.idea.nodeint.service.api;

public interface LogListener {
    void info(String msg);

    void error(String msg);

    void warn(String msg);

    default void error(String msg, Throwable t) {

    }

    default void warn(String msg, Throwable t) {

    }

    default void info(String msg, boolean noLineBreak) {

    }

    default void printWait(String msg) {

    }
}
