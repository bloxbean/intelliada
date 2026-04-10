package com.bloxbean.intelliada.idea.julc.configuration.ui;

import com.bloxbean.intelliada.idea.julc.configuration.JulcConfigurationHelperService;
import com.bloxbean.intelliada.idea.julc.configuration.service.JulcProjectState;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JulcProjectConfigurationDialog extends DialogWrapper {

    private final JulcProjectConfig julcProjectConfig;

    public JulcProjectConfigurationDialog(@Nullable Project project) {
        super(project);
        julcProjectConfig = new JulcProjectConfig(project);
        init();
        setTitle("julc Project - Configuration");
    }

    public void save(Project project) {
        JulcProjectState projectState = JulcProjectState.getInstance(project);
        if (projectState == null) {
            IdeaUtil.showNotification(project, "julc configuration",
                    "Unable to save julc configuration for the project", NotificationType.ERROR, null);
            return;
        }

        JulcProjectState.State state = projectState.getState();
        if (state != null) {
            julcProjectConfig.updateDataToState(state);
            projectState.setState(state);

            if (julcProjectConfig.isConfigChanged()) {
                JulcConfigurationHelperService.notifyProjectConfigChange(project);
            }
        } else {
            IdeaUtil.showNotification(project, "julc project configuration",
                    "Unable to save julc configuration for the project !!!", NotificationType.ERROR, null);
        }
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        return julcProjectConfig.doValidate();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return julcProjectConfig.getMainPanel();
    }
}
