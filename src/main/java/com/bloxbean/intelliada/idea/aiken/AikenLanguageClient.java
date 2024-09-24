package com.bloxbean.intelliada.idea.aiken;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;

public class AikenLanguageClient extends LanguageClientImpl {

    public AikenLanguageClient(Project project) {
        super(project);
    }

    @Override
    public void handleServerStatusChanged(ServerStatus serverStatus) {
        if (serverStatus == ServerStatus.started) {
            triggerChangeConfiguration();
        }
    }

}
