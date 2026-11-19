package com.bloxbean.intelliada.idea.joust.core;

import java.util.Map;
import java.util.Set;

/**
 * ADR-004 D3a items 1 and 2: the <b>read-only set R</b>, one constant that drives both the LSP4IJ feature allow-list and
 * the {@code client/registerCapability} filter.
 */
public final class ReadOnlyFeatureSet {

    private ReadOnlyFeatureSet() {
    }

    /** How IntelliAda treats each LSP4IJ client feature. */
    public enum Treatment {
        /** In R, and LSP4IJ's own feature is used (no server-supplied link or location leaves the file). */
        LSP4IJ,
        /** In R, but LSP4IJ's feature is disabled and IntelliAda provides it (hover, navigation; D3a items 6 and 7). */
        INTELLIADA,
        /** Not in R: disabled. */
        DISABLED
    }

    /**
     * Every {@code LSPClientFeatures.set…Feature} of LSP4IJ 0.10.0, classified. A guard test fails on any setter not
     * listed here, so a feature a future LSP4IJ adds is classified before it can be enabled.
     */
    public static final Map<String, Treatment> FEATURES = Map.ofEntries(
            Map.entry("setDiagnosticFeature", Treatment.LSP4IJ),
            Map.entry("setDocumentHighlightFeature", Treatment.LSP4IJ),
            Map.entry("setDocumentSymbolFeature", Treatment.LSP4IJ),
            Map.entry("setFoldingRangeFeature", Treatment.LSP4IJ),
            Map.entry("setSelectionRangeFeature", Treatment.LSP4IJ),
            Map.entry("setSemanticTokensFeature", Treatment.LSP4IJ),
            Map.entry("setProgressFeature", Treatment.LSP4IJ),
            Map.entry("setHoverFeature", Treatment.INTELLIADA),
            Map.entry("setDefinitionFeature", Treatment.INTELLIADA),
            Map.entry("setDeclarationFeature", Treatment.INTELLIADA),
            Map.entry("setTypeDefinitionFeature", Treatment.INTELLIADA),
            Map.entry("setImplementationFeature", Treatment.INTELLIADA),
            Map.entry("setReferencesFeature", Treatment.INTELLIADA),
            Map.entry("setUsageFeature", Treatment.INTELLIADA),
            Map.entry("setWorkspaceSymbolFeature", Treatment.INTELLIADA),
            Map.entry("setCompletionFeature", Treatment.DISABLED),
            Map.entry("setCodeActionFeature", Treatment.DISABLED),
            Map.entry("setCodeLensFeature", Treatment.DISABLED),
            Map.entry("setDocumentLinkFeature", Treatment.DISABLED),
            Map.entry("setDocumentColorFeature", Treatment.DISABLED),
            Map.entry("setFormattingFeature", Treatment.DISABLED),
            Map.entry("setOnTypeFormattingFeature", Treatment.DISABLED),
            Map.entry("setRenameFeature", Treatment.DISABLED),
            Map.entry("setInlayHintFeature", Treatment.DISABLED),
            Map.entry("setSignatureHelpFeature", Treatment.DISABLED),
            Map.entry("setCallHierarchyFeature", Treatment.DISABLED),
            Map.entry("setTypeHierarchyFeature", Treatment.DISABLED));

    /** The LSP methods of the features in R: the only dynamic registrations IntelliAda accepts. */
    public static final Set<String> REGISTRATION_METHODS = Set.of(
            "textDocument/diagnostic",
            "textDocument/hover",
            "textDocument/definition",
            "textDocument/declaration",
            "textDocument/typeDefinition",
            "textDocument/implementation",
            "textDocument/references",
            "textDocument/documentHighlight",
            "textDocument/documentSymbol",
            "workspace/symbol",
            "textDocument/foldingRange",
            "textDocument/selectionRange",
            "textDocument/semanticTokens");

    public static boolean allowsRegistration(String method) {
        return method != null && REGISTRATION_METHODS.contains(method);
    }
}
