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
        // Literal (UP-SEC-4): plain surfaces such as Inspect Code's problem tooltip treat a leading <html as HTML.
        return PlainText.literal(d == null ? "" : messageOf(d), MESSAGE_CAP);
    }

    /**
     * @param describeLocation turns a related location into display text (a project-relative path and position, or the
     *                         URI); its result is escaped here too.
     */
    public static String tooltipHtml(Diagnostic d, Function<Location, String> describeLocation) {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append(PlainText.fragment(messageOf(d), MESSAGE_CAP));
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

    /**
     * The diagnostic's message as text, whichever LSP4J LSP4IJ brings (ADR-004 §12.2 E20): LSP4J 0.2x returns a
     * {@code String}; LSP4J 1.0 (LSP 3.18) returns {@code Either<String, MarkupContent>}, whose markup is shown as its
     * literal source, never rendered. The accessor is looked up by name because the two return types are not
     * link-compatible: code compiled against one fails on the other with {@code NoSuchMethodError}.
     */
    public static String messageOf(Diagnostic d) {
        Object m;
        try {
            m = MESSAGE.invoke(d);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
        if (m instanceof org.eclipse.lsp4j.jsonrpc.messages.Either<?, ?> e) m = e.isLeft() ? e.getLeft() : e.getRight();
        if (m instanceof org.eclipse.lsp4j.MarkupContent mc) m = mc.getValue();
        return m instanceof String s ? s : "";
    }

    private static final java.lang.reflect.Method MESSAGE;

    static {
        try {
            MESSAGE = Diagnostic.class.getMethod("getMessage");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** A location as text: the URI and a one-based line and column. */
    public static String describe(Location l) {
        if (l == null) return "";
        String pos = l.getRange() == null || l.getRange().getStart() == null ? ""
                : ":" + (l.getRange().getStart().getLine() + 1) + ":" + (l.getRange().getStart().getCharacter() + 1);
        return l.getUri() + pos;
    }
}
