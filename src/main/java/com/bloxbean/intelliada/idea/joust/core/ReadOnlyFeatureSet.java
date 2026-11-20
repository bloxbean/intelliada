package com.bloxbean.intelliada.idea.joust.core;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ADR-004 D3a items 1 and 2: the <b>read-only set R</b>, defined once. Each LSP4IJ client feature has a treatment and
 * the LSP methods it covers; the feature allow-list ({@code JoustClientFeatures} installs one object per entry, by
 * treatment) and the {@code client/registerCapability} filter ({@link #allowsRegistration}) both derive from it.
 */
public final class ReadOnlyFeatureSet {

    private ReadOnlyFeatureSet() {
    }

    /** How IntelliAda treats a feature. */
    public enum Treatment {
        /** In R, and LSP4IJ's own feature is used (no server-supplied link or location leaves the file). */
        LSP4IJ,
        /** In R, but LSP4IJ's feature is disabled and IntelliAda provides it (hover, navigation; D3a items 6 and 7). */
        INTELLIADA,
        /** Not in R: disabled. */
        DISABLED;

        public boolean inR() {
            return this != DISABLED;
        }
    }

    /**
     * Every {@code LSPClientFeatures.set…Feature} of LSP4IJ 0.30.1, with its treatment and LSP methods. A guard test fails
     * on any setter not listed here, so a feature a future LSP4IJ adds is classified before it can be enabled; at run time
     * {@code JoustLsp4jCompat} keeps the server off when the installed LSP4IJ's setters differ from this list.
     */
    public enum Feature {
        DIAGNOSTIC("setDiagnosticFeature", Treatment.LSP4IJ, "textDocument/diagnostic"),
        DOCUMENT_HIGHLIGHT("setDocumentHighlightFeature", Treatment.LSP4IJ, "textDocument/documentHighlight"),
        DOCUMENT_SYMBOL("setDocumentSymbolFeature", Treatment.LSP4IJ, "textDocument/documentSymbol"),
        FOLDING_RANGE("setFoldingRangeFeature", Treatment.LSP4IJ, "textDocument/foldingRange"),
        SELECTION_RANGE("setSelectionRangeFeature", Treatment.LSP4IJ, "textDocument/selectionRange"),
        SEMANTIC_TOKENS("setSemanticTokensFeature", Treatment.LSP4IJ, "textDocument/semanticTokens"),
        PROGRESS("setProgressFeature", Treatment.LSP4IJ),
        HOVER("setHoverFeature", Treatment.INTELLIADA, "textDocument/hover"),
        DEFINITION("setDefinitionFeature", Treatment.INTELLIADA, "textDocument/definition"),
        DECLARATION("setDeclarationFeature", Treatment.INTELLIADA, "textDocument/declaration"),
        TYPE_DEFINITION("setTypeDefinitionFeature", Treatment.INTELLIADA, "textDocument/typeDefinition"),
        IMPLEMENTATION("setImplementationFeature", Treatment.INTELLIADA, "textDocument/implementation"),
        REFERENCES("setReferencesFeature", Treatment.INTELLIADA, "textDocument/references"),
        USAGE("setUsageFeature", Treatment.INTELLIADA),
        WORKSPACE_SYMBOL("setWorkspaceSymbolFeature", Treatment.INTELLIADA, "workspace/symbol"),
        COMPLETION("setCompletionFeature", Treatment.DISABLED, "textDocument/completion"),
        CODE_ACTION("setCodeActionFeature", Treatment.DISABLED, "textDocument/codeAction"),
        CODE_LENS("setCodeLensFeature", Treatment.DISABLED, "textDocument/codeLens"),
        DOCUMENT_LINK("setDocumentLinkFeature", Treatment.DISABLED, "textDocument/documentLink"),
        DOCUMENT_COLOR("setDocumentColorFeature", Treatment.DISABLED, "textDocument/documentColor"),
        FORMATTING("setFormattingFeature", Treatment.DISABLED, "textDocument/formatting", "textDocument/rangeFormatting"),
        ON_TYPE_FORMATTING("setOnTypeFormattingFeature", Treatment.DISABLED, "textDocument/onTypeFormatting"),
        RENAME("setRenameFeature", Treatment.DISABLED, "textDocument/rename", "textDocument/prepareRename"),
        INLAY_HINT("setInlayHintFeature", Treatment.DISABLED, "textDocument/inlayHint"),
        SIGNATURE_HELP("setSignatureHelpFeature", Treatment.DISABLED, "textDocument/signatureHelp"),
        CALL_HIERARCHY("setCallHierarchyFeature", Treatment.DISABLED, "textDocument/prepareCallHierarchy"),
        TYPE_HIERARCHY("setTypeHierarchyFeature", Treatment.DISABLED, "textDocument/prepareTypeHierarchy"),
        // New since LSP4IJ 0.10.0 (ADR-004 §12.2 E21), all off:
        /** Breadcrumbs from document symbols: a new surface for server text, not needed read-only. */
        BREADCRUMBS("setBreadcrumbsFeature", Treatment.DISABLED),
        /** Settings pushed to the server: IntelliAda sends none, and a change is ignored rather than sent or restarted on. */
        CONFIGURATION("setConfigurationFeature", Treatment.DISABLED, "workspace/didChangeConfiguration"),
        /** LSP4IJ's client-side typing aids (quotes, terminators, braces): all off, as LSP4IJ's own defaults are. */
        EDITOR_BEHAVIOR("setEditorBehaviorFeature", Treatment.DISABLED),
        /** Inline (ghost-text) completion: an edit. */
        INLINE_COMPLETION("setInlineCompletionFeature", Treatment.DISABLED, "textDocument/inlineCompletion"),
        /** Debugger inline values: no JOUST debugger. */
        INLINE_VALUE("setInlineValueFeature", Treatment.DISABLED, "textDocument/inlineValue"),
        /** Linked editing (rename-as-you-type): an edit. */
        LINKED_EDITING_RANGE("setLinkedEditingRangeFeature", Treatment.DISABLED, "textDocument/linkedEditingRange"),
        /** Workspace folders added as files open: off, so the server only ever has I25's confined folders. */
        WORKSPACE_FOLDER("setWorkspaceFolderFeature", Treatment.DISABLED, "workspace/didChangeWorkspaceFolders");

        public final String setter;
        public final Treatment treatment;
        public final List<String> methods;

        Feature(String setter, Treatment treatment, String... methods) {
            this.setter = setter;
            this.treatment = treatment;
            this.methods = List.of(methods);
        }
    }

    /** Setter name to treatment, for the guard test. */
    public static final Map<String, Treatment> FEATURES = Arrays.stream(Feature.values())
            .collect(Collectors.toUnmodifiableMap(f -> f.setter, f -> f.treatment));

    /** The LSP methods of the features in R: the only dynamic registrations IntelliAda accepts. */
    public static final Set<String> REGISTRATION_METHODS = Arrays.stream(Feature.values())
            .filter(f -> f.treatment.inR())
            .flatMap(f -> f.methods.stream())
            .collect(Collectors.toUnmodifiableSet());

    /** The feature that covers an LSP method, or null. */
    public static final Map<String, Feature> BY_METHOD = Arrays.stream(Feature.values())
            .flatMap(f -> f.methods.stream().map(m -> Map.entry(m, f)))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    public static boolean allowsRegistration(String method) {
        return method != null && REGISTRATION_METHODS.contains(method);
    }

    static <T> Map<Feature, T> byFeature(Function<Feature, T> f) {
        return Arrays.stream(Feature.values()).collect(Collectors.toUnmodifiableMap(x -> x, f));
    }
}
