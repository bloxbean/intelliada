package com.bloxbean.intelliada.idea.joust.host;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Starts the dev host from a verified snapshot (ADR-005 D11 step 6; protocol 1 "Launch and framing"): the launch spec's
 * {@code java}, protocol 1's fixed JVM options, the snapshot class path in ordinal order, {@code DevHost --stdio}, the
 * project root as working directory, and the protocol's environment allow-list only. stderr is drained continuously into
 * a bounded buffer.
 */
public final class HostLauncher {

    private HostLauncher() {
    }

    /** Protocol 1's JVM options, passed before {@code -cp}. Nothing from the project is added. */
    static final List<String> JVM_OPTIONS = List.of("-XX:+DisplayVMOutputToStderr", "-Xlog:disable", "-Xlog:all=warning:stderr",
            "--enable-native-access=ALL-UNNAMED");

    /**
     * A started host: its process, the drained stderr, and every descendant seen while it lived (M31A-CDX-22), so a
     * helper the host started is still ended after the host itself has exited.
     */
    public record Started(Process process, HostStderr stderr, java.util.Set<ProcessHandle> owned, int javaFeature) {
        /** Ends the root and every owned descendant; true once all of them are gone. */
        public boolean stop() {
            HostLauncher.stop(process);
            for (ProcessHandle h : owned) {
                try {
                    h.destroyForcibly();
                } catch (RuntimeException ignored) {
                    // gone
                }
            }
            return !process.isAlive() && owned.stream().noneMatch(ProcessHandle::isAlive);
        }

        /** True when the root and every owned descendant have exited. */
        public boolean treeGone() {
            return !process.isAlive() && owned.stream().noneMatch(ProcessHandle::isAlive);
        }
    }

