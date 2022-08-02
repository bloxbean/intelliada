package com.bloxbean.intelliada.idea.scripts.action;

import com.bloxbean.intelliada.idea.common.CardanoIcons;
import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.bloxbean.intelliada.idea.scripts.service.ScriptService;
import com.bloxbean.intelliada.idea.scripts.ui.CompositeScriptGenerateDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class CreateCompositeScriptAction extends AnAction {

    public CreateCompositeScriptAction() {
        super("Create script (Sigs & TimeLock)", "Create script (Sigs & TimeLock)", CardanoIcons.MULTI_SCRIPT_ICON);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null)
            return;

        CompositeScriptGenerateDialog dialog = new CompositeScriptGenerateDialog(project);
        boolean ok = dialog.showAndGet();
        if (!ok)
            return;

        ScriptInfo scriptInfo = dialog.generateScriptPubkey();
        ScriptService scriptService = new ScriptService();
        scriptService.addScript(scriptInfo);
    }

    @Override
    public boolean isDumbAware() {
        return true;
    }
}
