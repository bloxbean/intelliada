package com.bloxbean.intelliada.idea.aiken;

import com.bloxbean.intelliada.idea.aiken.configuration.AikenConfigurationHelperService;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider;

import java.util.List;

public class AikenLanguageServer extends ProcessStreamConnectionProvider {

    public AikenLanguageServer(Project project) {
        var aikenSdk = AikenConfigurationHelperService.getCompilerLocalSDK(project);

        if (aikenSdk == null) {
            IdeaUtil.showNotification(project, "Aiken LSP", "Aiken executable not found", NotificationType.WARNING, null);
            return;
        }
        List<String> aikenCmds = aikenSdk.getAikenCommand();

        aikenCmds.add("lsp");
        aikenCmds.add("--stdio");
        super.setCommands(aikenCmds);
        super.setWorkingDirectory(project.getBasePath());
        super.setIncludeSystemEnvironmentVariables(true);
    }
}
