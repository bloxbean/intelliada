package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * ADR-004 I15, T6 (M1-SEC-1): the server's stdout, re-framed so that every JSON-RPC <b>error message</b> is escaped and
 * capped before LSP4J reads it. LSP4IJ shows the error of a request it issued itself (folding, semantic tokens,
 * document symbols, …) in an IDE notification, which renders HTML; this is the one place that covers all of them.
 *
 * <p>Every frame is parsed and re-serialised ({@link #sanitize}); a frame that does not parse, one over
 * {@link #MAX_BODY} bytes, or a malformed header ends the stream: the connection is closed rather than parsed past.
 */
final class JoustSanitizingInput extends InputStream {

    static final int MAX_HEADER = 8 * 1024;
    static final int MAX_BODY = 64 * 1024 * 1024;
    static final int ERROR_CAP = 500;

    private final InputStream in;
    private byte[] buf = new byte[0];
    private int pos;
    private boolean eof;

    JoustSanitizingInput(InputStream in) {
        this.in = in;
    }

    @Override
    public int read() throws IOException {
        if (!fill()) return -1;
        return buf[pos++] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) return 0;
        if (!fill()) return -1;
        int n = Math.min(len, buf.length - pos);
        System.arraycopy(buf, pos, b, off, n);
        pos += n;
        return n;
    }

    @Override
    public int available() {
        return buf.length - pos;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private boolean fill() throws IOException {
        while (pos >= buf.length) {
            if (eof) return false;
            byte[] frame = nextFrame();
            if (frame == null) {
                eof = true;
                return false;
            }
            buf = frame;
            pos = 0;
        }
        return true;
    }

    /** The next frame, rewritten if needed; null at the end of the stream or on a malformed frame. */
    private byte[] nextFrame() throws IOException {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int c = in.read();
            if (c < 0) return null;
            header.write(c);
            if (header.size() > MAX_HEADER) return null;
            matched = (c == (matched % 2 == 0 ? '\r' : '\n')) ? matched + 1 : (c == '\r' ? 1 : 0);
        }
        // Headers are normalised (M1-SEC-1, round 2): exactly one Content-Length of digits, at most one Content-Type
        // whose charset is UTF-8, nothing else. Only Content-Length is re-emitted, so LSP4J decodes the body as UTF-8,
        // exactly as it was parsed here.
        String head = header.toString(StandardCharsets.US_ASCII);
        int length = -1;
        boolean contentType = false;
        for (String line : head.substring(0, head.length() - 4).split("\r\n", -1)) {
            int colon = line.indexOf(':');
            if (colon <= 0) return null;
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (name.equals("content-length")) {
                if (length >= 0 || !value.matches("[0-9]{1,9}")) return null;
                length = Integer.parseInt(value);
            } else if (name.equals("content-type")) {
                if (contentType || !utf8(value)) return null;
                contentType = true;
            } else {
                return null;
            }
        }
        if (length < 0 || length > MAX_BODY) return null;
        byte[] body = in.readNBytes(length);
        if (body.length < length) return null;
        byte[] out = sanitize(body);
        if (out == null) return null;
        return concat(("Content-Length: " + out.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII), out);
    }

    /**
     * The body, parsed <b>structurally</b> and always re-serialised in canonical form, or {@code null} when it cannot be
     * parsed (the stream then ends). Re-serialising means LSP4J never sees a spelling IntelliAda's parser read
     * differently: escaped member names, duplicate keys and lenient syntax all either canonicalise or end the stream
     * (M1-CDX-22). Rules:
     * <ul>
     *   <li>{@code error.message}: escaped and capped; {@code error.data} dropped (LSP4IJ shows errors as HTML);</li>
     *   <li>{@code window/logMessage}, {@code window/showMessage} and {@code window/showMessageRequest}
     *   {@code params.message}: cleaned and capped as literal text;</li>
     *   <li>every {@code collapsedText} (folding placeholders): cleaned and capped as literal text.</li>
     * </ul>
     */
    static byte[] sanitize(byte[] body) {
        JSONObject o;
        try {
            o = new JSONObject(new String(body, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return null;
        }
        JSONObject error = o.optJSONObject("error");
        if (error != null) {
            Object m = error.opt("message");
            error.put("message", PlainText.escape(PlainText.clean(m instanceof String str ? str : "", ERROR_CAP)));
            error.remove("data");
        } else if (o.has("error")) {
            o.remove("error");
            o.put("error", new JSONObject().put("code", -32603).put("message", "error"));
        }
        Object method = o.opt("method");
        if ("window/logMessage".equals(method) || "window/showMessage".equals(method) || "window/showMessageRequest".equals(method)) {
            JSONObject params = o.optJSONObject("params");
            if (params != null && params.opt("message") instanceof String m) params.put("message", PlainText.literal(m, MESSAGE_CAP));
        }
        literalCollapsedText(o, 0);
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }

    static final int MESSAGE_CAP = 4_000;
    static final int FOLD_CAP = 200;

    private static void literalCollapsedText(Object node, int depth) {
        if (depth > 64) return;
        if (node instanceof JSONObject obj) {
            for (String k : obj.keySet()) {
                Object v = obj.opt(k);
                if (k.equals("collapsedText") && v instanceof String str) obj.put(k, PlainText.literal(str, FOLD_CAP));
                else literalCollapsedText(v, depth + 1);
            }
        } else if (node instanceof org.json.JSONArray arr) {
            for (int i = 0; i < arr.length(); i++) literalCollapsedText(arr.opt(i), depth + 1);
        }
    }

    /** A Content-Type whose charset, if given, is UTF-8 (LSP's default); anything else ends the stream. */
    static boolean utf8(String contentType) {
        String v = contentType.toLowerCase(Locale.ROOT).replace(" ", "");
        int i = v.indexOf("charset=");
        if (i < 0) return true;
        String cs = v.substring(i + "charset=".length());
        int semi = cs.indexOf(';');
        if (semi >= 0) cs = cs.substring(0, semi);
        cs = cs.replace("\"", "");
        return cs.equals("utf-8") || cs.equals("utf8");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
