package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Entry;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Handout;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.State;
import com.bloxbean.intelliada.idea.joust.tx.TxCbor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Synthetic transactions for the journal's tests: chosen inputs, validity and publications; no witnesses. */
final class Bodies {

    private Bodies() {
    }

    static String txid(int n) {
        return String.format("%064x", n);
    }

    private static String uint(long v) {
        if (v < 24) return String.format("%02x", v);
        if (v < 0x100) return "18" + String.format("%02x", v);
        if (v < 0x10000) return "19" + String.format("%04x", v);
        if (v < 0x100000000L) return "1a" + String.format("%08x", v);
        return "1b" + String.format("%016x", v);
    }

    private static String inputs(List<String> refs) {
        StringBuilder b = new StringBuilder("d90102").append(arr(refs.size()));
        for (String r : refs) {
            String[] p = r.split("#");
            b.append("82").append("5820").append(p[0]).append(uint(Long.parseLong(p[1])));
        }
        return b.toString();
    }

    private static String arr(int n) {
        return n < 24 ? String.format("%02x", 0x80 + n) : "98" + String.format("%02x", n);
    }

    /**
     * A body spending {@code spends}, with {@code collateral}, {@code references}, the validity interval, and a
     * stake-registration certificate for each of {@code registers} (28-byte hex).
     */
    static String tx(List<String> spends, List<String> collateral, List<String> references, @Nullable Long from, long to,
                     List<String> registers) {
        int keys = 2 + (collateral.isEmpty() ? 0 : 1) + (references.isEmpty() ? 0 : 1) + (from == null ? 0 : 1) + (registers.isEmpty() ? 0 : 1);
        StringBuilder b = new StringBuilder(String.format("%02x", 0xa0 + keys));
        b.append("00").append(inputs(spends));
        b.append("03").append(uint(to));
        if (!registers.isEmpty()) {
            b.append("04").append(arr(registers.size()));
            for (String c : registers) b.append("82").append("00").append("82").append("01").append("581c").append(c);
        }
        if (from != null) b.append("08").append(uint(from));
        if (!collateral.isEmpty()) b.append("0d").append(inputs(collateral));
        if (!references.isEmpty()) b.append("12").append(inputs(references));
        return "84" + b + "a0" + "f5" + "f6";
    }

    static String tx(List<String> spends, long to) {
        return tx(spends, List.of(), List.of(), null, to, List.of());
    }

    static String hash(String cbor) {
        try {
            return TxCbor.transaction(cbor).txHash();
        } catch (TxCbor.Malformed m) {
            throw new AssertionError(m);
        }
    }

    static Entry entry(String reservation, String cbor, Handout handout) {
        try {
            TxCbor.Body b = TxCbor.body(TxCbor.transaction(cbor));
            return new Entry(reservation, b.txHash(), cbor, b.validFrom(), b.validTo(), handout, "node-1", State.prepared, null, null,
                    List.of(), "test " + reservation);
        } catch (TxCbor.Malformed m) {
            throw new AssertionError(m);
        }
    }

    static Entry submitted(String reservation, String cbor) {
        return entry(reservation, cbor, Handout.submitted);
    }
}
