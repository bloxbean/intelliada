package com.bloxbean.intelliada.idea.scripts.ui;

import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ScriptPubKeyGenerateDialog extends BaseScriptGenerateDialog {
    private JPanel mainPanel;
    private ScriptPubkeyEntryForm scriptPubkeyEntryForm;
    private JEditorPane editorPane;

    public ScriptPubKeyGenerateDialog(@Nullable Project project) {
        super(project, true);
        setTitle("Script(sig) - Create");

        initialize(project);
        init();
    }

    protected void initialize(Project project) {
        scriptPubkeyEntryForm.initializeData(project, console);
        scriptPubkeyEntryForm.addScriptGenListener(new ScriptPubkeyEntryForm.ScriptGenListener() {
            @Override
            public void generateFromTypeChanged() {
                editorPane.setText("");
            }
        });

    }

    @Override
    protected ValidationInfo doCustomValidation() {
        ValidationInfo validationInfo = scriptPubkeyEntryForm.doValidate();
        return validationInfo;
    }

    protected ScriptInfo generateScriptPubkey() {
        try {
            ScriptInfo scriptInfo = scriptPubkeyEntryForm.generateScriptPubkey();
            editorPane.setText(scriptInfo.printFormatted());
            return scriptInfo;
        } catch (Exception e) {
            console.showErrorMessage("Error generating script", e);
            editorPane.setText("");
        }
        return null;
    }

    public ScriptPubkeyEntryForm getScriptPubkeyEntryForm() {
        return scriptPubkeyEntryForm;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel;
    }
}
