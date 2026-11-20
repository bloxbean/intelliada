package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.core.PlainText;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
     *   <li>every {@code collapsedText} (folding placeholders): cleaned and capped as literal text;</li>
     *   <li>the {@code initialize} result (a {@code result} with a {@code capabilities} object): the capabilities cut to
     *   {@link #SERVER_CAPABILITIES}, {@code willSaveWaitUntil} removed from the sync options, and {@code serverInfo}
     *   made a plain title (ADR-004 §12.2 E23). This runs before LSP4J parses the frame, so it holds whatever LSP4J
     *   version LSP4IJ brings and whatever objects LSP4IJ caches from the result.</li>
     * </ul>
     */
    static byte[] sanitize(byte[] body) {
        return sanitize(body, JoustStrictJson.DEFAULT, MAX_BODY);
    }

    static byte[] sanitize(byte[] body, JoustStrictJson.Budget budget) {
        return sanitize(body, budget, MAX_BODY);
    }

    /** As {@link #sanitize(byte[])}, with the rewritten body bounded by {@code maxOut} bytes (M1-CDX-31). */
    @SuppressWarnings("unchecked")
    static byte[] sanitize(byte[] body, JoustStrictJson.Budget budget, int maxOut) {
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
        // One strict RFC 8259 object, nothing after it, read within the depth, container and value budgets
        // (M1-CDX-22, M1-CDX-27): trailing commas, single quotes, unquoted names, NUL, duplicate members, deep or
        // very wide frames all end the stream before a large tree exists.
        Map<String, Object> o;
        try {
            Object v = JoustStrictJson.parse(text, budget);
            if (!(v instanceof Map<?, ?> m)) return null;
            o = (Map<String, Object>) m;
        } catch (JoustStrictJson.Refused e) {
            return null;
        }
        Object error = o.get("error");
        if (error instanceof Map<?, ?> em) {
            Map<String, Object> e = (Map<String, Object>) em;
            Object m = e.get("message");
            e.put("message", PlainText.escape(PlainText.clean(m instanceof String str ? str : "", ERROR_CAP)));
            e.remove("data");
        } else if (error == JoustStrictJson.NULL) {
            o.remove("error");   // "error": null is no error (M1-SEC-7)
        } else if (o.containsKey("error")) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("code", new JoustStrictJson.Num("-32603"));
            e.put("message", "error");
            o.put("error", e);
        }
        Object method = o.get("method");
        if (("window/logMessage".equals(method) || "window/showMessage".equals(method) || "window/showMessageRequest".equals(method))
                && o.get("params") instanceof Map<?, ?> pm && pm.get("message") instanceof String m) {
            // r9 E6: literal text; an <html> start is defused (M1-CDX-13).
            ((Map<String, Object>) pm).put("message", PlainText.literal(m, MESSAGE_CAP));
        }
        literalCollapsedText(o, 0);
        if (o.get("result") instanceof Map<?, ?> rm && rm.get("capabilities") instanceof Map<?, ?>) {
            readOnlyInitializeResult((Map<String, Object>) rm);
        }
        try {
            return JoustStrictJson.writeUtf8(o, maxOut);
        } catch (JoustStrictJson.Refused e) {
            return null;
        }
    }

    static final int MESSAGE_CAP = 4_000;
    static final int SERVER_INFO_CAP = 200;

    /**
     * ADR-004 §12.2 E23: the server capabilities IntelliAda lets LSP4IJ see, an allow-list of the read-only set R. A
     * capability a later LSP or LSP4IJ adds is dropped until it is classified. Not listed, so dropped: every editing
     * provider, pull diagnostics ({@code diagnosticProvider}: LSP4IJ 0.30.1 resolves a report's related-document URIs
     * before checking them, M1-CDX-24), {@code workspace} (folder-change notifications, file operations, virtual
     * documents), inline values and completion, linked editing, colour, code lens, links, hierarchies, notebooks and
     * {@code experimental}. joust-lsp 0.1.0-pre2 advertises only allowed ones to IntelliAda.
     */
    static final java.util.Set<String> SERVER_CAPABILITIES = java.util.Set.of(
            "positionEncoding", "textDocumentSync",
            "hoverProvider", "definitionProvider", "declarationProvider", "typeDefinitionProvider", "implementationProvider",
            "referencesProvider", "workspaceSymbolProvider",
            "documentHighlightProvider", "documentSymbolProvider", "foldingRangeProvider", "selectionRangeProvider",
            "semanticTokensProvider");

    /** The sync options IntelliAda keeps: no {@code willSaveWaitUntil}, whose edits LSP4IJ 0.30.1 applies on save. */
    static final java.util.Set<String> SYNC_OPTIONS = java.util.Set.of("openClose", "change", "willSave", "save");

    @SuppressWarnings("unchecked")
    private static void readOnlyInitializeResult(Map<String, Object> result) {
        Map<String, Object> caps = (Map<String, Object>) result.get("capabilities");
        caps.keySet().retainAll(SERVER_CAPABILITIES);
        if (caps.get("textDocumentSync") instanceof Map<?, ?> sync) ((Map<String, Object>) sync).keySet().retainAll(SYNC_OPTIONS);
        Object info = result.get("serverInfo");
        if (info instanceof Map<?, ?> im && im.get("name") instanceof String name) {
            Map<String, Object> clean = new LinkedHashMap<>();
            clean.put("name", PlainText.title(name, SERVER_INFO_CAP));
            if (im.get("version") instanceof String version) clean.put("version", PlainText.title(version, SERVER_INFO_CAP));
            result.put("serverInfo", clean);
        } else {
            result.remove("serverInfo");
        }
    }
    static final int FOLD_CAP = 200;

    @SuppressWarnings("unchecked")
    private static void literalCollapsedText(Object node, int depth) {
        if (depth > 70) return;
        if (node instanceof Map<?, ?> obj) {
            for (var e : ((Map<String, Object>) obj).entrySet()) {
                if (e.getKey().equals("collapsedText") && e.getValue() instanceof String str) {
                    e.setValue(PlainText.literal(str, FOLD_CAP).replace('\n', ' '));
                } else {
                    literalCollapsedText(e.getValue(), depth + 1);
                }
            }
        } else if (node instanceof List<?> arr) {
            for (Object x : arr) literalCollapsedText(x, depth + 1);
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
