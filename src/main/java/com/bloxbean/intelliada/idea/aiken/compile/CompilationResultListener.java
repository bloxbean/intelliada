package com.bloxbean.intelliada.idea.aiken.compile;

import com.intellij.execution.process.OSProcessHandler;

public interface CompilationResultListener {
    default public void attachProcess(OSProcessHandler processHandler) {

    }

    public void error(String message);
    public void info(String message);
    public void warn(String msg);

    @Deprecated
    public void onSuccessful(String sourceFile);
    public void onFailure(String sourceFile, Throwable t);

    default public void error(String message, Throwable t) {
        error(message + " : " + t.getMessage());
    }

    default public void onSuccessfulCompile(String sourceFile, String outputFile, String contractHash) {}

}
