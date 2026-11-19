package com.bloxbean.intelliada.idea.joust.editor;

import com.intellij.model.Pointer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ADR-004 D3a item 6: hover for .jsc files, requested by IntelliAda and rendered as escaped literal text. LSP4IJ's hover
 * is disabled for the JOUST server, so neither its markdown-to-HTML renderer nor its documentation link handler ever
 * sees JOUST hover text.
 */
public final class JoustDocumentationTargetProvider implements DocumentationTargetProvider {

    @Override
    public @NotNull List<? extends DocumentationTarget> documentationTargets(@NotNull PsiFile file, int offset) {
        VirtualFile vf = file.getVirtualFile();
        if (!JoustServerAccess.isJsc(vf)) return List.of();
        Document doc = FileDocumentManager.getInstance().getDocument(vf);
        if (doc == null || offset < 0 || offset > doc.getTextLength()) return List.of();
        return List.of(new HoverTarget(file.getProject(), vf, LSPIJUtils.toPosition(offset, doc), file.getName()));
    }

    static final class HoverTarget implements DocumentationTarget {
        private final Project project;
        private final VirtualFile file;
        private final Position position;
        private final String name;

        HoverTarget(Project project, VirtualFile file, Position position, String name) {
            this.project = project;
            this.file = file;
            this.position = position;
            this.name = name;
        }

        @Override
        public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
            return Pointer.hardPointer(this);
        }

        @Override
        public @NotNull TargetPresentation computePresentation() {
            return TargetPresentation.builder(name).presentation();
        }

        @Override
        public @Nullable String computeDocumentationHint() {
            return null;
        }

        @Override
        public @Nullable DocumentationResult computeDocumentation() {
            return DocumentationResult.asyncDocumentation(() -> {
                if (project.isDisposed()) return null;
                var server = JoustServerAccess.server(project);
                if (server == null) return null;
                String uri = ReadAction.compute(() -> JoustServerAccess.uriOf(file));
                var hover = JoustServerAccess.await(server.getTextDocumentService()
                        .hover(new HoverParams(new TextDocumentIdentifier(uri), position)));
                String text = JoustHoverText.plain(hover);
                return text == null ? null : DocumentationResult.documentation(JoustHoverText.html(text));
            });
        }
    }
}
