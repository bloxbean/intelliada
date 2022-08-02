package com.bloxbean.intelliada.idea.scripts.service;

import com.bloxbean.intelliada.idea.scripts.ui.ScriptChooserDialog;
import com.intellij.openapi.project.Project;

public class ScriptChooser {

    public static ScriptInfo selectScript(Project project) {
        return selectScript(project, false);
    }

    public static ScriptInfo selectScript(Project project, boolean showSigScriptOnly) {
        ScriptChooserDialog dialog = new ScriptChooserDialog(project, showSigScriptOnly);
        boolean ok = dialog.showAndGet();
        if (!ok)
            return null;

        return dialog.getSelectedScript();
    }
}
