package com.bloxbean.intelliada.idea.julc.blueprint;

import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JulcBlueprintAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setVisible(false);
            return;
        }
        JulcTomlService tomlService = JulcTomlService.getInstance(project);
        boolean isJulc = tomlService != null && tomlService.isJulcProject();
        e.getPresentation().setVisible(isJulc);
        e.getPresentation().setEnabled(isJulc);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        DialogWrapper dialog = new DialogWrapper(project) {
            {
                init();
                setTitle("julc Blueprint Viewer");
            }

            @Override
            protected @Nullable JComponent createCenterPanel() {
                JulcBlueprintPanel panel = new JulcBlueprintPanel(project);
                JPanel main = panel.getMainPanel();
                main.setPreferredSize(new java.awt.Dimension(800, 500));
                return main;
            }
        };
        dialog.show();
    }

    @NotNull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
