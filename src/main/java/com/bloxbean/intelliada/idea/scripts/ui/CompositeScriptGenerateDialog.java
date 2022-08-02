package com.bloxbean.intelliada.idea.scripts.ui;

import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CompositeScriptGenerateDialog extends BaseScriptGenerateDialog {
    private JPanel mainPanel;
    private JEditorPane editorPane;
    private CompositeScriptEntryForm compositeScriptEntryForm;

    public CompositeScriptGenerateDialog(@Nullable Project project) {
        super(project, true);
        setTitle("Multi-sig Script (Script Sigs & Time Lock) - Create");
        initialization(project);
        init();
    }

    private void initialization(Project project) {
        compositeScriptEntryForm.initializeData(project, console);
    }

    @Override
    protected @Nullable
    ValidationInfo doCustomValidation() {
        ValidationInfo validationInfo = compositeScriptEntryForm.doValidation();
        return validationInfo;
    }

    @Override
    public ScriptInfo generateScriptPubkey() {
        ScriptInfo scriptInfo = compositeScriptEntryForm.generateCompositeScript();
        if (scriptInfo != null) {
            editorPane.setText(scriptInfo.printFormatted());
        } else {
            editorPane.setText("");
        }

        return scriptInfo;
    }

    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        return mainPanel;
    }
}
