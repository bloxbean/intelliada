package com.bloxbean.intelliada.idea.aiken.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class CreateNewAikenSDKAction extends AnAction {
    public final static String ACTION_ID = CreateNewAikenSDKAction.class.getName();

    public CreateNewAikenSDKAction() {
        super("Add Aiken SDK", "Add a new Aiken SDK", AllIcons.General.AddJdk);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();

        AikenConfigurationHelperService.createOrUpdateLocalSDKConfiguration(project, null);
    }

}