    private static final java.util.concurrent.ScheduledExecutorService SAMPLER =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "JOUST host descendants");
                t.setDaemon(true);
                return t;
            });

    /** Records the descendants of {@code p} every 500 ms while it lives. */
    static java.util.Set<ProcessHandle> track(Process p) {
        java.util.Set<ProcessHandle> owned = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.concurrent.ScheduledFuture<?>[] f = new java.util.concurrent.ScheduledFuture<?>[1];
        f[0] = SAMPLER.scheduleWithFixedDelay(() -> {
            try {
                p.descendants().forEach(owned::add);
            } catch (RuntimeException ignored) {
                // listing can be refused; the root is still ended by stop
            }
            if (!p.isAlive() && f[0] != null) f[0].cancel(false);
        }, 0, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
        return owned;
    }

    /** The command line, for tests and the confirmation dialog. */
    public static List<String> command(HostSpec spec, HostSnapshot snapshot) {
        List<String> cmd = new ArrayList<>();
        cmd.add(spec.java().toString());
        cmd.addAll(JVM_OPTIONS);
        cmd.add("-cp");
        StringBuilder cp = new StringBuilder();
        for (Path p : snapshot.classPath()) {
            if (cp.length() > 0) cp.append(java.io.File.pathSeparatorChar);
            cp.append(p);
        }
        cmd.add(cp.toString());
        cmd.add(HostSpec.MAIN_CLASS);
        cmd.add("--stdio");
        return cmd;
    }

    /** The protocol's environment allow-list (D3): PATH, HOME, LANG, LC_*, and on Windows USERPROFILE, SystemRoot, TEMP, TMP. */
    public static Map<String, String> environment(Map<String, String> parent, boolean windows) {
        Map<String, String> env = new TreeMap<>();
        for (var e : parent.entrySet()) {
            String k = e.getKey();
            String key = windows ? k.toUpperCase(Locale.ROOT) : k;
            boolean keep = key.equals("PATH") || key.equals("HOME") || key.equals("LANG") || key.startsWith("LC_")
                    || (windows && (key.equals("USERPROFILE") || key.equals("SYSTEMROOT") || key.equals("TEMP") || key.equals("TMP")));
            if (keep) env.put(k, e.getValue());
        }
        return env;
    }

    /** Starts the host. The caller has the user's trust decision and confirmation (D1, D11 step 5). */
    public static Started start(HostSpec spec, HostSnapshot snapshot, Path projectRoot, int stderrCapBytes) throws IOException {
        return start(spec, snapshot, projectRoot, stderrCapBytes, -1);
    }

    /**
     * Starts the host, refusing before any process exists when the JDK's feature version is not {@code confirmedFeature}
     * (when given): the anchors are resolved for the confirmed JVM (M33A-CDX-40, -CDX-48).
     */
    public static Started start(HostSpec spec, HostSnapshot snapshot, Path projectRoot, int stderrCapBytes, int confirmedFeature)
            throws IOException {
        Path java = spec.java();
        int feature = javaVersion(java);   // read immediately before the start, and carried
        if (confirmedFeature > 0 && feature != confirmedFeature) {
            throw new IOException("the project's JDK changed while the host was being confirmed; start it again");
        }
        if (!Files.isDirectory(projectRoot, LinkOption.NOFOLLOW_LINKS)) throw new IOException("the project directory is missing");
        ProcessBuilder pb = new ProcessBuilder(command(spec, snapshot));
        pb.directory(projectRoot.toFile());
        Map<String, String> env = pb.environment();
        Map<String, String> keep = environment(Map.copyOf(env), isWindows());
        env.clear();
        env.putAll(keep);
        pb.redirectErrorStream(false);
        Process p = pb.start();
        STARTS.incrementAndGet();
        HostStderr err = new HostStderr(stderrCapBytes);
        drain(p.getErrorStream(), err);
        return new Started(p, err, track(p), feature);
    }

    /**
     * Ends the host: the root process always, and every descendant that could be listed while it lived (M31A-CDX-4).
     * Listing descendants can fail (macOS {@code sysctl} refusals); that never skips ending the root.
     */
    public static void stop(Process p) {
        List<ProcessHandle> kids = List.of();
        try {
            kids = p.descendants().toList();
        } catch (RuntimeException ignored) {
            // the root is still ended below
        }
        try {
            p.destroyForcibly();
        } finally {
            for (ProcessHandle k : kids) {
                try {
                    k.destroyForcibly();
                } catch (RuntimeException ignored) {
                    // already gone, or not ours to end
                }
            }
        }
    }

    /**
     * The feature version of the JDK whose {@code bin/java} this is, from its home's {@code release} file; refuses
     * anything but an executable {@code bin/java[.exe]} of a JDK 25 or later (ADR-005 D11 step 2).
     */
    public static int javaVersion(Path exe) throws IOException {
        if (!Files.isRegularFile(exe) || !Files.isExecutable(exe)) throw new IOException("the launch spec's java is not an executable file");
        String name = exe.getFileName().toString();
        Path bin = exe.getParent();
        if (!(name.equals("java") || name.equals("java.exe")) || bin == null || !bin.getFileName().toString().equals("bin")
                || bin.getParent() == null) {
            throw new IOException("the launch spec's java is not a JDK's bin/java");
        }
        Path release = bin.getParent().resolve("release");
        if (!Files.isRegularFile(release, LinkOption.NOFOLLOW_LINKS) || Files.size(release) > 64 * 1024) {
            throw new IOException("the launch spec's java has no JDK release file");
        }
        byte[] rb;
        try {
            rb = HostIo.readCapped(release, 64 * 1024, 10_000);     // no link, capped, bounded (M31A-SEC-5, -CDX-23)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        if (rb.length > 64 * 1024) throw new IOException("the launch spec's JDK release file is too large");
        for (String line : new String(rb, java.nio.charset.StandardCharsets.ISO_8859_1).split("\\R")) {
            var m = java.util.regex.Pattern.compile("^JAVA_VERSION=\"?(\\d{1,3})(?:[.+\\-].*)?\"?$").matcher(line.trim());
            if (m.matches()) {
                int v = Integer.parseInt(m.group(1));
                if (v < 25) throw new IOException("the dev host needs JDK 25 or later; the project's toolchain is " + v);
                return v;
            }
        }
        throw new IOException("the launch spec's JDK release file has no JAVA_VERSION");
    }

    /** Host processes started (tests: a Stop before launch must start none). */
    static final java.util.concurrent.atomic.AtomicInteger STARTS = new java.util.concurrent.atomic.AtomicInteger();

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static void drain(InputStream in, HostStderr sink) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[8192];
            try (in) {
                int r;
                while ((r = in.read(buf)) > 0) sink.append(buf, r);
            } catch (IOException ignored) {
                // the process ended
            } finally {
                sink.close();
            }
        }, "JOUST host stderr");
        t.setDaemon(true);
        t.start();
    }
}
