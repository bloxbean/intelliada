package com.bloxbean.intelliada.idea.scripts.action;

import com.bloxbean.intelliada.idea.scripts.ui.ScriptChooserDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ListScriptsAction extends AnAction {

    public ListScriptsAction() {
        super(AllIcons.FileTypes.Archive);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if(project == null)
            return;

        ScriptChooserDialog dialog = new ScriptChooserDialog(project);
        boolean ok = dialog.showAndGet();
        if(!ok)
            return;

    }

    @Override
    public boolean isDumbAware() {
        return true;
    }
}
