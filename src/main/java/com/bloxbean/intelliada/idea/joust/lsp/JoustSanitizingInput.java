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
 * <p>Frames are passed through byte for byte unless their body has a top-level {@code error} object with a string
 * {@code message}; only those are re-serialised. A frame over {@link #MAX_BODY} bytes, or a malformed header, ends the
 * stream: the connection is closed rather than parsed past.
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
        String head = header.toString(StandardCharsets.US_ASCII);
        int length = -1;
        for (String line : head.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().toLowerCase(Locale.ROOT).equals("content-length")) {
                try {
                    length = Integer.parseInt(line.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        if (length < 0 || length > MAX_BODY) return null;
        byte[] body = in.readNBytes(length);
        if (body.length < length) return null;
        byte[] out = sanitize(body);
        if (out == body) return concat(header.toByteArray(), body);
        return concat(("Content-Length: " + out.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII), out);
    }

    /** The body with its error message escaped and capped, or the same array when there is none. */
    static byte[] sanitize(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8);
        if (!text.contains("\"error\"")) return body;
        JSONObject o;
        try {
            o = new JSONObject(text);
        } catch (RuntimeException e) {
            return body;
        }
        JSONObject error = o.optJSONObject("error");
        if (error == null || !(error.opt("message") instanceof String m)) return body;
        error.put("message", PlainText.escape(PlainText.clean(m, ERROR_CAP)));
        error.remove("data");
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
