package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.core.ReadOnlyFeatureSet;
import com.bloxbean.intelliada.idea.joust.core.ReadOnlyFeatureSet.Feature;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.*;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;

import java.util.List;

/**
 * ADR-004 D3a item 1: LSP4IJ features by allow-list. One object is installed per {@link Feature}, chosen by its
 * {@link ReadOnlyFeatureSet.Treatment}, so the installed features cannot drift from R.
 *
 * <ul>
 *   <li><b>LSP4IJ's own, in R:</b> diagnostics (rendered by {@link JoustDiagnosticFeature}), document highlight, document
 *   symbols and progress (their server text cleaned and capped, I15), folding, selection range, semantic tokens. None of
 *   them opens a server-supplied link or a location outside the current file.</li>
 *   <li><b>In R, provided by IntelliAda</b> (LSP4IJ's disabled, because it has no result hook): hover, definition,
 *   references. joust-lsp advertises no declaration, type definition, implementation or workspace symbols.</li>
 *   <li><b>Disabled:</b> everything else, including every editing feature.</li>
 * </ul>
 * {@link #initializeParams} confines the workspace folders (I25) and declares no {@code workspace/applyEdit}.
 */
public class JoustClientFeatures extends LSPClientFeatures {

    static final int SYMBOL_TEXT_CAP = 200;
    static final int PROGRESS_TEXT_CAP = 200;

    public JoustClientFeatures() {
        for (Feature f : Feature.values()) install(f);
    }

