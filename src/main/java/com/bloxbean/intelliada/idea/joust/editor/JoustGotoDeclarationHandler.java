package com.bloxbean.intelliada.idea.joust.editor;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * ADR-004 D3a item 7: Go to Definition for .jsc, by IntelliAda. LSP4IJ's definition feature is disabled because it has
 * no result hook; here every server location is confined to real paths inside the project before it reaches the editor.
 */
public final class JoustGotoDeclarationHandler implements GotoDeclarationHandler {

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement source, int offset, Editor editor) {
        if (source == null || editor == null) return null;
        PsiFile file = source.getContainingFile();
        VirtualFile vf = file == null ? null : file.getVirtualFile();
        if (!JoustServerAccess.isJsc(vf)) return null;
        Project project = source.getProject();
        var server = JoustServerAccess.server(project);
        if (server == null) return null;
        var params = new DefinitionParams(new TextDocumentIdentifier(JoustServerAccess.uriOf(vf)),
                LSPIJUtils.toPosition(offset, editor.getDocument()));
        var answer = JoustServerAccess.await(server.getTextDocumentService().definition(params));
        var targets = JoustServerAccess.confine(project, JoustServerAccess.locations(answer));
        List<PsiElement> out = new ArrayList<>();
        for (JoustServerAccess.Target t : targets) {
            PsiFile pf = PsiManager.getInstance(project).findFile(t.file());
            Document doc = FileDocumentManager.getInstance().getDocument(t.file());
            if (pf == null || doc == null) continue;
            int off = t.range() == null ? 0 : LSPIJUtils.toOffset(t.range().getStart(), doc);
            int line = doc.getLineNumber(Math.min(off, doc.getTextLength()));
            String text = PlainText.clean(doc.getText(new com.intellij.openapi.util.TextRange(doc.getLineStartOffset(line),
                    doc.getLineEndOffset(line))).trim(), 120);
            out.add(new JoustNavigationElement(pf, off, text));
        }
        return out.isEmpty() ? null : out.toArray(PsiElement.EMPTY_ARRAY);
    }
}
