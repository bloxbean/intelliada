package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.core.JoustDiagnosticText;
import com.bloxbean.intelliada.idea.joust.core.JoustDocsUrl;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.BrowserUtil;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.LSPDiagnosticFeature;
import org.eclipse.lsp4j.Diagnostic;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * ADR-004 D3a item 8: diagnostics rendered by IntelliAda. The tooltip escapes every server field; the message is plain
 * text; {@code codeDescription.href} is literal text and is never opened. Open documentation goes to IntelliAda's own
 * catalogue URL, for a {@code JSC} code only.
 */
public class JoustDiagnosticFeature extends LSPDiagnosticFeature {

    @Override
    public String getMessage(Diagnostic diagnostic) {
        return JoustDiagnosticText.message(diagnostic);
    }

    @Override
    public String getTooltip(Diagnostic diagnostic) {
        return JoustDiagnosticText.tooltipHtml(diagnostic, JoustDiagnosticText::describe);
    }

    @Override
    public void createAnnotation(Diagnostic diagnostic, Document document, List<IntentionAction> fixes, AnnotationHolder holder) {
        List<IntentionAction> mine = new ArrayList<>();
        String url = JoustDocsUrl.forDiagnostic(diagnostic);
        if (url != null) mine.add(new OpenDocumentation(diagnostic.getCode().getLeft(), url));
        super.createAnnotation(diagnostic, document, mine, holder);
    }

    /** Opens IntelliAda's catalogue URL for one JSC code. */
    static final class OpenDocumentation implements IntentionAction {
        private final String code;
        private final String url;

        OpenDocumentation(String code, String url) {
            this.code = code;
            this.url = url;
        }

        String url() {
            return url;
        }

        @Override
        public @NotNull String getText() {
            return "Open JOUST documentation for " + code;
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Open JOUST documentation";
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
            return true;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
            BrowserUtil.browse(url);
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }
    }
}
