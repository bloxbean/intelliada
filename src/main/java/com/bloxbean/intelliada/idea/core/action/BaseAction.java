package com.bloxbean.intelliada.idea.core.action;

import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;

import javax.swing.*;

public abstract class BaseAction extends AnAction {

    public BaseAction() {
        super();
    }

    public BaseAction(Icon icon) {
        super(icon);
    }

    public BaseAction(String title, String desc, Icon icon) {
        super(title, desc, icon);
    }

    public void warnTargetNodeNotConfigured(Project project, String actionTitle) {
        IdeaUtil.showNotification(project, actionTitle, "Target Cardano node is not configured. Please select a default node.",
                    NotificationType.ERROR, null);

    }
}
