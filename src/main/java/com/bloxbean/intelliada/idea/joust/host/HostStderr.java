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

    synchronized void append(byte[] b, int n) {
        for (int i = 0; i < n; i++) ring[(int) ((written + i) % cap)] = b[i];
        written += n;
        if (!listeners.isEmpty()) {
            String s = PlainText.clean(new String(b, 0, n, StandardCharsets.UTF_8), n + 16);
            for (Consumer<String> l : listeners) l.accept(s);
        }
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
