package com.bloxbean.intelliada.idea.joust.core;

import java.util.regex.Pattern;

/**
 * ADR-004 I15 and T6: project- and server-supplied text is shown as plain text, within size caps.
 *
 * <p>Every surface IntelliAda renders through Swing HTML (notifications, tooltips, dialogs, documentation) receives text
 * through {@link #html} or {@link #escape}: markup is escaped, so a server's {@code <img src=...>} or {@code <a href=...>}
 * is shown literally and never fetched or followed. ANSI escapes and other control characters are removed.
 */
public final class PlainText {

    public static final int DEFAULT_CAP = 8_000;
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-?]*[ -/]*[@-~]|\u001B[@-Z\\\\-_]|\u009B[0-?]*[ -/]*[@-~]");

    private PlainText() {
    }

    /** Removes ANSI sequences and control characters other than newline and tab, and caps the length. */
    public static String clean(String s, int cap) {
        if (s == null) return "";
        String t = ANSI.matcher(s).replaceAll("");
        StringBuilder sb = new StringBuilder(Math.min(t.length(), cap + 16));
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\r') continue;
            if (c == '\n' || c == '\t' || !Character.isISOControl(c)) {
                // Unicode bidi overrides and isolates are shown as visible escapes, not applied.
                if ((c >= '‪' && c <= '‮') || (c >= '⁦' && c <= '⁩')) {
                    sb.append(String.format("\\u%04X", (int) c));
                } else {
                    sb.append(c);
                }
            }
            if (sb.length() >= cap) {
                sb.append(" …");
                break;
            }
        }
        return sb.toString();
    }

    /** HTML-escapes {@code & < > " '}. */
    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Cleaned, escaped text with newlines as {@code <br>}: a fragment for an HTML surface. */
    public static String fragment(String s, int cap) {
        return escape(clean(s, cap)).replace("\n", "<br>");
    }

    /** A complete HTML document that displays {@code s} literally. */
    public static String html(String s) {
        return "<html>" + fragment(s, DEFAULT_CAP) + "</html>";
    }

    /**
     * Literal text for a surface that is not HTML but might auto-detect it (a Swing label shows HTML when its text starts
     * with {@code <html>}): cleaned and capped, every character kept as the user wrote it, and a zero-width space put in
     * front when the text would otherwise start with {@code <html}, so it is never taken for markup (M1-CDX-13).
     */
    public static String literal(String s, int cap) {
        String c = clean(s, cap);
        return c.stripLeading().regionMatches(true, 0, "<html", 0, 5) ? "\u200B" + c : c;
    }

    /**
     * A short label for a Swing component that may render HTML (a status bar widget, a list cell): only letters, digits
     * and {@code . _ - + ( ) : / ;} and spaces survive, capped.
     */
    public static String label(String s, int cap) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < cap; i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || " ._-+():/;,".indexOf(c) >= 0) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
