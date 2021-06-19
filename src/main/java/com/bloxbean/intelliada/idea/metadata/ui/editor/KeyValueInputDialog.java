package com.bloxbean.intelliada.idea.metadata.ui.editor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class KeyValueInputDialog extends DialogWrapper {
    private JPanel mainPanel;
    private KeyValueInputForm keyValueForm;

    public KeyValueInputDialog(@Nullable Project project) {
       this(project, false);
    }

    public KeyValueInputDialog(@Nullable Project project, boolean metadatalabel) {
        super(project, true);
        keyValueForm.initialize(metadatalabel);
        setTitle("Metadata - Key / Value");
        init();
    }

    public void setKey(String key) {
        keyValueForm.setKey(key);
    }

    public void setValue(String value) {
        keyValueForm.setValue(value);
    }

    public void setType(DataType type) {
        keyValueForm.setType(type);
    }

    public void enableListMode() {
        keyValueForm.enableListMode();
    }
    public void enableEditMode() {
        keyValueForm.enableEditMode();
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        return keyValueForm.doValidate();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel;
    }

    public KeyValueInputForm getKeyValueForm() {
        return keyValueForm;
    }
}
