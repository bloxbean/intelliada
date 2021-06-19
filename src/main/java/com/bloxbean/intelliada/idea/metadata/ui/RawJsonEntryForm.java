package com.bloxbean.intelliada.idea.metadata.ui;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.helper.JsonNoSchemaToMetadataConverter;
import com.bloxbean.intelliada.idea.metadata.exception.InvalidMetadataException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;

import static com.bloxbean.intelliada.idea.metadata.ui.MetadataTemplateHelper.*;

public class RawJsonEntryForm {
    private JPanel mainPanel;
    private EditorTextField editorPane;
    private JScrollPane scrollPane;
    private JComboBox copyTemplateCB;
    private Project project;
    public String[] templateItems = {SIMPLE_KEY_VALUE_TEMPLATE, COMPLEX_KEY_VALUE_TEMPLATE, NFT_TEMPLATE};

    public RawJsonEntryForm(Project project) {
        this.project = project;

        initialize();
    }

    public void initialize() {
        copyTemplateCB.addActionListener(e -> {
            String templateName = (String) copyTemplateCB.getSelectedItem();
            if(StringUtil.isEmpty(templateName))
                return;

            MetadataTemplateHelper metadataTemplateHelper = new MetadataTemplateHelper();
            String content = metadataTemplateHelper.getTemplateJson(templateName);
            editorPane.setText(content);
        });

    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        Language language = Language.findLanguageByID("JSON");
        FileType fileType = language != null ? language.getAssociatedFileType() : null;
        editorPane = new EditorTextField(EditorFactory.getInstance().createDocument(""), project, fileType, false, false);
        scrollPane = new JBScrollPane(editorPane);

        copyTemplateCB = new ComboBox(templateItems);
    }

    public Metadata getMetadata() throws InvalidMetadataException {
        String content = editorPane.getText();
        if(StringUtil.isEmpty(content))
            return null;

        try {
            return JsonNoSchemaToMetadataConverter.jsonToCborMetadata(content);
        } catch (JsonProcessingException e) {
            throw new InvalidMetadataException("Cannot convert json to metadata", e);
        }
    }
}