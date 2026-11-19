package com.bloxbean.intelliada.idea.joust.wizard;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * ADR-004 D9: runs the wizard's {@code joust new}, exactly the argument list {@link JoustNewCommand} built, with exactly
 * the environment {@link JoustNewEnvironment} built (the inherited environment is cleared first, I7). No shell, stdin
 * closed, output captured within a cap, a timeout, and cancellation that destroys the process and its descendants.
 *
 * <p>This is the wizard's only process. It runs when the user clicks Create, never at wizard start.
 */
public final class JoustNewRunner {

    public static final int OUTPUT_CAP = 64 * 1024;

    private JoustNewRunner() {
    }

    /** What happened: the exit code (-1 when it did not finish), the captured output, and why it stopped early. */
    public record Outcome(int exitCode, String output, boolean timedOut, boolean cancelled) {
        public boolean ok() {
            return exitCode == 0 && !timedOut && !cancelled;
        }
    }

    public static Outcome run(List<String> command, Map<String, String> env, Path workDir, Duration timeout,
                              BooleanSupplier cancelled) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().clear();
        pb.environment().putAll(env);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.from(new File(File.separatorChar == '/' ? "/dev/null" : "NUL")));
        Process p = pb.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            byte[] buf = new byte[8192];
            try (InputStream in = p.getInputStream()) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    synchronized (out) {
                        int room = OUTPUT_CAP - out.size();
                        if (room > 0) out.write(buf, 0, Math.min(n, room));
                    }
                }
            } catch (IOException ignored) {
                // the process ended
            }
        }, "joust-new-output");
        reader.setDaemon(true);
        reader.start();
        long deadline = System.nanoTime() + timeout.toNanos();
        boolean timedOut = false;
        boolean wasCancelled = false;
        try {
            while (!p.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    wasCancelled = true;
                    break;
                }
                if (System.nanoTime() > deadline) {
                    timedOut = true;
                    break;
                }
            }
        } catch (InterruptedException e) {
            wasCancelled = true;
            Thread.currentThread().interrupt();
        }
        if (timedOut || wasCancelled) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
        try {
            reader.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String text;
        synchronized (out) {
            text = out.toString(StandardCharsets.UTF_8);
        }
        int code = p.isAlive() ? -1 : (timedOut || wasCancelled ? -1 : p.exitValue());
        return new Outcome(code, text, timedOut, wasCancelled);
    }
}
