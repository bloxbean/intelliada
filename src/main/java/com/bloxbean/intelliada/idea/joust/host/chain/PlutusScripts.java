package com.bloxbean.intelliada.idea.joust.host.chain;

import java.util.HexFormat;
import java.util.Locale;

/**
 * A Plutus script's CBOR as the protocol's {@code script} read answers it: double-wrapped (a CBOR byte string holding the
 * flat script's CBOR byte string), as cardano-client-lib's {@code PlutusScript} carries it, whichever form the provider
 * served (playground {@code plutusScriptOf}).
 */
public final class PlutusScripts {

    private PlutusScripts() {
    }

    /** {@code hex} if it is already a byte string holding a byte string, else {@code hex} wrapped once more. */
    public static String doubleWrapped(String hex) {
        String h = hex.toLowerCase(Locale.ROOT);
        if (!h.matches("(?:[0-9a-f]{2})+")) throw new IllegalArgumentException("not hex");
        String inner = unwrap(h);
        return inner != null && unwrap(inner) != null ? h : wrap(h);
    }

    /** The payload of exactly one definite CBOR byte string, or null. */
    public static String unwrap(String hex) {
        int head = Integer.parseInt(hex.substring(0, 2), 16);
        if (head < 0x40 || head > 0x5b) return null;
        int extra = head & 0x1f;
        int width = extra < 24 ? 0 : extra == 24 ? 1 : extra == 25 ? 2 : extra == 26 ? 4 : 8;
        if (hex.length() < 2 + width * 2) return null;
        long len = width == 0 ? extra : Long.parseUnsignedLong(hex.substring(2, 2 + width * 2), 16);
        String body = hex.substring(2 + width * 2);
        return body.length() == len * 2 ? body : null;
    }

    static String wrap(String hex) {
        int n = hex.length() / 2;
        String head = n < 24 ? HexFormat.of().toHexDigits((byte) (0x40 + n))
                : n < 0x100 ? "58" + HexFormat.of().toHexDigits((byte) n)
                : n < 0x10000 ? "59" + String.format("%04x", n)
                : "5a" + String.format("%08x", n);
        return head + hex;
    }
}
