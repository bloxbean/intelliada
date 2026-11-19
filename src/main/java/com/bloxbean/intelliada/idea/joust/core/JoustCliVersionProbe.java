package com.bloxbean.intelliada.idea.joust.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADR-004 D9 with D4's rule: the only execution of the native {@code joust} at configuration time,
 * {@code <joust> version}, on its validated absolute real path. Like {@link NodeVersionProbe} it runs <b>only</b> from
 * the explicit Save of Settings | Tools | JOUST: never on open, never from Detect…, never at wizard start (I1).
 *
 * <p>It runs in a fresh, empty, private directory that {@link JoustPrivateTemp} creates under the IDE's temporary
 * directory only after checking it against every open project (§12 E12, M2-CDX-11); that directory is also the child's
 * {@code TMPDIR}. The environment is built: {@code PATH} = the system directories only, plus the user's home (when it
 * lies outside every project) and locale. One deadline bounds the whole probe: the output is drained concurrently and
 * capped, and the process and its descendants are destroyed at the end (M2-CDX-17).
 */
public final class JoustCliVersionProbe {

    private static final AtomicInteger INVOCATIONS = new AtomicInteger();
    private static final int OUTPUT_CAP = 1024;
    public static final long TIMEOUT_MS = 10_000;
    private static final Pattern VERSION = Pattern.compile("^joust (\\S+)");

    private JoustCliVersionProbe() {
    }

    /** Runs {@code <realJoust> version} with a 10 s timeout; returns its trimmed first line, or throws. */
    public static String probe(Path realJoust, Path tempBase, Collection<Path> forbiddenRoots) throws IOException {
        return probe(realJoust, tempBase, forbiddenRoots, TIMEOUT_MS);
    }

    /** As {@link #probe(Path, Path, Collection)} with the given deadline for the whole run. */
    public static String probe(Path realJoust, Path tempBase, Collection<Path> forbiddenRoots, long timeoutMs) throws IOException {
        INVOCATIONS.incrementAndGet();
        JoustPaths.Flavor flavor = JoustPaths.Flavor.current();
        Path dir = JoustPrivateTemp.create(tempBase, "intelliada-joust-version-", forbiddenRoots, flavor);
        try {
            ProcessBuilder pb = new ProcessBuilder(List.of(realJoust.toString(), "version"));
            pb.environment().clear();
            pb.environment().putAll(minimalEnv(System.getenv(), dir, forbiddenRoots, flavor));
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);
            pb.redirectInput(ProcessBuilder.Redirect.from(new File(File.separatorChar == '/' ? "/dev/null" : "NUL")));
            Process p = pb.start();
            CompletableFuture<byte[]> out = CompletableFuture.supplyAsync(() -> drain(p.getInputStream()));
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            try {
                long left = deadline - System.nanoTime();
                if (!p.waitFor(Math.max(0, left), TimeUnit.NANOSECONDS)) {
                    throw new IOException("joust version did not finish in " + Math.max(1, timeoutMs / 1000) + " s");
                }
                if (p.exitValue() != 0) throw new IOException("joust version exited with " + p.exitValue());
                long leftMs = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
                String text = new String(out.get(leftMs, TimeUnit.MILLISECONDS), StandardCharsets.UTF_8).trim();
                int nl = text.indexOf('\n');
                return nl < 0 ? text : text.substring(0, nl).trim();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IOException("joust version output could not be read in time", e);
            } finally {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
            }
        } finally {
            JoustPrivateTemp.deleteTree(dir);
        }
    }

    private static byte[] drain(InputStream in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
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

    /** The version in a {@code joust 0.1.0-pre2 (native)} line, or {@code null}. */
    public static String versionOf(String line) {
        if (line == null) return null;
        Matcher m = VERSION.matcher(line.trim());
        return m.find() ? m.group(1) : null;
    }

    /**
     * The child's environment: the system {@code PATH}, the locale, the user's home when it lies outside every project
     * (resolved through its real ancestors), and the private directory as every temporary-directory variable. Nothing
     * else is inherited.
     */
    static Map<String, String> minimalEnv(Map<String, String> ide, Path privateTemp, Collection<Path> forbiddenRoots,
                                          JoustPaths.Flavor flavor) {
        Map<String, String> env = new LinkedHashMap<>();
        boolean windows = flavor == JoustPaths.Flavor.WINDOWS;
        if (windows) {
            String root = ide.getOrDefault("SystemRoot", "C:\\Windows");
            if (!JoustPaths.isAbsolute(root, flavor) || JoustPrivateTemp.destinationProblem(Path.of(root), forbiddenRoots, flavor) != null) {
                root = "C:\\Windows";
            }
            env.put("SystemRoot", root);
            env.put("PATH", root + "\\System32;" + root);
        } else {
            env.put("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
        }
        for (String k : List.of("HOME", "USERPROFILE")) {
            String v = ide.get(k);
            if (v == null || v.isEmpty()) continue;
            try {
                if (JoustPrivateTemp.destinationProblem(Path.of(v), forbiddenRoots, flavor) == null) env.put(k, v);
            } catch (RuntimeException ignored) {
                // not a path: left out
            }
        }
        for (String k : List.of("LANG", "LC_ALL")) {
            String v = ide.get(k);
            if (v != null && !v.isEmpty()) env.put(k, v);
        }
        env.put("TMPDIR", privateTemp.toString());
        if (windows) {
            env.put("TEMP", privateTemp.toString());
            env.put("TMP", privateTemp.toString());
        }
        return env;
    }

    public static int invocations() {
        return INVOCATIONS.get();
    }
}
