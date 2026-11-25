package com.bloxbean.intelliada.idea.joust.host;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * ADR-005 D11a / JOUST dev-host protocol 1, "Launch and framing": {@code Content-Length: N\r\n\r\n} then N bytes of UTF-8
 * JSON. The header (with its delimiter) is at most {@link #MAX_HEADER} bytes, the body at most {@link #MAX_BODY}, and a
 * whole frame must arrive within {@link #STALL_NANOS} of its first byte. {@code Content-Length} occurs exactly once;
 * {@code Content-Type} is the only other field allowed. Anything else is a {@link Violation}: the session ends (I22).
 */
public final class HostFrames {

    private HostFrames() {
    }

    public static final int MAX_BODY = 4 * 1024 * 1024;
    public static final int MAX_HEADER = 1024;
    public static final long STALL_NANOS = 10_000_000_000L;

    /** A transport fault of the peer: the client stops the host and discards any in-flight result. */
    public static final class Violation extends Exception {
        public Violation(String message) {
            super(message, null, false, false);
        }
    }

    /**
     * Reads frames. {@link #startedAt()} is the monotonic time the current frame's first byte arrived, or 0 between
     * frames, so a watchdog can end a session whose frame stalls even while this reader is blocked.
     */
    public static final class Reader {
        private final InputStream in;
        private volatile long startedAt;

        public Reader(InputStream in) {
            this.in = in;
        }

        public long startedAt() {
            return startedAt;
        }

        /** The next frame's body, or null at end of input between frames. */
        public byte[] next() throws IOException, Violation {
            startedAt = 0;
            int first = in.read();
            if (first < 0) return null;
            startedAt = System.nanoTime() | 1;
            byte[] header = new byte[MAX_HEADER];
            int n = 0;
            header[n++] = (byte) first;
            while (!(n >= 4 && header[n - 4] == '\r' && header[n - 3] == '\n' && header[n - 2] == '\r' && header[n - 1] == '\n')) {
                if (n == MAX_HEADER) throw new Violation("frame header longer than " + MAX_HEADER + " bytes");
                int b = in.read();
                if (b < 0) throw new Violation("end of input inside a frame header");
                header[n++] = (byte) b;
            }
            long length = contentLength(new String(header, 0, n - 4, StandardCharsets.US_ASCII));
            if (length > MAX_BODY) throw new Violation("frame body of " + length + " bytes exceeds the " + MAX_BODY + "-byte cap");
            byte[] body = in.readNBytes((int) length);
            if (body.length != length) throw new Violation("end of input inside a frame body");
            if (System.nanoTime() - startedAt >= STALL_NANOS) throw new Violation("stalled frame");
            startedAt = 0;
            return body;
        }

        static long contentLength(String header) throws Violation {
            long length = -1;
            for (String line : header.split("\r\n", -1)) {
                for (int k = 0; k < line.length(); k++) {
                    char c = line.charAt(k);
                    if (c < 0x20 || c > 0x7e) throw new Violation("non-ASCII byte in a frame header");
                }
                int colon = line.indexOf(':');
                if (colon <= 0) throw new Violation("malformed frame header line");
                String name = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (name.equalsIgnoreCase("Content-Length")) {
                    if (length >= 0) throw new Violation("two Content-Length headers");
                    if (!value.matches("[0-9]{1,10}")) throw new Violation("malformed Content-Length");
                    length = Long.parseLong(value);
                } else if (!name.equalsIgnoreCase("Content-Type")) {
                    throw new Violation("unexpected frame header field");
                }
            }
            if (length < 0) throw new Violation("frame header without Content-Length");
            return length;
        }
    }

    /** Writes whole frames, one at a time; {@link #writingSince()} lets a watchdog see a write the host stopped reading. */
    public static final class Writer {
        private final OutputStream out;
        private volatile long writingSince;

        public Writer(OutputStream out) {
            this.out = out;
        }

        public long writingSince() {
            return writingSince;
        }

        public synchronized void write(byte[] body) throws IOException {
            if (body.length > MAX_BODY) throw new IOException("frame body exceeds the cap");
            byte[] head = ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            byte[] frame = new byte[head.length + body.length];
            System.arraycopy(head, 0, frame, 0, head.length);
            System.arraycopy(body, 0, frame, head.length, body.length);
            writingSince = System.nanoTime() | 1;
            try {
                out.write(frame);
                out.flush();
            } finally {
                writingSince = 0;
            }
        }

        public void close() {
            try {
                out.close();
            } catch (IOException ignored) {
                // the process is going away
            }
        }
    }
}
