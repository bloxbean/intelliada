package com.bloxbean.intelliada.idea.joust.lsp;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/** M1-SEC-1: the server's frames pass through unchanged, except error messages, which are escaped and capped. */
public class JoustSanitizingInputTest {

    private static String frame(String body) {
        return "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body;
    }

    private static String through(String raw) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new JoustSanitizingInput(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8))).transferTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    public void ordinaryFramesPassThroughByteForByte() throws Exception {
        String raw = frame("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"x\":\"<b>é</b>\"}}")
                + "Content-Type: application/vscode-jsonrpc; charset=utf-8\r\n" + frame("{\"jsonrpc\":\"2.0\",\"method\":\"m\",\"params\":{\"error\":1}}");
        assertEquals(raw, through(raw));
    }

    @Test
    public void errorMessagesAreEscapedAndCapped() throws Exception {
        String raw = frame("{\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":-32603,\"message\":\"<img src=\\\"http://127.0.0.1:9/x\\\">"
                + "y".repeat(5000) + "\",\"data\":\"<img>\"}}");
        String out = through(raw);
        int sep = out.indexOf("\r\n\r\n");
        String body = out.substring(sep + 4);
        assertEquals(Integer.parseInt(out.substring("Content-Length: ".length(), sep)), body.getBytes(StandardCharsets.UTF_8).length);
        org.json.JSONObject o = new org.json.JSONObject(body);
        String m = o.getJSONObject("error").getString("message");
        assertFalse(m, m.contains("<"));
        assertTrue(m.length() < 700);
        assertFalse(o.getJSONObject("error").has("data"));
        assertEquals(7, o.getInt("id"));
    }

    @Test
    public void oversizedOrMalformedFramesEndTheStream() throws Exception {
        assertEquals("", through("Content-Length: 999999999\r\n\r\n{}"));
        assertEquals("", through("Content-Length: x\r\n\r\n{}"));
        assertEquals("", through("X".repeat(10_000)));
    }
}
