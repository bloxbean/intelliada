package com.bloxbean.intelliada.idea.joust.host;

import com.bloxbean.intelliada.idea.joust.core.PlainText;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The host's stderr, kept to its last {@code cap} bytes (ADR-005 D11: a capped console). Shown as plain text only: control
 * and ANSI sequences are removed when read (I15).
 */
public final class HostStderr {

    private final int cap;
    private final byte[] ring;
    private long written;
    private final List<Consumer<String>> listeners = new ArrayList<>();

    public HostStderr(int cap) {
        this.cap = cap;
        this.ring = new byte[cap];
    }

    private final java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE);
    private java.nio.ByteBuffer carry = java.nio.ByteBuffer.allocate(0);

    void append(byte[] b, int n) {
        String s;
        List<Consumer<String>> ls;
        synchronized (this) {
            for (int i = 0; i < n; i++) ring[(int) ((written + i) % cap)] = b[i];
            written += n;
            if (listeners.isEmpty()) return;
            // A multibyte character split across reads is decoded once its bytes arrive (M31A-DES-11).
            java.nio.ByteBuffer in = java.nio.ByteBuffer.allocate(carry.remaining() + n);
            in.put(carry).put(b, 0, n).flip();
            java.nio.CharBuffer out = java.nio.CharBuffer.allocate(in.remaining() + 1);
            decoder.decode(in, out, false);
            carry = in.slice();
            s = PlainText.clean(out.flip().toString(), n + 16);
            ls = List.copyOf(listeners);
        }
        // Listeners run outside the lock, so a slow console cannot stall the drain of the host's stderr.
        for (Consumer<String> l : ls) l.accept(s);
    }

    public synchronized void listen(Consumer<String> l) {
        listeners.add(l);
    }

    /** The retained text (the last {@code cap} bytes), as plain text. */
    public synchronized String text() {
        int n = (int) Math.min(written, cap);
        byte[] out = new byte[n];
        long start = written - n;
        for (int i = 0; i < n; i++) out[i] = ring[(int) ((start + i) % cap)];
        return PlainText.clean(new String(out, StandardCharsets.UTF_8), cap + 16);
    }

    /** The last nonempty line, for a status message. */
    public String lastLine() {
        String t = text().strip();
        int i = t.lastIndexOf('\n');
        return i < 0 ? t : t.substring(i + 1);
    }
}
