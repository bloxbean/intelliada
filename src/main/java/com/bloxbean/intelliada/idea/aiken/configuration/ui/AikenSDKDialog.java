package com.bloxbean.intelliada.idea.aiken.configuration.ui;

import com.bloxbean.intelliada.idea.aiken.configuration.AikenSDK;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.twelvemonkeys.lang.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AikenSDKDialog extends DialogWrapper {

    private AikenSDKPanel localSDKPanel;

    public AikenSDKDialog(Project project) {
        this(project, null);
    }

    public AikenSDKDialog(Project project, AikenSDK sdk) {
        super(project);
        localSDKPanel = new AikenSDKPanel(sdk);
        init();
        setTitle("Aiken SDK");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return localSDKPanel.getMainPanel();
    }

    public String getPath() {
        return localSDKPanel.getPath();
    }

    public String getName() {
        return localSDKPanel.getName();
    }

    public String getVersion() {
        return localSDKPanel.getVersion();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if(StringUtil.isEmpty(localSDKPanel.getName())) {
            return new ValidationInfo("Invalid Name", localSDKPanel.getNameTf());
        }

        if(StringUtil.isEmpty(localSDKPanel.getPath())) {
            return new ValidationInfo("Invalid Aiken Path", localSDKPanel.getPathTf());
        }

        if(StringUtil.isEmpty(localSDKPanel.getVersion())) {
            return new ValidationInfo("Invalid Version Number or Version could not be determined", localSDKPanel.getVersionTf());
        }

        return null;
    }
}
