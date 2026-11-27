package com.bloxbean.intelliada.idea.joust.tx;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A strict, structural reader of a Conway transaction's CBOR (ADR-005 D13 rule 1, D17, M3.3b1): it locates the four
 * top-level elements by their exact byte spans and reads, from the body, only what the journal and the signer need
 * (inputs, collateral, reference inputs, validity, required signers, and the components a body publishes). It decodes
 * no Plutus data and re-encodes nothing (I11, I16): every other item is only skipped, with its extent checked.
 * <p>
 * The protocol's accepted encodings are read as the ledger reads them: definite or indefinite lengths, non-minimal
 * integer heads, and sets with or without tag 258 (the kit's {@code accept-indefinite-body},
 * {@code accept-nonminimal-integers}, {@code accept-untagged-sets}). Anything else malformed, a repeated body key,
 * nesting beyond {@link #MAX_DEPTH} or trailing bytes refuses.
 */
public final class TxCbor {

    /** The bytes are not a transaction this reader accepts. */
    public static final class Malformed extends Exception {
        public Malformed(String why) {
            super(why, null, false, false);
        }
    }

    /** Deeper than Plutus data's 256 levels plus the ledger's framing around a redeemer or datum. */
    static final int MAX_DEPTH = 600;
    /** The protocol's frame limit bounds a transaction well below this. */
    static final int MAX_BYTES = 4 * 1024 * 1024;

    /** A half-open byte range {@code [start, end)} of the transaction's bytes. */
    public record Span(int start, int end) {
        public byte[] of(byte[] bytes) {
            return Arrays.copyOfRange(bytes, start, end);
        }

        public int length() {
            return end - start;
        }
    }

    /**
     * The top level: the array header's span (as received), the body, the witness set, {@code is_valid} and the
     * auxiliary data, and, for an indefinite-length array, the break byte's position.
     */
    public record Tx(byte[] bytes, Span header, Span body, Span witnesses, Span isValid, Span auxiliary, int breakAt) {
        public String txHash() {
            return HexFormat.of().formatHex(Blake2bUtil.blake2bHash256(body.of(bytes)));
        }

        public boolean indefinite() {
            return breakAt >= 0;
        }
    }

    /**
     * What the journal and the signer read from a body. References are {@code <txid hex>#<index decimal>};
     * {@code publishes} uses the protocol's prerequisite ids ({@code referenceScript:<hash>} for an output carrying a
     * reference script, {@code stakeRegistration:<hash>} for a pure registration of a script or key credential), as the
     * host derives them from a body.
     */
    public record Body(String txHash, Set<String> inputs, Set<String> collateral, Set<String> referenceInputs,
                       @Nullable Long validFrom, @Nullable Long validTo, List<String> requiredSigners, Set<String> publishes) {
    }

    private TxCbor() {
    }

    public static Tx transaction(byte[] bytes) throws Malformed {
        if (bytes.length == 0) throw new Malformed("an empty transaction");
        if (bytes.length > MAX_BYTES) throw new Malformed("the transaction is too large");
        Reader r = new Reader(bytes);
        Head h = r.head();
        if (h.major != 4) throw new Malformed("a transaction is a CBOR array");
        Span header = new Span(0, r.pos);
        Span[] parts = new Span[4];
        int breakAt = -1;
        for (int i = 0; ; i++) {
            if (h.indefinite) {
                if (r.peekBreak()) {
                    if (i != 4) throw new Malformed("a transaction has four elements");
                    breakAt = r.pos;
                    r.pos++;
                    break;
                }
            } else if (i == h.value) {
                break;
            }
            if (i >= 4) throw new Malformed("a transaction has four elements");
            int start = r.pos;
            r.skip(1);
            parts[i] = new Span(start, r.pos);
        }
        if (!h.indefinite && h.value != 4) throw new Malformed("a transaction has four elements");
        if (r.pos != bytes.length) throw new Malformed("bytes follow the transaction");
        Reader b = new Reader(bytes);
        b.pos = parts[0].start;
        if (b.head().major != 5) throw new Malformed("the transaction body is a CBOR map");
        b.pos = parts[1].start;
        if (b.head().major != 5) throw new Malformed("the witness set is a CBOR map");
        b.pos = parts[2].start;
        Head v = b.head();
        if (v.major != 7 || (v.value != 20 && v.value != 21)) throw new Malformed("is_valid is a boolean");
        return new Tx(bytes, header, parts[0], parts[1], parts[2], parts[3], breakAt);
    }

    public static Tx transaction(String hex) throws Malformed {
        try {
            return transaction(HexFormat.of().parseHex(hex));
        } catch (IllegalArgumentException e) {
            throw new Malformed("the transaction is not hex");
        }
    }

    public static Body body(Tx tx) throws Malformed {
        Reader r = new Reader(tx.bytes());
        r.pos = tx.body().start();
        Head m = r.head();
        Set<String> inputs = null, collateral = Set.of(), references = Set.of(), publishes = new LinkedHashSet<>();
        Long from = null, to = null;
        List<String> signers = List.of();
        Set<Long> seen = new java.util.HashSet<>();
        for (long i = 0; m.indefinite ? !r.peekBreak() : i < m.value; i++) {
            Head k = r.head();
            if (k.major != 0) throw new Malformed("a body key is an unsigned integer");
            if (!seen.add(k.value)) throw new Malformed("a body key repeats");
            switch ((int) Math.min(k.value, Integer.MAX_VALUE)) {
                case 0 -> inputs = refs(r);
                case 1 -> outputs(r, publishes);
                case 3 -> to = r.unsigned();
                case 4 -> certificates(r, publishes);
                case 8 -> from = r.unsigned();
                case 13 -> collateral = refs(r);
                case 14 -> signers = hashes(r, 28);
                case 18 -> references = refs(r);
                default -> r.skip(1);
            }
        }
        if (m.indefinite) r.pos++;
        if (r.pos != tx.body().end()) throw new Malformed("the body's extent differs");
        if (inputs == null || inputs.isEmpty()) throw new Malformed("the body spends no input");
        return new Body(tx.txHash(), inputs, collateral, references, from, to, signers, Set.copyOf(publishes));
    }

    /** A set of inputs: an optional tag 258, then an array of {@code [txid(32), index]}. */
    private static Set<String> refs(Reader r) throws Malformed {
        Head a = r.setHead();
        Set<String> out = new LinkedHashSet<>();
        for (long i = 0; a.indefinite ? !r.peekBreak() : i < a.value; i++) {
            Head in = r.head();
            if (in.major != 4 || (!in.indefinite && in.value != 2)) throw new Malformed("an input is a pair");
            String id = HexFormat.of().formatHex(r.bytes(32));
            long ix = r.unsigned();
            if (ix > 0xFFFF) throw new Malformed("an input index is out of range");
            if (in.indefinite) r.expectBreak();
            if (!out.add(id + "#" + ix)) throw new Malformed("an input repeats");
        }
        if (a.indefinite) r.pos++;
        return Set.copyOf(out);
    }

    private static List<String> hashes(Reader r, int size) throws Malformed {
        Head a = r.setHead();
        List<String> out = new ArrayList<>();
        for (long i = 0; a.indefinite ? !r.peekBreak() : i < a.value; i++) {
            String h = HexFormat.of().formatHex(r.bytes(size));
            if (out.contains(h)) throw new Malformed("a required signer repeats");
            out.add(h);
        }
        if (a.indefinite) r.pos++;
        return List.copyOf(out);
    }

    /** Outputs: only a post-Alonzo output's reference script (key 3) is read; the rest is skipped. */
    private static void outputs(Reader r, Set<String> publishes) throws Malformed {
        Head a = r.head();
        if (a.major != 4) throw new Malformed("the outputs are an array");
        for (long i = 0; a.indefinite ? !r.peekBreak() : i < a.value; i++) {
            Head o = r.head();
            if (o.major == 4) {
                r.skipContents(o, 1);
                continue;
            }
            if (o.major != 5) throw new Malformed("an output is an array or a map");
            for (long j = 0; o.indefinite ? !r.peekBreak() : j < o.value; j++) {
                Head k = r.head();
                if (k.major != 0) throw new Malformed("an output key is an unsigned integer");
                if (k.value == 3) publishes.add("referenceScript:" + scriptRefHash(r));
                else r.skip(1);
            }
            if (o.indefinite) r.pos++;
        }
        if (a.indefinite) r.pos++;
    }

    /**
     * {@code script_ref = #6.24(bytes .cbor script)}, {@code script = [0, native] / [1..3, bytes]}; its hash is
     * Blake2b-224 of the language tag followed by the script's bytes (the native script's encoding for tag 0).
     */
    private static String scriptRefHash(Reader r) throws Malformed {
        Head t = r.head();
        if (t.major != 6 || t.value != 24) throw new Malformed("a reference script is tag 24");
        byte[] inner = r.byteString();
        Reader s = new Reader(inner);
        Head a = s.head();
        if (a.major != 4 || a.indefinite || a.value != 2) throw new Malformed("a script is a pair");
        long lang = s.unsigned();
        byte[] payload;
        if (lang == 0) {
            int start = s.pos;
            s.skip(1);
            payload = Arrays.copyOfRange(inner, start, s.pos);
        } else if (lang >= 1 && lang <= 3) {
            payload = s.byteString();
        } else {
            throw new Malformed("an unknown script language");
        }
        if (s.pos != inner.length) throw new Malformed("bytes follow a reference script");
        byte[] tagged = new byte[payload.length + 1];
        tagged[0] = (byte) lang;
        System.arraycopy(payload, 0, tagged, 1, payload.length);
        return HexFormat.of().formatHex(Blake2bUtil.blake2bHash224(tagged));
    }

    /** Certificates: a pure registration, legacy ({@code [0, cred]}) or Conway ({@code [7, cred, coin]}), is read. */
    private static void certificates(Reader r, Set<String> publishes) throws Malformed {
        Head a = r.setHead();
        for (long i = 0; a.indefinite ? !r.peekBreak() : i < a.value; i++) {
            Head c = r.head();
            if (c.major != 4 || (!c.indefinite && c.value < 1) || (c.indefinite && r.peekBreak())) {
                throw new Malformed("a certificate is a non-empty array");
            }
            int start = r.pos;
            Head kind = r.head();
            if (kind.major == 0 && (kind.value == 0 || kind.value == 7)) {
                if (!c.indefinite && c.value != (kind.value == 0 ? 2 : 3)) throw new Malformed("a registration has the wrong arity");
                Head cred = r.head();
                if (cred.major != 4 || cred.indefinite || cred.value != 2) throw new Malformed("a credential is a pair");
                long ct = r.unsigned();
                if (ct > 1) throw new Malformed("an unknown credential kind");
                publishes.add("stakeRegistration:" + HexFormat.of().formatHex(r.bytes(28)));
                if (kind.value == 7) r.unsigned();
                if (c.indefinite) r.expectBreak();
            } else {
                r.pos = start;
                r.skipContents(c, 1);
            }
        }
        if (a.indefinite) r.pos++;
    }

    // ---- the reader ---------------------------------------------------------------------------------------------------

    private record Head(int major, long value, boolean indefinite) {
    }

    private static final class Reader {
        final byte[] b;
        int pos;

        Reader(byte[] b) {
            this.b = b;
        }

        int u8() throws Malformed {
            if (pos >= b.length) throw new Malformed("the transaction ends early");
            return b[pos++] & 0xff;
        }

        boolean peekBreak() throws Malformed {
            if (pos >= b.length) throw new Malformed("the transaction ends early");
            return (b[pos] & 0xff) == 0xff;
        }

        void expectBreak() throws Malformed {
            if (u8() != 0xff) throw new Malformed("an indefinite item is not closed");
        }

        /** One head; non-minimal encodings of the argument are accepted. */
        Head head() throws Malformed {
            int ib = u8();
            int major = ib >>> 5, ai = ib & 31;
            if (ai < 24) return new Head(major, ai, false);
            if (ai == 31) {
                if (major == 0 || major == 1 || major == 6) throw new Malformed("an indefinite length where none is allowed");
                if (major == 7) throw new Malformed("an unexpected break");
                return new Head(major, -1, true);
            }
            if (ai > 27) throw new Malformed("a reserved additional information value");
            int n = 1 << (ai - 24);
            long v = 0;
            for (int i = 0; i < n; i++) v = (v << 8) | u8();
            if (major == 7) return new Head(major, ai == 24 ? v : -2 - ai, false);   // floats: an opaque marker
            return new Head(major, v, false);
        }

        /** A set's array, with or without tag 258. */
        Head setHead() throws Malformed {
            Head h = head();
            if (h.major == 6) {
                if (h.value != 258) throw new Malformed("a set's tag is 258");
                h = head();
            }
            if (h.major != 4) throw new Malformed("a set is an array");
            return h;
        }

        long unsigned() throws Malformed {
            Head h = head();
            if (h.major != 0) throw new Malformed("an unsigned integer was expected");
            if (h.value < 0) throw new Malformed("an integer is out of range");
            return h.value;
        }

        /** A definite or indefinite byte string's content. */
        byte[] byteString() throws Malformed {
            Head h = head();
            if (h.major != 2) throw new Malformed("a byte string was expected");
            if (!h.indefinite) return take(h.value);
            var out = new java.io.ByteArrayOutputStream();
            while (!peekBreak()) {
                Head c = head();
                if (c.major != 2 || c.indefinite) throw new Malformed("a byte string chunk was expected");
                out.writeBytes(take(c.value));
            }
            pos++;
            return out.toByteArray();
        }

        byte[] bytes(int size) throws Malformed {
            byte[] v = byteString();
            if (v.length != size) throw new Malformed("a " + size + "-byte string was expected");
            return v;
        }

        byte[] take(long n) throws Malformed {
            if (n < 0 || n > b.length - pos) throw new Malformed("the transaction ends early");
            byte[] v = Arrays.copyOfRange(b, pos, pos + (int) n);
            pos += (int) n;
            return v;
        }

        /** Skips one complete item. */
        void skip(int depth) throws Malformed {
            skipContents(head(), depth);
        }

        void skipContents(Head h, int depth) throws Malformed {
            if (depth > MAX_DEPTH) throw new Malformed("the transaction nests too deeply");
            switch (h.major) {
                case 0, 1 -> {
                }
                case 2, 3 -> {
                    if (!h.indefinite) {
                        take(h.value);
                    } else {
                        while (!peekBreak()) {
                            Head c = head();
                            if (c.major != h.major || c.indefinite) throw new Malformed("a string chunk of another type");
                            take(c.value);
                        }
                        pos++;
                    }
                }
                case 4, 5 -> {
                    int per = h.major == 5 ? 2 : 1;
                    if (h.indefinite) {
                        while (!peekBreak()) for (int i = 0; i < per; i++) skip(depth + 1);
                        pos++;
                    } else {
                        if (h.value < 0 || h.value > (b.length - pos)) throw new Malformed("the transaction ends early");
                        for (long i = 0; i < h.value; i++) for (int j = 0; j < per; j++) skip(depth + 1);
                    }
                }
                case 6 -> skip(depth + 1);
                case 7 -> {
                }
                default -> throw new Malformed("an unknown major type");
            }
        }
    }
}
