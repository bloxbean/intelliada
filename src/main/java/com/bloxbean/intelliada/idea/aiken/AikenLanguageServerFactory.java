package com.bloxbean.intelliada.idea.aiken;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerEnablementSupport;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.jetbrains.annotations.NotNull;

public class AikenLanguageServerFactory implements LanguageServerFactory, LanguageServerEnablementSupport {
    private boolean enabled = true;
    @Override
    public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
       return new AikenLanguageServer(project);
    }

    @Override
    public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project) {
        var languageClient = new AikenLanguageClient(project);
        return languageClient;
    }

    @Override
    public boolean isEnabled(@NotNull Project project) {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled, @NotNull Project project) {
        this.enabled = enabled;
    }

}
