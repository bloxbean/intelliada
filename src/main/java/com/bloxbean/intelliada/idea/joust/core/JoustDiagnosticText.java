package com.bloxbean.intelliada.idea.joust.core;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Location;

import java.util.List;
import java.util.function.Function;

/**
 * ADR-004 D3a item 8 (SEC-18, SEC-20): diagnostics rendered by IntelliAda.
 *
 * <ul>
 *   <li>The <b>tooltip</b> is HTML built here: the message, source, code, {@code codeDescription.href} and every
 *   related-information message and location are escaped; the href is literal text, never a link.</li>
 *   <li>The <b>message</b> is plain and unescaped, for plain-text surfaces such as the Problems view.</li>
 * </ul>
 */
public final class JoustDiagnosticText {

    static final int MESSAGE_CAP = 4_000;
    static final int FIELD_CAP = 300;
    static final int RELATED_MAX = 20;

    private JoustDiagnosticText() {
    }

    public static String message(Diagnostic d) {
        return PlainText.clean(d == null ? "" : d.getMessage(), MESSAGE_CAP);
    }

    /**
     * @param describeLocation turns a related location into display text (a project-relative path and position, or the
     *                         URI); its result is escaped here too.
     */
    public static String tooltipHtml(Diagnostic d, Function<Location, String> describeLocation) {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append(PlainText.fragment(d.getMessage(), MESSAGE_CAP));
        StringBuilder meta = new StringBuilder();
        if (d.getSource() != null && !d.getSource().isEmpty()) meta.append(d.getSource());
        if (d.getCode() != null) {
            String code = d.getCode().isLeft() ? d.getCode().getLeft() : String.valueOf(d.getCode().getRight());
            if (meta.length() > 0) meta.append(' ');
            meta.append('[').append(code).append(']');
        }
        if (meta.length() > 0) sb.append("<br><br>").append(PlainText.fragment(meta.toString(), FIELD_CAP));
        if (d.getCodeDescription() != null && d.getCodeDescription().getHref() != null) {
            sb.append("<br>Server documentation link (not opened): ")
                    .append(PlainText.fragment(d.getCodeDescription().getHref(), FIELD_CAP));
        }
        List<DiagnosticRelatedInformation> related = d.getRelatedInformation();
        if (related != null && !related.isEmpty()) {
            sb.append("<br><br>Related:");
            int n = 0;
            for (DiagnosticRelatedInformation r : related) {
                if (n++ >= RELATED_MAX) {
                    sb.append("<br>…");
                    break;
                }
                String where = r.getLocation() == null ? "" : describeLocation.apply(r.getLocation());
                sb.append("<br>").append(PlainText.fragment(where, FIELD_CAP)).append(": ")
                        .append(PlainText.fragment(r.getMessage(), FIELD_CAP));
            }
        }
        return sb.append("</html>").toString();
    }

    /** A location as text: the URI and a one-based line and column. */
    public static String describe(Location l) {
        if (l == null) return "";
        String pos = l.getRange() == null || l.getRange().getStart() == null ? ""
                : ":" + (l.getRange().getStart().getLine() + 1) + ":" + (l.getRange().getStart().getCharacter() + 1);
        return l.getUri() + pos;
    }
}
