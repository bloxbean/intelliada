package com.bloxbean.intelliada.idea.aiken.configuration;

import com.bloxbean.intelliada.idea.aiken.common.AikenIcons;
import com.bloxbean.intelliada.idea.aiken.configuration.service.AikenProjectState;
import com.bloxbean.intelliada.idea.aiken.configuration.ui.AikenProjectConfigurationDialog;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AikenConfigurationAction extends AnAction {
    public static final String ACTION_ID = AikenConfigurationAction.class.getName();

    public AikenConfigurationAction() {
        super(AikenIcons.AIKEN_ICON);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if(project == null)
            return;

        AikenProjectState aikenProjectState = AikenProjectState.getInstance(project);
        if(aikenProjectState == null) {
            IdeaUtil.showNotification(project, "Aiken project configuration",
                    "Unable to configure Aiken project", NotificationType.ERROR, null);
            return;
        }

        AikenProjectConfigurationDialog dialog = new AikenProjectConfigurationDialog(project);

        boolean ok = dialog.showAndGet();
        if(ok) {
            dialog.save(project);
        } else {

        }

    }
}
