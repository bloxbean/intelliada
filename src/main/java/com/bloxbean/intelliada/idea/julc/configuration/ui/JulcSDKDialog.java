package com.bloxbean.intelliada.idea.julc.configuration.ui;

import com.bloxbean.intelliada.idea.julc.configuration.JulcSDK;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JulcSDKDialog extends DialogWrapper {

    private final JulcSDKPanel sdkPanel;

    public JulcSDKDialog(Project project) {
        this(project, null);
    }

    public JulcSDKDialog(Project project, JulcSDK sdk) {
        super(project);
        sdkPanel = new JulcSDKPanel(sdk);
        init();
        setTitle("julc SDK");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return sdkPanel.getMainPanel();
    }

    public String getPath() {
        return sdkPanel.getPath();
    }

    public String getName() {
        return sdkPanel.getName();
    }

    public String getVersion() {
        return sdkPanel.getVersion();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (StringUtil.isEmpty(sdkPanel.getName())) {
            return new ValidationInfo("Invalid Name", sdkPanel.getNameTf());
        }
        if (StringUtil.isEmpty(sdkPanel.getPath())) {
            return new ValidationInfo("Invalid julc Path", sdkPanel.getPathTf());
        }
        if (StringUtil.isEmpty(sdkPanel.getVersion())) {
            return new ValidationInfo("Invalid Version Number or Version could not be determined", sdkPanel.getVersionTf());
        }
        return null;
    }
}
