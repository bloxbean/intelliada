package com.bloxbean.intelliada.idea.joust.tx;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * ADR-005 D13 "Signing" (M3.3b2): builds a signed transaction by splicing, never by re-encoding. The result is the
 * received array header, the body bytes as received, a witness set made of a new vkey-witness entry (key 0) followed by
 * the received witness set's entries as raw bytes, and the remaining elements as received. The body, the redeemers and
 * the datums are never re-encoded, so the transaction's id is the one the host built.
 */
public final class TxSplice {

    /** One vkey witness: the 32-byte verification key and its 64-byte Ed25519 signature of the body hash. */
    public record VkeyWitness(byte[] vkey, byte[] signature) {
        public VkeyWitness {
            if (vkey.length != 32) throw new IllegalArgumentException("a verification key is 32 bytes");
            if (signature.length != 64) throw new IllegalArgumentException("a signature is 64 bytes");
        }
    }

    private TxSplice() {
    }

    /**
     * The transaction with {@code witnesses} added. A received witness set that already holds vkey witnesses (key 0) is
     * refused: the host never adds them, and merging imported ones is external signing's (M3.5).
     */
    public static byte[] splice(TxCbor.Tx tx, List<VkeyWitness> witnesses) throws TxCbor.Malformed {
        if (witnesses.isEmpty()) throw new IllegalArgumentException("nothing to splice");
        List<TxCbor.WitnessEntry> received = TxCbor.witnessEntries(tx);
        for (TxCbor.WitnessEntry e : received) {
            if (e.key() == 0) throw new TxCbor.Malformed("the received witness set already holds vkey witnesses");
        }
        byte[] b = tx.bytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length + 128 * witnesses.size());
        out.write(b, tx.header().start(), tx.header().length());
        out.write(b, tx.body().start(), tx.body().length());
        head(out, 5, received.size() + 1L);
        head(out, 0, 0);
        head(out, 4, witnesses.size());
        for (VkeyWitness w : witnesses) {
            head(out, 4, 2);
            head(out, 2, 32);
            out.writeBytes(w.vkey());
            head(out, 2, 64);
            out.writeBytes(w.signature());
        }
        for (TxCbor.WitnessEntry e : received) out.write(b, e.span().start(), e.span().length());
        out.write(b, tx.isValid().start(), tx.isValid().length());
        out.write(b, tx.auxiliary().start(), tx.auxiliary().length());
        if (tx.indefinite()) out.write(0xff);
        return out.toByteArray();
    }

    /** A CBOR head of {@code major} type with argument {@code value}, in its shortest form. */
    private static void head(ByteArrayOutputStream out, int major, long value) {
        int m = major << 5;
        if (value < 24) {
            out.write(m | (int) value);
        } else if (value < 0x100) {
            out.write(m | 24);
            out.write((int) value);
        } else if (value < 0x10000) {
            out.write(m | 25);
            out.write((int) (value >> 8));
            out.write((int) value & 0xff);
        } else {
            out.write(m | 26);
            for (int s = 24; s >= 0; s -= 8) out.write((int) (value >> s) & 0xff);
        }
    }
}
