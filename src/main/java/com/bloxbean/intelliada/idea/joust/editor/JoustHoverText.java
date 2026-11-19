package com.bloxbean.intelliada.idea.joust.editor;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.List;

/**
 * ADR-004 D3a item 6: hover text rendered by IntelliAda as escaped literal text. No markdown-to-HTML, no anchors, no
 * images: a server's {@code <img src=...>} or link is shown as characters. Markdown code fences are dropped for
 * readability; nothing else is interpreted.
 */
public final class JoustHoverText {

    static final int CAP = 20_000;
    static final int MAX_PARTS = 50;

    /** Each part is cut to the cap before anything is concatenated or split (M1-CDX-13). */
    private static String capped(String s) {
        return s == null ? "" : s.length() > CAP ? s.substring(0, CAP) : s;
    }

    private JoustHoverText() {
    }

    /** The hover as plain text, or {@code null} when empty. */
    public static String plain(Hover hover) {
        if (hover == null || hover.getContents() == null) return null;
        StringBuilder sb = new StringBuilder();
        Either<List<Either<String, MarkedString>>, MarkupContent> c = hover.getContents();
        if (c.isRight()) {
            if (c.getRight() != null) sb.append(capped(c.getRight().getValue()));
        } else if (c.getLeft() != null) {
            int parts = 0;
            for (Either<String, MarkedString> e : c.getLeft()) {
                if (e == null) continue;
                if (++parts > MAX_PARTS || sb.length() >= CAP) break;
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(capped(e.isLeft() ? e.getLeft() : e.getRight() == null ? "" : e.getRight().getValue()));
            }
        }
        String text = sb.toString();
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.trim().startsWith("```")) continue;
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        String r = out.toString().strip();
        return r.isEmpty() ? null : r;
    }

    /** The documentation popup's HTML: the text, escaped, in a preformatted block. */
    public static String html(String plain) {
        return "<pre>" + PlainText.escape(PlainText.clean(plain, CAP)) + "</pre>";
    }
}
