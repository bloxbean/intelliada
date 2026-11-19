package com.bloxbean.intelliada.idea.joust.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The one place IntelliAda executes Node outside the language server: {@code <node> --version}, on the user's binary by
 * its validated absolute real path, with the IDE's environment (ADR-004 D4).
 *
 * <p>It is called <b>only</b> from the explicit configuration actions (Save, Detect…), never on open, at server start or
 * on a file event (I1, CDX-25). {@link #invocations()} lets tests assert that. One deadline bounds the whole probe: the
 * output is drained concurrently and capped, the process is destroyed on timeout or interruption, and a non-zero exit
 * is a failure (M1-CDX-16).
 */
public final class NodeVersionProbe {

    private static final AtomicInteger INVOCATIONS = new AtomicInteger();
    static final int OUTPUT_CAP = 256;
    static final long TIMEOUT_MS = 10_000;

    private NodeVersionProbe() {
    }

    /** Runs {@code <realNode> --version}; returns its trimmed first line, or throws. */
    public static String probe(Path realNode) throws IOException {
        return probe(List.of(realNode.toString(), "--version"), realNode.getParent(), TIMEOUT_MS);
    }

    static String probe(List<String> command, Path dir, long timeoutMs) throws IOException {
        INVOCATIONS.incrementAndGet();
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (dir != null) pb.directory(dir.toFile());
        Process p = pb.start();
        CompletableFuture<byte[]> out = CompletableFuture.supplyAsync(() -> drain(p.getInputStream()));
        try {
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IOException("node --version did not finish in " + timeoutMs / 1000 + " s");
            }
            if (p.exitValue() != 0) throw new IOException("node --version exited with " + p.exitValue());
            byte[] bytes = out.get(Math.max(1, timeoutMs / 10), TimeUnit.MILLISECONDS);
            String text = new String(bytes, StandardCharsets.UTF_8).trim();
            int nl = text.indexOf('\n');
            return nl < 0 ? text : text.substring(0, nl).trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IOException("node --version output could not be read", e);
        } finally {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
    }

    private static byte[] drain(InputStream in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[64];
        try (in) {
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() < OUTPUT_CAP) out.write(buf, 0, Math.min(n, OUTPUT_CAP - out.size()));
            }
        } catch (IOException ignored) {
            // the process was destroyed
        }
        return out.toByteArray();
    }

    public static int invocations() {
        return INVOCATIONS.get();
    }
}
