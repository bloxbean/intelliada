package com.bloxbean.intelliada.idea.aiken.compile;

public interface CompileService {
    void compile(String source, CompilationResultListener compilationResultListener);
}
