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
        if (depthExceeds(body, MAX_DEPTH)) return null;
        String text;
        try {
            // Strict UTF-8: malformed bytes end the stream instead of being replaced (M1-CDX-22).
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(body)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return null;
        }
        JSONObject o;
        try {
            org.json.JSONTokener t = new org.json.JSONTokener(text);
            Object v = t.nextValue();
            // One object, and nothing after it: org.json stops at the closing brace, LSP4J would read on (M1-CDX-22).
            // The strict lexical check has already refused NUL, which org.json's tokenizer would read as the end.
            if (!(v instanceof JSONObject obj) || t.nextClean() != 0 || t.more()) return null;
            o = obj;
        } catch (RuntimeException e) {
            return null;
        }
        JSONObject error = o.optJSONObject("error");
        if (error != null) {
            Object m = error.opt("message");
            error.put("message", PlainText.escape(PlainText.clean(m instanceof String str ? str : "", ERROR_CAP)));
            error.remove("data");
        } else if (o.has("error") && o.isNull("error")) {
            o.remove("error");   // "error": null is no error (M1-SEC-7)
        } else if (o.has("error")) {
            o.remove("error");
            o.put("error", new JSONObject().put("code", -32603).put("message", "error"));
        }
        Object method = o.opt("method");
        if ("window/logMessage".equals(method) || "window/showMessage".equals(method) || "window/showMessageRequest".equals(method)) {
            JSONObject params = o.optJSONObject("params");
            if (params != null && params.opt("message") instanceof String m) params.put("message", PlainText.clean(m, MESSAGE_CAP));
        }
        literalCollapsedText(o, 0);
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }

    static final int MESSAGE_CAP = 4_000;
    /** Nesting deeper than this ends the stream; checked on the bytes, before any tree is built (M1-CDX-27). */
    static final int MAX_DEPTH = 64;

    /**
     * True unless the body is <b>strict</b> JSON with nesting no deeper than {@code max} (M1-CDX-22, M1-CDX-27): strings in
     * double quotes only, no raw control characters (NUL included) inside or outside them, and outside strings only
     * structure, whitespace, numbers and the literals {@code true}, {@code false} and {@code null}. No single quotes,
     * unquoted names, comments or NUL: whatever passes is read the same by org.json and by LSP4J's Gson. Checked on
     * the bytes, before any tree is built.
     */
    static boolean depthExceeds(byte[] body, int max) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < body.length; i++) {
            int b = body[i] & 0xff;
            if (inString) {
                if (b < 0x20) return true;
                if (escaped) escaped = false;
                else if (b == '\\') escaped = true;
                else if (b == '"') inString = false;
                continue;
            }
            switch (b) {
                case '"' -> inString = true;
                case '{', '[' -> {
                    if (++depth > max) return true;
                }
                case '}', ']' -> {
                    if (--depth < 0) return true;
                }
                case ',', ':', ' ', '\t', '\n', '\r', '-', '+', '.', 'e', 'E', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                }
                case 't', 'f', 'n' -> {
                    String lit = b == 't' ? "true" : b == 'f' ? "false" : "null";
                    if (i + lit.length() > body.length) return true;
                    for (int k = 0; k < lit.length(); k++) {
                        if (body[i + k] != lit.charAt(k)) return true;
                    }
                    i += lit.length() - 1;
                }
                default -> {
                    return true;
                }
            }
        }
        return inString || depth != 0;
    }
    static final int FOLD_CAP = 200;

    private static void literalCollapsedText(Object node, int depth) {
        if (depth > 64) return;
        if (node instanceof JSONObject obj) {
            for (String k : obj.keySet()) {
                Object v = obj.opt(k);
                if (k.equals("collapsedText") && v instanceof String str) obj.put(k, PlainText.literal(str, FOLD_CAP).replace('\n', ' '));
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
