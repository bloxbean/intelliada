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
        assertEquals("the user's characters are kept; only controls go (M1-CDX-13)", "[31m<html><b>x</b>".replace("[31m", ""), m);
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
            if (i < 2) {
                assertEquals("form " + i + " is read, escaped", 1, got.size());
                var err = ((org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage) got.get(0)).getError();
                assertNotNull(err);
                assertFalse(err.getMessage(), err.getMessage().contains("<"));
            } else {
                assertEquals("form " + i + " (duplicate keys, UTF-16) ends the stream", List.of(), got);
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
}
