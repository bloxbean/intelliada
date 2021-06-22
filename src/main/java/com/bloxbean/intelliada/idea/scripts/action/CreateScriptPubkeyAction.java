package com.bloxbean.intelliada.idea.scripts.action;

import com.bloxbean.intelliada.idea.common.CardanoIcons;
import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.bloxbean.intelliada.idea.scripts.service.ScriptService;
import com.bloxbean.intelliada.idea.scripts.ui.ScriptPubKeyGenerateDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class CreateScriptPubkeyAction extends AnAction {

    public CreateScriptPubkeyAction() {
        super("Create Script (Single Sig)", "Create Script (Single Sig)", CardanoIcons.SCRIPT_ICON);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if(project == null)
            return;

        ScriptPubKeyGenerateDialog dialog = new ScriptPubKeyGenerateDialog(project);
        boolean ok = dialog.showAndGet();
        if(!ok)
            return;

        ScriptInfo scriptInfo = dialog.getScriptPubkeyEntryForm().generateScriptPubkey();
        ScriptService scriptService = new ScriptService();
        if(ok) {
            scriptService.addScript(scriptInfo);
        }

    }

    @Override
    public boolean isDumbAware() {
        return true;
    }
}
