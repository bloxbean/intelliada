package com.bloxbean.intelliada.idea.aiken.compile;

import com.intellij.openapi.diagnostic.Logger;

public abstract class BaseCompileService implements CompileService{
    private final static Logger LOG = Logger.getInstance(BaseCompileService.class);

    protected void failed(CompilationResultListener resultListener, String source, String message, Throwable t) {
        resultListener.error(message);
        resultListener.onFailure(source, t);
    }
}
