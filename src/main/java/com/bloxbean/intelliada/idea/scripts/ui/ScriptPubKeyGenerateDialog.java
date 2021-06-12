package com.bloxbean.intelliada.idea.scripts.ui;

import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.bloxbean.intelliada.idea.util.JsonUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ScriptPubKeyGenerateDialog extends BaseScriptGenerateDialog {
    private JPanel mainPanel;
    private ScriptPubkeyEntryForm scriptPubkeyEntryForm;
    private JEditorPane editorPane;

    public ScriptPubKeyGenerateDialog(@Nullable Project project) {
        super(project, true);
        setTitle("ScriptPubkey - Create");

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

    protected ScriptInfo generateScriptPubkey() {
        ScriptInfo scriptInfo = scriptPubkeyEntryForm.generateScriptPubkey();
        String json = JsonUtil.getPrettyJson(scriptInfo.getScript());

        if(scriptInfo.getVKey() != null)
            System.out.println(scriptInfo.getVKey().getCborHex());

        editorPane.setText(json);

        return scriptInfo;
    }

    public ScriptPubkeyEntryForm getScriptPubkeyEntryForm() {
        return scriptPubkeyEntryForm;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel;
    }
}
