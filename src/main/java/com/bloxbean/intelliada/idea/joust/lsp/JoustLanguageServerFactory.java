package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.redhat.devtools.lsp4ij.LanguageServerEnablementSupport;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;


/**
 * ADR-004 D3: the JOUST language server for LSP4IJ. It is enabled only once the user has configured and validated the
 * tools (D4); otherwise opening a .jsc starts nothing and {@link com.bloxbean.intelliada.idea.joust.editor.JoustEditorListener}
 * offers Configure JOUST…
 */
public final class JoustLanguageServerFactory implements LanguageServerFactory, LanguageServerEnablementSupport {

    /** The user's own on/off switch from LSP4IJ's UI, per project (kept on the project, so it goes with it). */
    private static final Key<Boolean> USER_DISABLED = Key.create("intelliada.joust.userDisabled");

    @Override
    public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
        return new JoustStreamConnectionProvider(project);
    }

    @Override
    public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project) {
        return new JoustLanguageClient(project);
    }

    @Override
    public @NotNull LSPClientFeatures createClientFeatures() {
        return new JoustClientFeatures();
    }

    @Override
    public @NotNull Class<? extends LanguageServer> getServerInterface() {
        return JoustLanguageServerApi.class;
    }

    @Override
    public boolean isEnabled(@NotNull Project project) {
        return !Boolean.TRUE.equals(project.getUserData(USER_DISABLED)) && JoustToolsState.getInstance().looksConfigured();
    }

    @Override
    public void setEnabled(boolean enabled, @NotNull Project project) {
        project.putUserData(USER_DISABLED, enabled ? null : Boolean.TRUE);
    }
}