    private void install(Feature f) {
        boolean off = f.treatment != ReadOnlyFeatureSet.Treatment.LSP4IJ;
        switch (f) {
            case DIAGNOSTIC -> setDiagnosticFeature(new JoustDiagnosticFeature());
            case DOCUMENT_HIGHLIGHT -> setDocumentHighlightFeature(new LSPDocumentHighlightFeature() {
                @Override public boolean isEnabled(PsiFile file) { return !off && super.isEnabled(file); }
            });
            case DOCUMENT_SYMBOL -> setDocumentSymbolFeature(new SafeDocumentSymbolFeature(off));
            case FOLDING_RANGE -> setFoldingRangeFeature(new LSPFoldingRangeFeature() {
                @Override public boolean isEnabled(PsiFile file) { return !off && super.isEnabled(file); }
            });
            case SELECTION_RANGE -> setSelectionRangeFeature(new LSPSelectionRangeFeature() {
                @Override public boolean isEnabled(PsiFile file) { return !off && super.isEnabled(file); }
            });
            case SEMANTIC_TOKENS -> setSemanticTokensFeature(new LSPSemanticTokensFeature() {
                @Override public boolean isEnabled(PsiFile file) { return !off && super.isEnabled(file); }
            });
            case PROGRESS -> setProgressFeature(new SafeProgressFeature());
            case HOVER -> setHoverFeature(new LSPHoverFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case DEFINITION -> setDefinitionFeature(new LSPDefinitionFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case DECLARATION -> setDeclarationFeature(new LSPDeclarationFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case TYPE_DEFINITION -> setTypeDefinitionFeature(new LSPTypeDefinitionFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case IMPLEMENTATION -> setImplementationFeature(new LSPImplementationFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case REFERENCES -> setReferencesFeature(new LSPReferencesFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case USAGE -> setUsageFeature(new LSPUsageFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case WORKSPACE_SYMBOL -> setWorkspaceSymbolFeature(new LSPWorkspaceSymbolFeature() {
                @Override public boolean isEnabled() { return false; }
                @Override public boolean isSupported() { return false; }
            });
            case COMPLETION -> setCompletionFeature(new LSPCompletionFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case CODE_ACTION -> setCodeActionFeature(new LSPCodeActionFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case CODE_LENS -> setCodeLensFeature(new LSPCodeLensFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case DOCUMENT_LINK -> setDocumentLinkFeature(new LSPDocumentLinkFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case DOCUMENT_COLOR -> setDocumentColorFeature(new LSPDocumentColorFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case FORMATTING -> setFormattingFeature(new LSPFormattingFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case ON_TYPE_FORMATTING -> setOnTypeFormattingFeature(new LSPOnTypeFormattingFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case RENAME -> setRenameFeature(new LSPRenameFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case INLAY_HINT -> setInlayHintFeature(new LSPInlayHintFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case SIGNATURE_HELP -> setSignatureHelpFeature(new LSPSignatureHelpFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case CALL_HIERARCHY -> setCallHierarchyFeature(new LSPCallHierarchyFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case TYPE_HIERARCHY -> setTypeHierarchyFeature(new LSPTypeHierarchyFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case BREADCRUMBS -> setBreadcrumbsFeature(new LSPBreadcrumbsFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case CONFIGURATION -> {
                LSPConfigurationFeature c = new LSPConfigurationFeature();
                c.setOnConfigurationChanged(LSPConfigurationFeature.OnConfigurationChanged.IGNORE);
                setConfigurationFeature(c);
            }
            case EDITOR_BEHAVIOR -> setEditorBehaviorFeature(new OffEditorBehaviorFeature(this));
            case INLINE_COMPLETION -> setInlineCompletionFeature(new LSPInlineCompletionFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case INLINE_VALUE -> setInlineValueFeature(new LSPInlineValueFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case LINKED_EDITING_RANGE -> setLinkedEditingRangeFeature(new LSPLinkedEditingRangeFeature() {
                @Override public boolean isEnabled(PsiFile file) { return false; }
                @Override public boolean isSupported(PsiFile file) { return false; }
            });
            case WORKSPACE_FOLDER -> setWorkspaceFolderFeature(new ConfinedWorkspaceFolderFeature());
        }
    }

    /** LSP4IJ's typing aids, every one off (they are off by default in 0.30.1; this keeps them off). */
    static final class OffEditorBehaviorFeature extends EditorBehaviorFeature {
        OffEditorBehaviorFeature(LSPClientFeatures features) {
            super(features);
        }

        @Override public boolean isEnableStringLiteralImprovements(PsiFile file) { return false; }
        @Override public boolean isEnableStatementTerminatorImprovements(PsiFile file) { return false; }
        @Override public boolean isEnableEnterBetweenBracesFix(PsiFile file) { return false; }
        @Override public boolean isEnableTextMateNestedBracesImprovements(PsiFile file) { return false; }
        @Override public boolean isEnableSemanticTokensFileViewProvider(PsiFile file) { return false; }
    }

    /**
     * I25 for LSP4IJ 0.30.1, which adds a workspace folder (and sends {@code workspace/didChangeWorkspaceFolders}) when a
     * file outside the initial folders opens. LSP4IJ sends that notification only when this feature names a folder and
     * then agrees to mark it notified, so it names none and agrees to nothing: the server only ever has the confined
     * folders of {@link #initializeParams}.
     */
    static final class ConfinedWorkspaceFolderFeature extends LSPWorkspaceFolderFeature {
        @Override
        public List<WorkspaceFolder> getInitialWorkspaceFolders(Project project) {
            return JoustProjectFolders.confined(project);
        }

        @Override
        public WorkspaceFolder computeWorkspaceFolderToNotify(com.intellij.openapi.vfs.VirtualFile file) {
            return null;
        }

        @Override
        public boolean markFolderAsNotified(WorkspaceFolder folder) {
            return false;
        }
    }

    /** Document symbols: LSP4IJ's Structure view, with the server's names and details cleaned and capped (I15). */
    static final class SafeDocumentSymbolFeature extends LSPDocumentSymbolFeature {
        private final boolean off;

        SafeDocumentSymbolFeature(boolean off) {
            this.off = off;
        }

        @Override
        public boolean isEnabled(PsiFile file) {
            return !off && super.isEnabled(file);
        }

        @Override
        public String getPresentableText(DocumentSymbol symbol, PsiFile file) {
            return PlainText.literal(super.getPresentableText(symbol, file), SYMBOL_TEXT_CAP).replace('\n', ' ');
        }

        @Override
        public String getLocationString(DocumentSymbol symbol, PsiFile file) {
            return PlainText.literal(super.getLocationString(symbol, file), SYMBOL_TEXT_CAP).replace('\n', ' ');
        }
    }

    /** Progress: the server's titles and messages cleaned and capped before they reach a progress indicator (I15). */
    static final class SafeProgressFeature extends LSPProgressFeature {
        @Override
        public String getProgressTaskTitle(String title) {
            // The status bar puts the title in a tooltip, which IntelliJ renders as HTML without escaping plain text
            // (M1-SEC-8): angle brackets become their single-guillemet look-alikes, so List<T> reads List‹T› and no
            // tag can form. Every other character is kept (r9 E6, M1-CDX-13).
            return PlainText.literal(super.getProgressTaskTitle(PlainText.clean(title, PROGRESS_TEXT_CAP)), PROGRESS_TEXT_CAP)
                    .replace('\n', ' ').replace('<', '\u2039').replace('>', '\u203A');
        }

        @Override
        protected void updateMessage(String message, ProgressIndicator indicator) {
            super.updateMessage(PlainText.literal(message, PROGRESS_TEXT_CAP).replace('\n', ' '), indicator);
        }
    }

    /**
     * The server's capabilities with every editing provider removed before LSP4IJ sees them (M1-DES-1, I5): completion,
     * code actions, code lens, execute command, formatting, rename, document links, inlay hints, signature help and
     * <b>workspace file operations</b> (LSP4IJ applies a {@code willRenameFiles} answer directly, bypassing
     * {@code applyEdit}). joust-lsp 0.1.0-pre2 advertises none of them to IntelliAda; this holds whatever a server says.
     */
    @Override
    public void setServerCapabilities(ServerCapabilities caps) {
        super.setServerCapabilities(readOnly(caps));
    }

    public static ServerCapabilities readOnly(ServerCapabilities caps) {
        if (caps == null) return null;
        caps.setCompletionProvider(null);
        caps.setCodeActionProvider((Either<Boolean, org.eclipse.lsp4j.CodeActionOptions>) null);
        caps.setCodeLensProvider(null);
        caps.setExecuteCommandProvider(null);
        caps.setDocumentFormattingProvider((Either<Boolean, org.eclipse.lsp4j.DocumentFormattingOptions>) null);
        caps.setDocumentRangeFormattingProvider((Either<Boolean, org.eclipse.lsp4j.DocumentRangeFormattingOptions>) null);
        caps.setDocumentOnTypeFormattingProvider(null);
        caps.setRenameProvider((Either<Boolean, org.eclipse.lsp4j.RenameOptions>) null);
        caps.setDocumentLinkProvider(null);
        caps.setInlayHintProvider((Either<Boolean, org.eclipse.lsp4j.InlayHintRegistrationOptions>) null);
        caps.setSignatureHelpProvider(null);
        caps.setLinkedEditingRangeProvider((Either<Boolean, org.eclipse.lsp4j.LinkedEditingRangeRegistrationOptions>) null);
        if (caps.getWorkspace() != null) {
            caps.getWorkspace().setFileOperations(null);
            caps.getWorkspace().setWorkspaceFolders(null);   // I25: no folder-change channel, whatever LSP4IJ does with it
        }
        return caps;
    }

    /** For tests: every URI LSP4IJ asks this server's features to resolve (M1-CDX-24). */
    private static volatile java.util.function.Consumer<String> uriLookups;

    public static void setUriLookupRecorderForTests(java.util.function.Consumer<String> recorder) {
        uriLookups = recorder;
    }

    @Override
    public com.intellij.openapi.vfs.VirtualFile findFileByUri(String uri) {
        var r = uriLookups;
        if (r != null) r.accept(uri);
        return super.findFileByUri(uri);
    }

    /** The cached {@code joust/status} belongs to one server's lifetime, however it was stopped (M1-CDX-15). */
    @Override
    public void handleServerStatusChanged(com.redhat.devtools.lsp4ij.ServerStatus status) {
        super.handleServerStatusChanged(status);
        if (status == com.redhat.devtools.lsp4ij.ServerStatus.stopping || status == com.redhat.devtools.lsp4ij.ServerStatus.stopped) {
            Project p = getProject();
            if (p != null && !p.isDisposed()) JoustStatusCache.getInstance(p).clear();
        }
    }

    @Override
    public void initializeParams(InitializeParams params) {
        super.initializeParams(params);
        Project project = getProject();
        confine(params, JoustProjectFolders.confined(project));
    }

    /** Applies I25 and declares no applyEdit; public for tests. */
    public static void confine(InitializeParams params, List<WorkspaceFolder> folders) {
        params.setWorkspaceFolders(folders);
        params.setRootUri(folders.isEmpty() ? null : folders.get(0).getUri());
        params.setRootPath(null);
        ClientCapabilities caps = params.getCapabilities();
        if (caps != null) {
            WorkspaceClientCapabilities ws = caps.getWorkspace();
            if (ws != null) {
                ws.setApplyEdit(false);
                ws.setFileOperations(null);
                if (ws.getWorkspaceEdit() != null) ws.getWorkspaceEdit().setFailureHandling(null);
            }
        }
    }
}
