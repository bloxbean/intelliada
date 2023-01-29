package com.bloxbean.intelliada.idea.aiken.configuration.ui;

import com.bloxbean.intelliada.idea.aiken.configuration.AikenConfigurationHelperService;
import com.bloxbean.intelliada.idea.aiken.configuration.service.AikenProjectState;
import com.bloxbean.intelliada.idea.common.Tuple;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AikenProjectConfigurationDialog extends DialogWrapper {

    private AikenProjectConfig aikenPrjConfig;

    public AikenProjectConfigurationDialog(@Nullable Project project) {
        super(project);
        aikenPrjConfig = new AikenProjectConfig(project);
        init();
        setTitle("Aiken Project - Configuration");
    }

    public Tuple<AikenProjectState.ConfigType, String> getCompilerSdkId() {
        return aikenPrjConfig.getAikenSdkId();
    }

    public void save(Project project) {
        AikenProjectState projectState = AikenProjectState.getInstance(project);
        if (projectState == null) {
            IdeaUtil.showNotification(project, "Aiken configuration",
                    "Unable to save Aiken configuration for the project", NotificationType.ERROR, null);
            return;
        }

        AikenProjectState.State state = projectState.getState();
        if (state != null) {
            aikenPrjConfig.updateDataToState(state);
            projectState.setState(state);

            if(aikenPrjConfig.isConfigChanged()) {
                AikenConfigurationHelperService.notifyProjectNodeConfigChange(project);
            }
        } else {
            IdeaUtil.showNotification(project, "Aiken project configuration",
                    "Unable to save Aiken configuration for the project !!!", NotificationType.ERROR, null);
        }

    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        return aikenPrjConfig.doValidate();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return aikenPrjConfig.getMainPanel();
    }

}
