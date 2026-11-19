package com.bloxbean.intelliada.idea.joust.lsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A strict JSON reader and writer for the frame filter (ADR-004 §12 E3; M1-CDX-22, M1-CDX-27): exactly RFC 8259, with
 * budgets enforced <b>while</b> parsing.
 *
 * <ul>
 *   <li><b>Grammar:</b> double-quoted strings with valid escapes and no raw control characters; numbers per the RFC;
 *   {@code true}, {@code false}, {@code null}; no trailing commas, no comments, no single quotes, no unquoted names,
 *   nothing after the value. Duplicate member names are refused.</li>
 *   <li><b>Budgets:</b> nesting depth, the number of containers and the number of values are counted as they are read,
 *   so a deep or wide frame is refused before it can allocate a large tree.</li>
 * </ul>
 * Values are read into {@link LinkedHashMap}, {@link ArrayList}, {@link String}, {@link Num} (the number's own text),
 * {@link Boolean} and {@link #NULL}, and written back as canonical JSON.
 */
final class JoustStrictJson {

    /** JSON {@code null}. */
    static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    /** A number, kept as its validated text so nothing is rounded. */
    record Num(String text) {
    }

    /** A refusal: the frame is not acceptable. */
    static final class Refused extends Exception {
        Refused(String why) {
            super(why, null, false, false);
        }
    }

    /** The limits one parse may use. */
    record Budget(int maxDepth, int maxContainers, int maxValues) {
    }

    static final Budget DEFAULT = new Budget(64, 100_000, 2_000_000);

    private final String s;
    private final Budget budget;
    private int i;
    private int containers;
    private int values;

    private JoustStrictJson(String s, Budget budget) {
        this.s = s;
        this.budget = budget;
    }

    /** Parses one JSON value that is the whole text, within the budget, or throws. */
    static Object parse(String text, Budget budget) throws Refused {
        JoustStrictJson p = new JoustStrictJson(text, budget);
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.i != p.s.length()) throw new Refused("trailing input");
        return v;
    }

    private Object value(int depth) throws Refused {
        if (++values > budget.maxValues()) throw new Refused("too many values");
        if (i >= s.length()) throw new Refused("unexpected end");
        char c = s.charAt(i);
        switch (c) {
            case '{':
                return object(depth + 1);
            case '[':
                return array(depth + 1);
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", NULL);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return number();
                throw new Refused("unexpected character");
        }
    }

    private Map<String, Object> object(int depth) throws Refused {
        if (depth > budget.maxDepth()) throw new Refused("too deep");
        if (++containers > budget.maxContainers()) throw new Refused("too many containers");
        i++;
        Map<String, Object> m = new LinkedHashMap<>();
        ws();
        if (peek() == '}') {
            i++;
            return m;
        }
        while (true) {
            ws();
            if (peek() != '"') throw new Refused("member name must be a string");
            String k = string();
            ws();
            if (peek() != ':') throw new Refused("expected ':'");
            i++;
            ws();
            Object v = value(depth);
            if (m.containsKey(k)) throw new Refused("duplicate member");
            m.put(k, v);
            ws();
            char c = peek();
            i++;
            if (c == '}') return m;
            if (c != ',') throw new Refused("expected ',' or '}'");
            ws();
            if (peek() == '}') throw new Refused("trailing comma");
        }
    }

    private List<Object> array(int depth) throws Refused {
        if (depth > budget.maxDepth()) throw new Refused("too deep");
        if (++containers > budget.maxContainers()) throw new Refused("too many containers");
        i++;
        List<Object> a = new ArrayList<>();
        ws();
        if (peek() == ']') {
            i++;
            return a;
        }
        while (true) {
            ws();
            a.add(value(depth));
            ws();
            char c = peek();
            i++;
            if (c == ']') return a;
            if (c != ',') throw new Refused("expected ',' or ']'");
            ws();
            if (peek() == ']') throw new Refused("trailing comma");
        }
    }

    private String string() throws Refused {
        i++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw new Refused("unterminated string");
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c < 0x20) throw new Refused("control character in a string");
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i >= s.length()) throw new Refused("unterminated escape");
            char e = s.charAt(i++);
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (i + 4 > s.length()) throw new Refused("short unicode escape");
                    int cp = 0;
                    for (int k = 0; k < 4; k++) {
                        int d = hex(s.charAt(i + k));   // ASCII HEXDIG only (RFC 8259 §7; M1-CDX-22)
                        if (d < 0) throw new Refused("bad unicode escape");
                        cp = cp * 16 + d;
                    }
                    i += 4;
                    sb.append((char) cp);
                }
                default -> throw new Refused("bad escape");
            }
        }
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private Num number() throws Refused {
        int start = i;
        if (peek() == '-') i++;
        if (peek() == '0') {
            i++;
        } else if (peek() >= '1' && peek() <= '9') {
            while (peek() >= '0' && peek() <= '9') i++;
        } else {
            throw new Refused("bad number");
        }
        if (peek() == '.') {
            i++;
            if (!(peek() >= '0' && peek() <= '9')) throw new Refused("bad fraction");
            while (peek() >= '0' && peek() <= '9') i++;
        }
        if (peek() == 'e' || peek() == 'E') {
            i++;
            if (peek() == '+' || peek() == '-') i++;
            if (!(peek() >= '0' && peek() <= '9')) throw new Refused("bad exponent");
            while (peek() >= '0' && peek() <= '9') i++;
        }
        if (i - start > 100) throw new Refused("number too long");
        return new Num(s.substring(start, i));
    }

    private Object literal(String word, Object v) throws Refused {
        if (!s.startsWith(word, i)) throw new Refused("bad literal");
        i += word.length();
        return v;
    }

    private char peek() {
        return i < s.length() ? s.charAt(i) : '\0';
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return;
            i++;
        }
    }

    // ------------------------------------------------------------------ writing

    /** Canonical JSON for a value read by {@link #parse} (unbounded; tests and small values). */
    static String write(Object v) {
        try {
            return new String(writeUtf8(v, Integer.MAX_VALUE - 16), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Refused e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Canonical JSON encoded straight to UTF-8, with every byte charged against {@code maxBytes} as it is emitted: the
     * first write that would exceed the budget stops serialisation, before any later member is visited and before a
     * larger buffer is allocated (M1-CDX-31). Valid surrogate pairs are written as themselves (four bytes).
     */
    static byte[] writeUtf8(Object v, int maxBytes) throws Refused {
        Out out = new Out(maxBytes);
        write(v, out);
        return out.bytes();
    }

    /** A growable byte buffer that never grows past its budget. */
    static final class Out {
        private final int max;
        private byte[] buf = new byte[256];
        private int n;

        Out(int max) {
            this.max = max;
        }

        private void ensure(int more) throws Refused {
            if ((long) n + more > max) throw new Refused("rewritten frame too large");
            if (n + more > buf.length) {
                long want = Math.min((long) max, Math.max((long) buf.length * 2, (long) n + more));
                buf = java.util.Arrays.copyOf(buf, (int) want);
            }
        }

        void ascii(char c) throws Refused {
            ensure(1);
            buf[n++] = (byte) c;
        }

        void ascii(String s) throws Refused {
            ensure(s.length());
            for (int k = 0; k < s.length(); k++) buf[n++] = (byte) s.charAt(k);
        }

        void codePoint(int cp) throws Refused {
            if (cp < 0x80) {
                ensure(1);
                buf[n++] = (byte) cp;
            } else if (cp < 0x800) {
                ensure(2);
                buf[n++] = (byte) (0xC0 | (cp >> 6));
                buf[n++] = (byte) (0x80 | (cp & 0x3F));
            } else if (cp < 0x10000) {
                ensure(3);
                buf[n++] = (byte) (0xE0 | (cp >> 12));
                buf[n++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
                buf[n++] = (byte) (0x80 | (cp & 0x3F));
            } else {
                ensure(4);
                buf[n++] = (byte) (0xF0 | (cp >> 18));
                buf[n++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
                buf[n++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
                buf[n++] = (byte) (0x80 | (cp & 0x3F));
            }
        }

        byte[] bytes() {
            return java.util.Arrays.copyOf(buf, n);
        }
    }

    @SuppressWarnings("unchecked")
    private static void write(Object v, Out out) throws Refused {
        if (v == NULL || v == null) {
            out.ascii("null");
        } else if (v instanceof Boolean b) {
            out.ascii(b ? "true" : "false");
        } else if (v instanceof Num num) {
            out.ascii(num.text());
        } else if (v instanceof String str) {
            quote(str, out);
        } else if (v instanceof Map<?, ?> m) {
            out.ascii('{');
            boolean first = true;
            for (var e : ((Map<String, Object>) m).entrySet()) {
                if (!first) out.ascii(',');
                first = false;
                quote(e.getKey(), out);
                out.ascii(':');
                write(e.getValue(), out);
            }
            out.ascii('}');
        } else if (v instanceof List<?> a) {
            out.ascii('[');
            for (int k = 0; k < a.size(); k++) {
                if (k > 0) out.ascii(',');
                write(a.get(k), out);
            }
            out.ascii(']');
        } else {
            throw new IllegalArgumentException("not a JSON value: " + v.getClass());
        }
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static void quote(String s, Out out) throws Refused {
        out.ascii('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> out.ascii("\\\"");
                case '\\' -> out.ascii("\\\\");
                case '\n' -> out.ascii("\\n");
                case '\r' -> out.ascii("\\r");
                case '\t' -> out.ascii("\\t");
                case '\b' -> out.ascii("\\b");
                case '\f' -> out.ascii("\\f");
                default -> {
                    if (Character.isHighSurrogate(c) && k + 1 < s.length() && Character.isLowSurrogate(s.charAt(k + 1))) {
                        out.codePoint(Character.toCodePoint(c, s.charAt(++k)));   // a valid pair: written as itself
                    } else if (c < 0x20 || Character.isSurrogate(c)) {
                        out.ascii("\\u");
                        out.ascii(HEX[(c >> 12) & 15]);
                        out.ascii(HEX[(c >> 8) & 15]);
                        out.ascii(HEX[(c >> 4) & 15]);
                        out.ascii(HEX[c & 15]);
                    } else {
                        out.codePoint(c);
                    }
                }
            }
        }
        out.ascii('"');
    }
}
