package com.bloxbean.intelliada.idea.joust.lsp;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * M1-SEC-1, M1-CDX-22, M1-CDX-13: every frame is parsed structurally and re-serialised; error messages are escaped and
 * capped whatever the spelling of their member names; messages and folding placeholders become literal text.
 */
public class JoustSanitizingInputTest {

    private static String frame(String body) {
        return frameOf(body);
    }

    private static String frameOf(String body) {
        return "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body;
    }

    private static List<JSONObject> through(String raw) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new JoustSanitizingInput(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8))).transferTo(out);
        String s = out.toString(StandardCharsets.UTF_8);
        List<JSONObject> frames = new ArrayList<>();
        while (!s.isEmpty()) {
            int sep = s.indexOf("\r\n\r\n");
            int len = Integer.parseInt(s.substring("Content-Length: ".length(), sep));
            byte[] rest = s.substring(sep + 4).getBytes(StandardCharsets.UTF_8);
            frames.add(new JSONObject(new String(rest, 0, len, StandardCharsets.UTF_8)));
            s = new String(rest, len, rest.length - len, StandardCharsets.UTF_8);
        }
        return frames;
    }

    @Test
    public void ordinaryFramesKeepTheirMeaning() throws Exception {
        var out = through(frame("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"x\":\"<b>é</b>\",\"n\":[0,1,2]}}")
                + "Content-Type: application/vscode-jsonrpc; charset=utf-8\r\n" + frame("{\"jsonrpc\":\"2.0\",\"method\":\"m\",\"params\":{\"error\":1}}"));
        assertEquals(2, out.size());
        assertEquals("<b>é</b>", out.get(0).getJSONObject("result").getString("x"));
        assertEquals(1, out.get(1).getJSONObject("params").getInt("error"));
    }

    @Test
    public void errorMessagesAreEscapedAndCapped() throws Exception {
        var o = through(frame("{\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":-32603,\"message\":\"<img src=\\\"http://127.0.0.1:9/x\\\">"
                + "y".repeat(5000) + "\",\"data\":\"<img>\"}}")).get(0);
        String m = o.getJSONObject("error").getString("message");
        assertFalse(m, m.contains("<"));
        assertTrue(m.length() < 700);
        assertFalse(o.getJSONObject("error").has("data"));
        assertEquals(7, o.getInt("id"));
    }

    @Test
    public void escapedMemberNamesAreTheSameMember() throws Exception {
        // "\u0065rror" is "error" to every JSON reader, LSP4J's included.
        var o = through(frame("{\"jsonrpc\":\"2.0\",\"id\":8,\"\\u0065rror\":{\"code\":1,\"m\\u0065ssage\":\"<img src=x>\"}}")).get(0);
        assertFalse(o.toString(), o.toString().contains("<img"));
        assertTrue(o.getJSONObject("error").getString("message").contains("&lt;img"));
    }

    @Test
    public void messagesAndFoldingPlaceholdersBecomeLiteral() throws Exception {
        var log = through(frame("{\"jsonrpc\":\"2.0\",\"method\":\"window/logMessage\",\"params\":{\"type\":3,\"message\":\"\\u001b[31m<html><b>x</b>\"}}")).get(0);
        String m = log.getJSONObject("params").getString("message");
        assertEquals("the characters are kept; controls go; an <html> start is defused (r9 E6, M1-CDX-13)", "\u200B<html><b>x</b>", m);
        var fold = through(frame("{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":[{\"startLine\":0,\"endLine\":2,\"collapsedText\":\"<html><img src=x>"
                + "z".repeat(1000) + "\"}]}")).get(0);
        String c = fold.getJSONArray("result").getJSONObject(0).getString("collapsedText");
        assertTrue("an <html> start is defused, the text kept", c.startsWith("\u200B<html><img src=x>"));
        assertTrue(c.length() <= 210);
    }

    /** M1-SEC-1 round 2: the four bypasses, each checked through LSP4J's own message reader. */
    @Test
    public void bypassFormsReachLsp4jEscaped() throws Exception {
        String img = "<img src=x>";
        List<byte[]> forms = new ArrayList<>();
        forms.add(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"\\u0065rror\":{\"code\":1,\"message\":\"" + img + "\"}}").getBytes(StandardCharsets.UTF_8));
        forms.add(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",'error':{'code':1,'message':'" + img + "'}}").getBytes(StandardCharsets.UTF_8));
        forms.add(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":1,\"message\":\"a\"},\"error\":{\"code\":1,\"message\":\"" + img + "\"}}")
                .getBytes(StandardCharsets.UTF_8));
        byte[] utf16 = ("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":1,\"message\":\"" + img + "\"}}").getBytes(StandardCharsets.UTF_16LE);
        ByteArrayOutputStream f16 = new ByteArrayOutputStream();
        f16.write(("Content-Length: " + utf16.length + "\r\nContent-Type: application/vscode-jsonrpc; charset=utf-16le\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        f16.write(utf16);
        forms.add(f16.toByteArray());
        for (int i = 0; i < forms.size(); i++) {
            byte[] form = forms.get(i);
            var handler = new org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler(
                    java.util.Map.of(), gson -> {});
            List<org.eclipse.lsp4j.jsonrpc.messages.Message> got = new ArrayList<>();
            var producer = new org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer(
                    new JoustSanitizingInput(new ByteArrayInputStream(form)), handler);
            try {
                producer.listen(got::add);
            } catch (RuntimeException ignored) {
                // the outcome is judged by what LSP4J read
            }
            if (i == 0) {
                assertEquals("form " + i + " is read, escaped", 1, got.size());
                var err = ((org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage) got.get(0)).getError();
                assertNotNull(err);
                assertFalse(err.getMessage(), err.getMessage().contains("<"));
            } else {
                assertEquals("form " + i + " (single quotes, duplicate keys, UTF-16) ends the stream", List.of(), got);
            }
        }
    }

    /** M1-CDX-22, M1-CDX-27, M1-SEC-7, through LSP4J's reader: nothing partial, malformed or too deep is delivered. */
    @Test
    public void strictBodiesThroughLsp4j() throws Exception {
        String ok = "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":null}";
        assertEquals(1, lsp4jRead(frame(ok).getBytes(StandardCharsets.UTF_8)).size());
        assertEquals("trailing input", 0, lsp4jRead(frame(ok + "{\"id\":\"2\",\"error\":{\"code\":1,\"message\":\"<img>\"}}")
                .getBytes(StandardCharsets.UTF_8)).size());
        byte[] body = ("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":1,\"message\":\"a\u00ff\"}}").getBytes(StandardCharsets.ISO_8859_1);
        byte[] framed = concat(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII), body);
        assertEquals("malformed UTF-8", 0, lsp4jRead(framed).size());
        String deep = "[".repeat(64) + "1" + "]".repeat(64);
        assertEquals("depth 64 is allowed", 1, lsp4jRead(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":" + deep.substring(1, deep.length() - 1) + "}")
                .getBytes(StandardCharsets.UTF_8)).size());
        String deeper = "[".repeat(65) + "1" + "]".repeat(65);
        assertEquals("depth 65 ends the stream", 0, lsp4jRead(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":" + deeper + "}")
                .getBytes(StandardCharsets.UTF_8)).size());
        // A raw NUL after a complete object, then more input: org.json would read the NUL as the end (M1-CDX-22).
        byte[] nulBody = (ok + "\u0000{\"id\":\"2\"}").getBytes(StandardCharsets.UTF_8);
        assertEquals("NUL then trailing input", 0, lsp4jRead(concat(("Content-Length: " + nulBody.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII), nulBody)).size());
        // Brackets inside single-quoted strings cannot skew the depth count: single quotes are refused (M1-CDX-27).
        assertEquals(0, lsp4jRead(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":['" + "]".repeat(70) + "']}").getBytes(StandardCharsets.UTF_8)).size());
        assertEquals(0, lsp4jRead(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{x:1}}").getBytes(StandardCharsets.UTF_8)).size());
        // Unicode escapes take ASCII hex digits only: a fullwidth 'Ａ' ends the stream; the ASCII form is read (M1-CDX-22).
        assertEquals(0, lsp4jRead(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":\"\\u00\uFF21\uFF21\"}").getBytes(StandardCharsets.UTF_8)).size());
        assertEquals(0, lsp4jRead(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":\"\\u0\u0661AA\"}").getBytes(StandardCharsets.UTF_8)).size());
        assertEquals(1, lsp4jRead(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":\"\\u00AA\"}").getBytes(StandardCharsets.UTF_8)).size());
        // A trailing comma is not JSON (M1-CDX-22).
        assertEquals(0, lsp4jRead(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":1,}").getBytes(StandardCharsets.UTF_8)).size());
        assertEquals(0, lsp4jRead(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":[1,]}").getBytes(StandardCharsets.UTF_8)).size());
        assertEquals(0, lsp4jRead(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":01}").getBytes(StandardCharsets.UTF_8)).size());
        // A wide, shallow response is fine.
        String wide = "[" + "[1,2,3],".repeat(20_000) + "[4]]";
        assertEquals(1, lsp4jRead(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":" + wide + "}").getBytes(StandardCharsets.UTF_8)).size());
        var nullError = lsp4jRead(frame("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"x\":1},\"error\":null}").getBytes(StandardCharsets.UTF_8));
        assertEquals(1, nullError.size());
        assertNull("error:null is no error", ((org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage) nullError.get(0)).getError());
    }

    private static List<org.eclipse.lsp4j.jsonrpc.messages.Message> lsp4jRead(byte[] raw) {
        var handler = new org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler(java.util.Map.of(), gson -> {});
        List<org.eclipse.lsp4j.jsonrpc.messages.Message> got = new ArrayList<>();
        try {
            new org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer(new JoustSanitizingInput(new ByteArrayInputStream(raw)), handler).listen(got::add);
        } catch (RuntimeException ignored) {
            // judged by what was read
        }
        return got;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    @Test
    public void unparseableOrOversizedFramesEndTheStream() throws Exception {
        assertEquals(List.of(), through("Content-Length: 999999999\r\n\r\n{}"));
        assertEquals(List.of(), through("Content-Length: x\r\n\r\n{}"));
        assertEquals(List.of(), through("X".repeat(10_000)));
        assertEquals("duplicate keys end the stream", List.of(),
                through(frame("{\"id\":1,\"error\":{\"code\":1,\"message\":\"a\"},\"error\":{\"code\":1,\"message\":\"<img>\"}}")));
        assertEquals(List.of(), through(frame("{not json")));
        assertEquals("an unknown header ends the stream", List.of(), through("X-Evil: 1\r\n" + frame("{\"id\":1}")));
        assertEquals("two lengths end the stream", List.of(), through("Content-Length: 2\r\n" + frame("{}")));
    }

    /** M1-CDX-27: the container and value budgets hold for shallow frames; the boundary with a small budget. */
    @Test
    public void budgetsBoundShallowFrames() {
        var b = new JoustStrictJson.Budget(64, 5, 1_000);
        String four = "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":[[],[],[]]}";   // 5 containers
        String five = "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":[[],[],[],[]]}"; // 6 containers
        assertNotNull(JoustSanitizingInput.sanitize(four.getBytes(StandardCharsets.UTF_8), b));
        assertNull(JoustSanitizingInput.sanitize(five.getBytes(StandardCharsets.UTF_8), b));
        var v = new JoustStrictJson.Budget(64, 100, 6);
        assertNotNull(JoustSanitizingInput.sanitize("{\"a\":1,\"b\":2,\"c\":[3,4]}".getBytes(StandardCharsets.UTF_8), v));
        assertNull(JoustSanitizingInput.sanitize("{\"a\":1,\"b\":2,\"c\":[3,4,5]}".getBytes(StandardCharsets.UTF_8), v));
        String deep = "{\"r\":" + "[".repeat(63) + "]".repeat(63) + "}";
        assertNotNull(JoustSanitizingInput.sanitize(deep.getBytes(StandardCharsets.UTF_8)));
        assertNull(JoustSanitizingInput.sanitize(("{\"r\":" + "[".repeat(64) + "]".repeat(64) + "}").getBytes(StandardCharsets.UTF_8)));
        // The default budget refuses millions of tiny arrays before building them.
        String many = "{\"r\":[" + "[],".repeat(150_000) + "[]]}";
        assertNull(JoustSanitizingInput.sanitize(many.getBytes(StandardCharsets.UTF_8)));
    }

    /** M1-CDX-31: supplementary characters round-trip as themselves, and the rewritten body is bounded. */
    @Test
    public void unicodeRoundTripsAndTheOutputIsBounded() throws Exception {
        String emoji = "\uD83D\uDE00".repeat(100);
        byte[] in = ("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":\"" + emoji + "\"}").getBytes(StandardCharsets.UTF_8);
        byte[] out = JoustSanitizingInput.sanitize(in);
        assertNotNull(out);
        assertEquals("written as itself, no growth", in.length, out.length);
        assertEquals(emoji, new JSONObject(new String(out, StandardCharsets.UTF_8)).getString("result"));
        // A lone surrogate (from an escape) is escaped again, not written raw.
        byte[] lone = JoustSanitizingInput.sanitize("{\"r\":\"\\uD800x\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("{\"r\":\"\\ud800x\"}", new String(lone, StandardCharsets.UTF_8));
        assertNotNull(JoustSanitizingInput.sanitize(in, JoustStrictJson.DEFAULT, in.length));
        assertNull(JoustSanitizingInput.sanitize(in, JoustStrictJson.DEFAULT, in.length - 1));
    }

    /** M1-CDX-31: the budget is charged as bytes are emitted; serialisation stops before a later member is visited. */
    @Test
    public void theWriterStopsAtTheFirstWriteOverBudget() throws Exception {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("a", "x".repeat(100));
        m.put("b", new Object());   // not a JSON value: visiting it would throw IllegalArgumentException
        try {
            JoustStrictJson.writeUtf8(m, 50);
            fail("over budget");
        } catch (JoustStrictJson.Refused expected) {
            // stopped inside "a", before "b"
        }
        java.util.Map<String, Object> ok = new java.util.LinkedHashMap<>();
        ok.put("a", "\u00e9\uD83D\uDE00");
        byte[] exact = JoustStrictJson.writeUtf8(ok, 1 << 20);
        assertEquals("{\"a\":\"\u00e9\uD83D\uDE00\"}", new String(exact, StandardCharsets.UTF_8));
        assertEquals(exact.length, JoustStrictJson.writeUtf8(ok, exact.length).length);
        try {
            JoustStrictJson.writeUtf8(ok, exact.length - 1);
            fail("one byte under");
        } catch (JoustStrictJson.Refused expected) {
            // refused
        }
    }

    /**
     * ADR-004 §12.2 E23: the initialize result keeps only the read-only capabilities, loses {@code willSaveWaitUntil}
     * (UP-CDX-1), pull diagnostics (UP-CDX-3), virtual documents (UP-CDX-5) and every other workspace capability, and its
     * serverInfo becomes a plain title that cannot start an HTML tooltip (UP-CDX-4).
     */
    @Test
    public void theInitializeResultIsCutToTheReadOnlySet() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"capabilities\":{"
                + "\"textDocumentSync\":{\"openClose\":true,\"change\":2,\"willSave\":true,\"willSaveWaitUntil\":true,\"save\":{\"includeText\":false}},"
                + "\"hoverProvider\":true,\"definitionProvider\":true,\"referencesProvider\":true,\"documentSymbolProvider\":true,"
                + "\"foldingRangeProvider\":true,\"selectionRangeProvider\":true,\"semanticTokensProvider\":{\"legend\":{\"tokenTypes\":[],\"tokenModifiers\":[]},\"full\":true},"
                + "\"diagnosticProvider\":{\"interFileDependencies\":true,\"workspaceDiagnostics\":true},"
                + "\"workspace\":{\"workspaceFolders\":{\"supported\":true,\"changeNotifications\":true},\"textDocumentContent\":{\"schemes\":[\"joust-lib\"]},\"fileOperations\":{}},"
                + "\"completionProvider\":{},\"inlineCompletionProvider\":true,\"linkedEditingRangeProvider\":true,\"inlineValueProvider\":true,"
                + "\"codeActionProvider\":true,\"executeCommandProvider\":{\"commands\":[\"x\"]},\"renameProvider\":true,"
                + "\"documentFormattingProvider\":true,\"notebookDocumentSync\":{},\"experimental\":{\"joust\":{}},\"futureProvider\":true},"
                + "\"serverInfo\":{\"name\":\"<html><b>Sample</b>\",\"version\":\" <html><img src=http://127.0.0.1:9/v>\"}}}";
        JSONObject r = through(frame(body)).get(0).getJSONObject("result");
        JSONObject caps = r.getJSONObject("capabilities");
        assertEquals(new java.util.TreeSet<>(List.of("textDocumentSync", "hoverProvider", "definitionProvider", "referencesProvider",
                "documentSymbolProvider", "foldingRangeProvider", "selectionRangeProvider", "semanticTokensProvider")),
                new java.util.TreeSet<>(caps.keySet()));
        JSONObject sync = caps.getJSONObject("textDocumentSync");
        assertFalse(sync.has("willSaveWaitUntil"));
        assertTrue(sync.getBoolean("openClose"));
        assertEquals(2, sync.getInt("change"));
        JSONObject info = r.getJSONObject("serverInfo");
        for (String k : new String[]{"name", "version"}) {
            String v = info.getString(k);
            assertFalse(k + ": " + v, v.contains("<") || v.contains(">"));
            assertFalse(k, javax.swing.plaf.basic.BasicHTML.isHTMLString(v.strip()));
        }
        assertTrue(info.getString("name"), info.getString("name").endsWith("\u2039html\u203A\u2039b\u203ASample\u2039/b\u203A"));
    }

    @Test
    public void aNumericSyncKindIsKeptAndABadServerInfoIsDropped() throws Exception {
        JSONObject r = through(frame("{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"capabilities\":{\"textDocumentSync\":1},"
                + "\"serverInfo\":{\"version\":\"<html>1\"}}}")).get(0).getJSONObject("result");
        assertEquals(1, r.getJSONObject("capabilities").getInt("textDocumentSync"));
        assertFalse(r.has("serverInfo"));
        JSONObject r2 = through(frame("{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{\"capabilities\":{},"
                + "\"serverInfo\":{\"name\":\"joust-lsp\",\"version\":7}}}")).get(0).getJSONObject("result");
        assertEquals("joust-lsp", r2.getJSONObject("serverInfo").getString("name"));
        assertFalse(r2.getJSONObject("serverInfo").has("version"));
    }
}
