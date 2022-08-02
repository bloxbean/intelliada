package com.bloxbean.intelliada.idea.scripts.ui;

import com.bloxbean.intelliada.idea.scripts.util.ScriptExportUtil;
import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

public abstract class BaseScriptGenerateDialog extends DialogWrapper {
    private Action generateScriptAction;
    private Action exportAction;
    protected CardanoConsole console;
    protected Project project;

    protected BaseScriptGenerateDialog(@Nullable Project project, boolean canbeParent) {
        super(project, canbeParent);
        this.console = CardanoConsole.getConsole(project);
        this.project = project;
        this.generateScriptAction = new ScriptPubKeyGenerateDialog.GenerateScriptSourceAction();
        this.exportAction = new ScriptPubKeyGenerateDialog.ExportScriptSourceAction();
        setOKButtonText("Save");
    }

    @Override
    protected Action @NotNull [] createLeftSideActions() {
        return new Action[]{
                generateScriptAction,
                exportAction
        };
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        ValidationInfo validationInfo = doCustomValidation();
        if (validationInfo == null) {
            generateScriptAction.setEnabled(true);
            exportAction.setEnabled(true);
            return null;
        } else {
            generateScriptAction.setEnabled(false);
            exportAction.setEnabled(false);

            return validationInfo;
        }
    }

    protected abstract ValidationInfo doCustomValidation();

    public void exportScriptInfo() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                ScriptInfo scriptInfo = generateScriptPubkey();
                try {
                    ScriptExportUtil.exportScriptInfo(scriptInfo, project, createCenterPanel());
                } catch (Exception exception) {
                    console.showErrorMessage("Export script error", exception);
                }
            }
        });

    }

    protected abstract ScriptInfo generateScriptPubkey();

    class GenerateScriptSourceAction extends DialogWrapperAction {

        public GenerateScriptSourceAction() {
            super("Generate Json");
        }

        @Override
        protected void doAction(ActionEvent e) {
            List<ValidationInfo> infoList = doValidateAll();
            if (!infoList.isEmpty()) {
                ValidationInfo info = infoList.get(0);

                startTrackingValidation();
                if (infoList.stream().anyMatch(info1 -> !info1.okEnabled)) return;
            }

            generateScriptPubkey();
        }
    }

    class ExportScriptSourceAction extends DialogWrapperAction {

        public ExportScriptSourceAction() {
            super("Export");
        }

        @Override
        protected void doAction(ActionEvent e) {
            List<ValidationInfo> infoList = doValidateAll();
            if (!infoList.isEmpty()) {
                ValidationInfo info = infoList.get(0);

                startTrackingValidation();
                if (infoList.stream().anyMatch(info1 -> !info1.okEnabled)) return;
            }
            exportScriptInfo();
        }
    }
}
