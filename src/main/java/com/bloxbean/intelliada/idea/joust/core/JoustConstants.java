package com.bloxbean.intelliada.idea.joust.core;

/** Fixed values for IntelliAda's JOUST support (ADR-004). */
public final class JoustConstants {
    private JoustConstants() {
    }

    /** The LSP4IJ server id (D3). */
    public static final String SERVER_ID = "joustLanguageServer";
    public static final String LANGUAGE_ID = "jsc";
    public static final String JSC_EXTENSION = "jsc";
    public static final String NOTIFICATION_GROUP = "JOUST";

    /** The JOUST release the bundled grammar and the documentation URL are pinned to (D2, D3a item 8). */
    public static final String JOUST_TAG = "v0.1.0-pre2";

    /**
     * IntelliAda's own documentation URL for JOUST diagnostics: a constant equal to JOUST's {@code CATALOGUE}
     * ({@code js/joust/src/lsp/model.js:10} at {@link #JOUST_TAG}). The server's {@code codeDescription.href} never
     * chooses the destination (CDX-27).
     */
    public static final String DIAGNOSTICS_CATALOGUE =
            "https://github.com/bloxbean/joust/blob/main/docs/reference/jsc-2-diagnostics.md";

    public static boolean isJscName(String fileName) {
        return fileName != null && fileName.endsWith("." + JSC_EXTENSION);
    }
}
