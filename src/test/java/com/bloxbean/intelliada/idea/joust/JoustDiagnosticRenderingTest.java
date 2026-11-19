package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustConstants;
import com.bloxbean.intelliada.idea.joust.core.JoustDiagnosticText;
import com.bloxbean.intelliada.idea.joust.core.JoustDocsUrl;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** ADR-004 D3a item 8 (SEC-18, SEC-20, CDX-27) and I15. */
public class JoustDiagnosticRenderingTest {

    private static Diagnostic hostile() {
        Diagnostic d = new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)),
                "List<T> is bad <img src=http://127.0.0.1:9/x>", DiagnosticSeverity.Error,
                "<b>joust</b><img src=http://127.0.0.1:9/s>", "JSC0502");
        d.setCodeDescription(new DiagnosticCodeDescription("https://evil.example/\"><img src=http://127.0.0.1:9/h>"));
        d.setRelatedInformation(List.of(new DiagnosticRelatedInformation(
                new Location("file:///etc/passwd", new Range(new Position(3, 4), new Position(3, 5))),
                "<a href=\"file:///etc/passwd\">see</a>")));
        return d;
    }

    @Test
    public void tooltipEscapesEveryField() {
        String html = JoustDiagnosticText.tooltipHtml(hostile(), JoustDiagnosticText::describe);
        assertTrue(html.startsWith("<html>") && html.endsWith("</html>"));
        String body = html.substring("<html>".length(), html.length() - "</html>".length());
        assertFalse(body, body.contains("<img"));
        assertFalse(body, body.contains("<a "));
        assertFalse(body, body.contains("<b>"));
        assertFalse(body, body.contains("\""));
        assertTrue(body.contains("&lt;img src=http://127.0.0.1:9/x&gt;"));
        assertTrue(body.contains("https://evil.example/&quot;&gt;&lt;img"));
        assertTrue(body.contains("file:///etc/passwd:4:5"));
        String stripped = body.replace("<br>", "");
        assertFalse("only <br> markup is ours", stripped.contains("<"));
    }

    @Test
    public void messageIsPlainForPlainSurfaces() {
        assertEquals("List<T> is bad <img src=http://127.0.0.1:9/x>", JoustDiagnosticText.message(hostile()));
    }

    @Test
    public void documentationUrlIsOursAndOnlyForJscCodes() {
        Diagnostic d = hostile();
        assertEquals(JoustConstants.DIAGNOSTICS_CATALOGUE + "#:~:text=JSC0502,-error", JoustDocsUrl.forDiagnostic(d));
        d.setSeverity(DiagnosticSeverity.Warning);
        assertEquals(JoustConstants.DIAGNOSTICS_CATALOGUE + "#:~:text=JSC0502,-warning", JoustDocsUrl.forDiagnostic(d));
        d.setCodeDescription(new DiagnosticCodeDescription("https://github.com/someone-else/repo"));
        assertTrue(JoustDocsUrl.forDiagnostic(d).startsWith(JoustConstants.DIAGNOSTICS_CATALOGUE));
        for (String bad : new String[]{"JSC-INTERNAL", "JSC12345", "JSC050", "jsc0502", "JSC０５０２", "JSC0502 ", "X JSC0502", ""}) {
            d.setCode(bad);
            assertNull(bad, JoustDocsUrl.forDiagnostic(d));
        }
        d.setCode(Either.forRight(502));
        assertNull(JoustDocsUrl.forDiagnostic(d));
        assertTrue(JoustConstants.DIAGNOSTICS_CATALOGUE.startsWith("https://github.com/bloxbean/joust/"));
    }

    @Test
    public void plainText() {
        assertEquals("&lt;img src=&quot;x&quot;&gt; &amp; &#39;", PlainText.escape("<img src=\"x\"> & '"));
        assertEquals("red", PlainText.clean("\u001B[31mred\u001B[0m", 100));
        assertEquals("a\\u202Eb", PlainText.clean("a‮b", 100));
        assertEquals("abc", PlainText.clean("a\u0000b\u0007c", 100));
        assertTrue(PlainText.clean("x".repeat(50), 10).length() < 20);
        assertEquals("0.1.0-pre2", PlainText.label("<html>0.1.0-pre2", 40).replace("html", ""));
        assertEquals("<html>&lt;b&gt;x&lt;/b&gt;<br>y</html>", PlainText.html("<b>x</b>\ny"));
    }
}
