package com.bloxbean.intelliada.idea.joust.lsp;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.*;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;

import java.util.List;

/**
 * ADR-004 D3a item 1: LSP4IJ features by allow-list ({@link com.bloxbean.intelliada.idea.joust.core.ReadOnlyFeatureSet}).
 *
 * <ul>
 *   <li><b>LSP4IJ's own, in R:</b> diagnostics (rendered by {@link JoustDiagnosticFeature}), document highlight, document
 *   symbols, folding, selection range, semantic tokens, progress. None of them opens a server-supplied link or a
 *   location outside the current file.</li>
 *   <li><b>In R, provided by IntelliAda</b> (LSP4IJ's disabled, because it has no result hook): hover, definition,
 *   references. joust-lsp advertises no declaration, type definition, implementation or workspace symbols, so those are
 *   simply off.</li>
 *   <li><b>Disabled:</b> everything else, including every editing feature.</li>
 * </ul>
 * {@link #initializeParams} confines the workspace folders (I25) and declares no {@code workspace/applyEdit}.
 */
public class JoustClientFeatures extends LSPClientFeatures {

    public JoustClientFeatures() {
        setDiagnosticFeature(new JoustDiagnosticFeature());

        setHoverFeature(new LSPHoverFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setDefinitionFeature(new LSPDefinitionFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setDeclarationFeature(new LSPDeclarationFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setTypeDefinitionFeature(new LSPTypeDefinitionFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setImplementationFeature(new LSPImplementationFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setReferencesFeature(new LSPReferencesFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setUsageFeature(new LSPUsageFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setWorkspaceSymbolFeature(new LSPWorkspaceSymbolFeature() {
            @Override public boolean isEnabled() { return false; }
            @Override public boolean isSupported() { return false; }
        });

        setCompletionFeature(new LSPCompletionFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setCodeActionFeature(new LSPCodeActionFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setCodeLensFeature(new LSPCodeLensFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setDocumentLinkFeature(new LSPDocumentLinkFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setDocumentColorFeature(new LSPDocumentColorFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setFormattingFeature(new LSPFormattingFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setOnTypeFormattingFeature(new LSPOnTypeFormattingFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setRenameFeature(new LSPRenameFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setInlayHintFeature(new LSPInlayHintFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setSignatureHelpFeature(new LSPSignatureHelpFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setCallHierarchyFeature(new LSPCallHierarchyFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
        setTypeHierarchyFeature(new LSPTypeHierarchyFeature() {
            @Override public boolean isEnabled(PsiFile file) { return false; }
            @Override public boolean isSupported(PsiFile file) { return false; }
        });
    }

    @Override
    public void initializeParams(InitializeParams params) {
        super.initializeParams(params);
        Project project = getProject();
        List<WorkspaceFolder> folders = JoustProjectFolders.confined(project);
        confine(params, folders);
    }

    /** Applies I25 and declares no applyEdit; pure, for tests. */
    public static void confine(InitializeParams params, List<WorkspaceFolder> folders) {
        params.setWorkspaceFolders(folders);
        String root = folders.isEmpty() ? null : folders.get(0).getUri();
        params.setRootUri(root);
        params.setRootPath(null);
        ClientCapabilities caps = params.getCapabilities();
        if (caps != null) {
            WorkspaceClientCapabilities ws = caps.getWorkspace();
            if (ws != null) {
                ws.setApplyEdit(false);
                if (ws.getWorkspaceEdit() != null) ws.getWorkspaceEdit().setFailureHandling(null);
            }
        }
    }
}
