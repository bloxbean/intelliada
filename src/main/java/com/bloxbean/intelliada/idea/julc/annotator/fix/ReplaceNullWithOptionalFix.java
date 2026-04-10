package com.bloxbean.intelliada.idea.julc.annotator.fix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Quick fix that replaces 'null' with 'Optional.empty()' in julc validators.
 * Triggered from JulcExternalAnnotator when null literal is detected.
 */
public class ReplaceNullWithOptionalFix implements IntentionAction {

    private final TextRange range;

    public ReplaceNullWithOptionalFix(@NotNull TextRange range) {
        this.range = range;
    }

    @Override
    public @NotNull String getText() {
        return "Replace 'null' with 'Optional.empty()'";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "julc";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (editor == null || file == null) return false;
        Document doc = editor.getDocument();
        if (range.getEndOffset() > doc.getTextLength()) return false;

        String text = doc.getText(range).trim();
        return text.contains("null");
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        Document doc = editor.getDocument();
        String lineText = doc.getText(range);

        // Find the exact 'null' within the range and replace
        int nullOffset = lineText.indexOf("null");
        if (nullOffset >= 0) {
            int start = range.getStartOffset() + nullOffset;
            int end = start + 4; // "null".length()
            doc.replaceString(start, end, "Optional.empty()");
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        return IntentionPreviewInfo.EMPTY;
    }
}
