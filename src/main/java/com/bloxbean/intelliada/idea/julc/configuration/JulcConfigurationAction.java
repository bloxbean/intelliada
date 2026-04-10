package com.bloxbean.intelliada.idea.julc.configuration;

import com.bloxbean.intelliada.idea.julc.configuration.service.JulcProjectState;
import com.bloxbean.intelliada.idea.julc.configuration.ui.JulcProjectConfigurationDialog;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class JulcConfigurationAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        JulcProjectState projectState = JulcProjectState.getInstance(project);
        if (projectState == null) {
            IdeaUtil.showNotification(project, "julc project configuration",
                    "Unable to configure julc project", NotificationType.ERROR, null);
            return;
        }

        JulcProjectConfigurationDialog dialog = new JulcProjectConfigurationDialog(project);
        boolean ok = dialog.showAndGet();
        if (ok) {
            dialog.save(project);
        }
    }
}
