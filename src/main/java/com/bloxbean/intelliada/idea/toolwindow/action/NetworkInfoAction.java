package com.bloxbean.intelliada.idea.toolwindow.action;

import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.action.BaseAction;
import com.bloxbean.intelliada.idea.nodeint.exception.TargetNodeNotConfigured;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListenerAdapter;
import com.bloxbean.intelliada.idea.nodeint.service.api.NetworkInfoService;
import com.bloxbean.intelliada.idea.nodeint.service.impl.NetworkServiceImpl;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.bloxbean.intelliada.idea.util.JsonUtil;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class NetworkInfoAction extends BaseAction {
    private RemoteNode node;

    public NetworkInfoAction(RemoteNode node) {
        super("Network Info", "Get Network Info", AllIcons.General.Information);
        this.node = node;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();

        if(project == null)
            return;

        if(node == null)
            return;

        CardanoConsole console = CardanoConsole.getConsole(project);

        LogListenerAdapter logListenerAdapter = new LogListenerAdapter(console);

        Task.Backgroundable task = new Task.Backgroundable(project, "Network Info") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                console.showInfoMessage(String.format("Getting network information ...\n"));

                if(node == null) {
                    console.showErrorMessage("Remote node cannot be null. Please select a valid node.");
                    return;
                }

                try {
                    NetworkInfoService networkService = new NetworkServiceImpl(node, logListenerAdapter);
                    Result result = networkService.getNetworkInfo();
                    console.clear();
                    if(result.isSuccessful()) {
                        console.showInfoMessage(JsonUtil.getPrettyJson(result.getValue()));
//                        IdeaUtil.showNotification(project, getTitle(), String.format("%s was successful", getTxnCommand()), NotificationType.INFORMATION, null);
                    } else {
                        console.showErrorMessage(result.toString());
                        console.showErrorMessage(String.format("%s failed", getTxnCommand()));
                        IdeaUtil.showNotification(project, getTitle(), String.format("%s failed", getTxnCommand()), NotificationType.ERROR, null);

                    }
                } catch (TargetNodeNotConfigured ex) {
                    warnTargetNodeNotConfigured(project, "NetworkInfo");
                }catch (Exception exception) {
                    console.showErrorMessage("Error getting network info", exception);
                }
            }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }

    private String getTxnCommand() {
        return "Get Network Info";
    }
}

