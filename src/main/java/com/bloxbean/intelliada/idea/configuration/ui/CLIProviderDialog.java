package com.bloxbean.intelliada.idea.configuration.ui;

import com.bloxbean.intelliada.idea.configuration.model.CLIProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CLIProviderDialog extends DialogWrapper {

    private LocalYaciDevKitConfigPanel localSDKPanel;

    public CLIProviderDialog(Project project) {
        this(project, null);
    }

    public CLIProviderDialog(Project project, CLIProvider provider) {
        super(project);
       // localSDKPanel = new LocalYaciDevKitConfigPanel(provider);
        init();
        setTitle("Cardano Installation");
    }

    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        return localSDKPanel.getMainPanel();
    }

    public String getHome() {
        return localSDKPanel.getHome();
    }

    public String getName() {
        return localSDKPanel.getName();
    }

    public String getVersion() {
        return localSDKPanel.getVersion();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (StringUtil.isEmpty(localSDKPanel.getName())) {
            return new ValidationInfo("Invalid Name", localSDKPanel.getNameTf());
        }

        if (StringUtil.isEmpty(localSDKPanel.getHome())) {
            return new ValidationInfo("Invalid Cardano CLI Folder", localSDKPanel.getHomeTf());
        }

        if (StringUtil.isEmpty(localSDKPanel.getVersion())) {
            return new ValidationInfo("Invalid Version Number or Version could not be determined", localSDKPanel.getVersionTf());
        }

        return null;
    }
}
