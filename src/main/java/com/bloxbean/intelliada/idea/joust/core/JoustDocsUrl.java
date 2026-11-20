package com.bloxbean.intelliada.idea.joust.core;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import java.util.regex.Pattern;

/**
 * ADR-004 D3a item 8 (CDX-27): the Open documentation destination is IntelliAda's own constant catalogue URL plus a
 * fragment built from the diagnostic's code, only when that code matches {@code ^JSC\d{4}$}, and its severity, exactly as
 * JOUST builds it ({@code model.js:35-38}). The server's {@code codeDescription.href} never chooses it.
 */
public final class JoustDocsUrl {

    /** ASCII digits only: Java's {@code \d} without UNICODE_CHARACTER_CLASS matches [0-9]. */
    static final Pattern CODE = Pattern.compile("JSC[0-9]{4}");

    private JoustDocsUrl() {
    }

    /** The documentation URL for a diagnostic, or {@code null} when its code is not a catalogue code. */
    public static String forDiagnostic(Diagnostic d) {
        if (d == null || d.getCode() == null || !d.getCode().isLeft()) return null;
        return forCode(d.getCode().getLeft(), d.getSeverity());
    }

    public static String forCode(String code, DiagnosticSeverity severity) {
        if (code == null || !CODE.matcher(code).matches()) return null;
        String sev = severity == DiagnosticSeverity.Error ? "error" : "warning";
        return JoustConstants.DIAGNOSTICS_CATALOGUE + "#:~:text=" + code + ",-" + sev;
    }
}
