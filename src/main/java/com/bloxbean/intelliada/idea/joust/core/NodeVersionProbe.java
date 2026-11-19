package com.bloxbean.intelliada.idea.joust.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The one place IntelliAda executes Node outside the language server: {@code <node> --version}, on the user's binary by
 * its validated absolute real path, with the IDE's environment (ADR-004 D4).
 *
 * <p>It is called <b>only</b> from the explicit configuration actions (Save, Detect…), never on open, at server start or
 * on a file event (I1, CDX-25). {@link #invocations()} lets tests assert that.
 */
public final class NodeVersionProbe {

    private static final AtomicInteger INVOCATIONS = new AtomicInteger();
    private static final int OUTPUT_CAP = 256;

    private NodeVersionProbe() {
    }

    /** Runs {@code <realNode> --version} with a 10 s timeout; returns its trimmed first line, or throws. */
    public static String probe(Path realNode) throws IOException {
        INVOCATIONS.incrementAndGet();
        ProcessBuilder pb = new ProcessBuilder(List.of(realNode.toString(), "--version"));
        pb.redirectErrorStream(true);
        Path dir = realNode.getParent();
        if (dir != null) pb.directory(dir.toFile());
        Process p = pb.start();
        try (InputStream in = p.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64];
            int n;
            while (out.size() < OUTPUT_CAP && (n = in.read(buf)) > 0) out.write(buf, 0, Math.min(n, OUTPUT_CAP - out.size()));
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("node --version did not finish in 10 s");
            }
            String text = out.toString(StandardCharsets.UTF_8).trim();
            int nl = text.indexOf('\n');
            return nl < 0 ? text : text.substring(0, nl).trim();
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    public static int invocations() {
        return INVOCATIONS.get();
    }
}
