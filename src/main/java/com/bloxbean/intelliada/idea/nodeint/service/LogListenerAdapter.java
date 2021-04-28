package com.bloxbean.intelliada.idea.nodeint.service;

import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;

public class LogListenerAdapter implements LogListener {
    private CardanoConsole console;

    public LogListenerAdapter(CardanoConsole console) {
        this.console = console;
    }

    @Override
    public void info(String msg) {
        console.showInfoMessage(msg);
    }

    @Override
    public void error(String msg) {
        console.showErrorMessage(msg);
    }

    @Override
    public void warn(String msg) {
        console.showWarningMessage(msg);
    }

    @Override
    public void error(String msg, Throwable t) {
        console.showErrorMessage(msg);
        console.showWarningMessage(t.getMessage());
    }
}

