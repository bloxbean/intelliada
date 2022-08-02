package com.bloxbean.intelliada.idea.common.ui;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import org.jetbrains.annotations.NotNull;

public class JsonEditorTextField extends LanguageTextField {
    public JsonEditorTextField(Project project) {
        super(Language.findLanguageByID("JSON"), project, "", false);
    }

    @Override
    protected @NotNull EditorEx createEditor() {
        EditorEx editor = super.createEditor();
        editor.setVerticalScrollbarVisible(true);
        editor.setHorizontalScrollbarVisible(true);

        EditorSettings settings = editor.getSettings();
        settings.setLineNumbersShown(true);
        settings.setAutoCodeFoldingEnabled(true);
        settings.setFoldingOutlineShown(true);
        settings.setAllowSingleLogicalLineFolding(true);
        settings.setRightMarginShown(true);

        return editor;
    }
}
