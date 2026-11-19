package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.editor.JoustHoverText;
import com.bloxbean.intelliada.idea.joust.highlight.JoustHighlighterColors;
import com.bloxbean.intelliada.idea.joust.highlight.JoustSemanticTokensColorsProvider;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** ADR-004 D3a item 6 and I15 (hover as literal text), and D2's semantic token mapping (F16). */
public class JoustHoverAndTokensTest {

    @Test
    public void hoverIsLiteralText() {
        Hover h = new Hover(new MarkupContent("markdown",
                "```jsc\ncount: Int\n```\n<img src=http://127.0.0.1:9/x> [link](file:///etc/passwd) <a href=\"https://x\">x</a>"));
        String plain = JoustHoverText.plain(h);
        assertTrue(plain.startsWith("count: Int"));
        String html = JoustHoverText.html(plain);
        assertTrue(html.startsWith("<pre>") && html.endsWith("</pre>"));
        String body = html.substring(5, html.length() - 6);
        assertFalse(body.contains("<"));
        assertTrue(body.contains("&lt;img src=http://127.0.0.1:9/x&gt;"));
        assertTrue(body.contains("[link](file:///etc/passwd)"));
    }

    @Test
    public void hoverIsCappedBeforeItIsJoined() {
        String huge = "x".repeat(5_000_000);
        java.util.List<Either<String, MarkedString>> parts = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) parts.add(Either.forLeft(huge));
        long t0 = System.nanoTime();
        String plain = JoustHoverText.plain(new Hover(parts));
        assertTrue(plain.length() <= 2 * 20_000 + 10);
        assertTrue("bounded work", (System.nanoTime() - t0) / 1_000_000 < 2_000);
        assertTrue(JoustHoverText.html("\u001B[31m" + plain).length() < 200_000);
    }

    @Test
    public void legacyMarkedStrings() {
        Hover h = new Hover(List.of(Either.forLeft("<b>a</b>"), Either.forRight(new MarkedString("jsc", "b"))));
        assertEquals("<b>a</b>\n\nb", JoustHoverText.plain(h));
        assertNull(JoustHoverText.plain(new Hover(new MarkupContent("plaintext", "  "))));
        assertNull(JoustHoverText.plain(null));
    }

    @Test
    public void semanticTokens() {
        assertSame(JoustHighlighterColors.STATE_FIELD, JoustSemanticTokensColorsProvider.keyFor("property", List.of()));
        assertSame(JoustHighlighterColors.TIME_VALUE, JoustSemanticTokensColorsProvider.keyFor("variable", List.of("timeValue")));
        assertSame(JoustHighlighterColors.STD_FUNCTION, JoustSemanticTokensColorsProvider.keyFor("function", List.of("defaultLibrary")));
        assertSame(JoustHighlighterColors.FUNCTION_DECLARATION, JoustSemanticTokensColorsProvider.keyFor("method", List.of("declaration")));
        assertSame(JoustHighlighterColors.CONSTANT, JoustSemanticTokensColorsProvider.keyFor("variable", List.of("readonly")));
        for (String t : new String[]{"namespace", "type", "enum", "enumMember", "property", "parameter", "variable", "function",
                "method", "class", "keyword"}) {
            assertNotNull(t, JoustSemanticTokensColorsProvider.keyFor(t, List.of()));
        }
        assertNull(JoustSemanticTokensColorsProvider.keyFor("string", List.of()));
    }
}
