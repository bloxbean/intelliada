package com.bloxbean.intelliada.idea.toolwindow.action;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.nodeint.service.NetworkService;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class NetworkInfoAction extends AnAction {
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

        Task.Backgroundable task = new Task.Backgroundable(project, "Network Info") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                console.showInfoMessage(String.format("Getting network information ...\n"));

                NetworkService networkService = new NetworkService();
                try {
//                    Object networkInfo = networkService.getNetworkInfo(node);
//                    console.showInfoMessage(networkInfo.toString());
                } catch (Exception exception) {
                    exception.printStackTrace();
                    console.showErrorMessage("Error getting network info", exception);
                }
            }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));

    }
}

