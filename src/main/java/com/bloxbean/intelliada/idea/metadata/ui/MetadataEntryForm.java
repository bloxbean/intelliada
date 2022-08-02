package com.bloxbean.intelliada.idea.metadata.ui;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.intelliada.idea.metadata.exception.InvalidMetadataException;
import com.bloxbean.intelliada.idea.metadata.ui.editor.TreeMetadataEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class MetadataEntryForm {
    private JPanel mainPanel;
    private JComboBox editorType;
    private JPanel contentPanel;
    private RawJsonEntryForm rawJsonEditor;
    private TreeMetadataEditor treeMetadataEditor;

    public MetadataEntryForm() {
        editorType.addItem(EditorType.RawJson);
        editorType.addItem(EditorType.MetadataEditor);
    }

    public void initialize(Project project) {
        rawJsonEditor = new RawJsonEntryForm(project);
        treeMetadataEditor = new TreeMetadataEditor(project);
        contentPanel.setLayout(new CardLayout());
        contentPanel.add(rawJsonEditor.getMainPanel(), EditorType.RawJson.toString());
        contentPanel.add(treeMetadataEditor.getMainPanel(), EditorType.MetadataEditor.toString());

        editorType.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                CardLayout cl = (CardLayout) (contentPanel.getLayout());
                cl.show(contentPanel, ((EditorType) e.getItem()).toString());
            }
        });
    }

    public Metadata getMetadata() throws InvalidMetadataException {
        if (editorType.getSelectedItem() == EditorType.RawJson)
            return rawJsonEditor.getMetadata();
        else if (editorType.getSelectedItem() == EditorType.MetadataEditor) {
            return treeMetadataEditor.getMetadata();
        } else
            return null;
    }

    public ValidationInfo doValidate() {
        return null;
    }
}
