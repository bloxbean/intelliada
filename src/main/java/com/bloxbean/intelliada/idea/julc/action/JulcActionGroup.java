package com.bloxbean.intelliada.idea.julc.action;

import com.bloxbean.intelliada.idea.julc.common.JulcIcons;
import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class JulcActionGroup extends DefaultActionGroup {

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            event.getPresentation().setVisible(false);
            return;
        }

        boolean isJulcProject = ReadAction.nonBlocking(() -> {
            JulcTomlService tomlService = JulcTomlService.getInstance(project);
            return tomlService != null && tomlService.isJulcProject();
        }).executeSynchronously();

        event.getPresentation().setVisible(isJulcProject);
        event.getPresentation().setIcon(JulcIcons.JULC_ICON);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
