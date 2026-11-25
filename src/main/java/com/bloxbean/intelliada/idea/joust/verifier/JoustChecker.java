package com.bloxbean.intelliada.idea.joust.verifier;

import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostLauncher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * ADR-005 D13 (L24, L37): JOUST's reference checker, bundled in the plugin's {@code joust-verifier/} directory and run
 * out of process, one process per check. Before each run every file the built-in manifest lists is read, hashed against
 * the manifest and copied into a fresh private directory, and the check runs from that copy (I27: a changed file refuses
 * before any process starts). The process is the IDE's own runtime (Java 25 or later), with a fresh private empty
 * working directory, the protocol's environment allow-list and a 30 s deadline; its input is written to stdin from
 * memory. <b>Accepted</b> only for exit status 0 with stdout exactly {@code []} and at most one line terminator;
 * <b>refused</b> with the codes for exit status 1 and one JSON array of code strings; anything else refuses too. There is
 * no bypass: a plugin built without the checker refuses every check.
 */
public final class JoustChecker {

    /** The verdict on one input. */
    public sealed interface Verdict permits Accepted, Refused {
        boolean accepted();
    }

    public record Accepted() implements Verdict {
        @Override
        public boolean accepted() {
            return true;
        }
    }

    /** Refused: the checker's codes when it gave them, and always a plain-text reason. */
    public record Refused(List<String> codes, String why) implements Verdict {
        @Override
        public boolean accepted() {
            return false;
        }
    }

    static final String MANIFEST = "/joust/joust-verifier.manifest";
    static final Duration DEADLINE = Duration.ofSeconds(30);
    static final int MAX_STDOUT = 1024 * 1024;
    static final int MAX_FILE = 32 * 1024 * 1024;
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern FILE = Pattern.compile("(?:lib/)?[A-Za-z0-9._-]{1,200}\\.jar");
    /** Checker processes started (tests: a tampered file must refuse before any). */
    static final AtomicInteger STARTS = new AtomicInteger();
    /** Tests: called with each checker process as soon as it starts. */
    static volatile java.util.function.Consumer<Process> startedForTests;

    /** The built-in manifest: what the plugin was built with. */
    record Manifest(boolean bundled, @Nullable String kit, boolean snapshot, @Nullable String runner, @Nullable String main,
                    Map<String, String> files) {

        static Manifest parse(@Nullable String text) {
            if (text == null) return new Manifest(false, null, false, null, null, Map.of());
            Map<String, String> kv = new LinkedHashMap<>();
            Map<String, String> files = new LinkedHashMap<>();
            for (String line : text.split("\n")) {
                if (line.isBlank()) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) throw new IllegalStateException("a malformed checker manifest");
                String k = line.substring(0, eq), v = line.substring(eq + 1);
                if (k.equals("file")) {
                    String[] p = v.split(" ", 2);
                    if (p.length != 2 || !SHA.matcher(p[0]).matches() || !FILE.matcher(p[1]).matches() || files.put(p[1], p[0]) != null) {
                        throw new IllegalStateException("a malformed checker manifest entry");
                    }
                } else if (kv.put(k, v) != null) {
                    throw new IllegalStateException("a repeated checker manifest key");
                }
            }
            if (!"1".equals(kv.get("format"))) throw new IllegalStateException("an unknown checker manifest format");
            boolean bundled = "true".equals(kv.get("bundled"));
            String runner = kv.get("runner");
            // Fail closed: a bundled manifest must name its runner among its files and list a closure (never zero files).
            if (bundled && (runner == null || !files.containsKey(runner) || files.size() < 2 || kv.get("main") == null)) {
                throw new IllegalStateException("an incomplete checker manifest");
            }
            // The manifest's order is the class path's order, the same on every run (M33A-DES-7, -SEC-6).
            return new Manifest(bundled, kv.get("kit"), "true".equals(kv.get("snapshot")), runner, kv.get("main"),
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(files)));
        }
    }

    private final Manifest manifest;
    private final @Nullable Path dir;
    private final Path javaExe;
    private final Duration deadline;

    JoustChecker(Manifest manifest, @Nullable Path dir, Path javaExe) {
        this(manifest, dir, javaExe, DEADLINE);
    }

    JoustChecker(Manifest manifest, @Nullable Path dir, Path javaExe, Duration deadline) {
        this.manifest = manifest;
        this.dir = dir;
        this.javaExe = javaExe;
        this.deadline = deadline;
    }

    /** The checker this plugin was built with, run on the IDE's own runtime. */
    public static JoustChecker bundled() {
        Manifest m;
        try (InputStream in = JoustChecker.class.getResourceAsStream(MANIFEST)) {
            m = Manifest.parse(in == null ? null : new String(in.readNBytes(64 * 1024), StandardCharsets.UTF_8));
        } catch (IOException | IllegalStateException e) {
            m = new Manifest(false, null, false, null, null, Map.of());
        }
        Path dir = null;
        var plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(com.intellij.openapi.extensions.PluginId.getId("com.bloxbean.intelliada"));
        if (plugin != null) dir = plugin.getPluginPath().resolve("joust-verifier");
        String exe = isWindows() ? "java.exe" : "java";
        return new JoustChecker(m, dir, Path.of(System.getProperty("java.home"), "bin", exe));
    }

    /** Whether this build carries the checker at all. */
    public boolean isBundled() {
        return manifest.bundled() && dir != null;
    }

    /** The kit version label, and whether it is a SNAPSHOT (the panel names it; L37). */
    public @Nullable String kit() {
        return manifest.kit();
    }

    public boolean snapshotKit() {
        return manifest.snapshot();
    }

    /**
     * Checks one input (the method, request, anchors, resolved inputs, node facts, parameters and the host's
     * {@code result}, D13). Off the EDT; blocks up to the deadline.
     */
    public Verdict check(ObjectNode input) throws InterruptedException {
        if (!isBundled()) return new Refused(List.of(), "the transaction checker is not bundled in this build of IntelliAda");
        try {
            if (HostLauncher.javaVersion(javaExe) < 25) throw new IOException("older");   // javaVersion refuses below 25 too
        } catch (IOException e) {
            return new Refused(List.of(), "the transaction checker needs the IDE to run on Java 25 or later (IntelliJ 2026.1 or later)");
        }
        Path work = null;
        try {
            work = Files.createTempDirectory("intelliada-checker-");
            restrict(work);
            Path cp = Files.createDirectory(work.resolve("cp"));
            Path cwd = Files.createDirectory(work.resolve("cwd"));
            List<String> classpath = new ArrayList<>();
            for (Map.Entry<String, String> f : manifest.files().entrySet()) {
                Path src = dir.resolve(f.getKey());
                byte[] b = readVerified(src, f.getValue());
                if (b == null) return new Refused(List.of(), "a bundled checker file is missing or was changed: " + f.getKey());
                Path dst = cp.resolve(f.getKey());   // the bundle's own layout, so no two names collide (M33A-DES-7)
                if (!dst.normalize().startsWith(cp)) return new Refused(List.of(), "a bundled checker file name leaves its folder");
                Files.createDirectories(dst.getParent());
                Files.write(dst, b);
                classpath.add(dst.toString());
            }
            return run(classpath, cwd, HostJson.write(input));
        } catch (IOException e) {
            return new Refused(List.of(), "the transaction checker could not be run (" + e.getClass().getSimpleName() + ")");
        } finally {
            if (work != null) deleteTree(work);
        }
    }

    /** The file's bytes when it is a regular file (no link) whose SHA-256 is {@code sha}; otherwise null. */
    static @Nullable byte[] readVerified(Path f, String sha) throws IOException {
        if (!Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS)) return null;
        byte[] b;
        try (InputStream in = Files.newInputStream(f, LinkOption.NOFOLLOW_LINKS)) {
            b = in.readNBytes(MAX_FILE + 1);
        }
        if (b.length > MAX_FILE) return null;
        return HexFormat.of().formatHex(sha256(b)).equals(sha) ? b : null;
    }

    private Verdict run(List<String> classpath, Path cwd, byte[] stdin) throws IOException, InterruptedException {
        // No attach mechanism and no shared perf data: nothing outside the process reaches into it (M33A-SEC-7).
        List<String> cmd = List.of(javaExe.toString(), "-Xmx1g", "-XX:+UseSerialGC", "-XX:+DisableAttachMechanism", "-XX:-UsePerfData",
                "-Xlog:disable", "-Xlog:all=warning:stderr",
                "-Djava.io.tmpdir=" + cwd, "-cp", String.join(File.pathSeparator, classpath), manifest.main());
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(cwd.toFile());
        Map<String, String> env = pb.environment();
        Map<String, String> allowed = HostLauncher.environment(System.getenv(), isWindows());
        env.clear();
        env.putAll(allowed);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        STARTS.incrementAndGet();
        long end = System.nanoTime() + deadline.toNanos();   // the deadline runs from launch (M33A-CDX-4)
        Process p = pb.start();
        java.util.function.Consumer<Process> hook = startedForTests;
        if (hook != null) hook.accept(p);   // tests inspect the live checker before its input is written (I19)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.util.concurrent.atomic.AtomicBoolean wrote = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean readToEnd = new java.util.concurrent.atomic.AtomicBoolean();
        // stdin and stdout each on their own thread: a checker that stops reading never blocks the caller (M33A-CDX-4).
        Thread writer = new Thread(() -> {
            try (OutputStream in = p.getOutputStream()) {
                in.write(stdin);
                in.flush();
                wrote.set(true);
            } catch (IOException e) {
                // not delivered: the verdict is refused below (M33A-CDX-5)
            }
        }, "JOUST checker stdin");
        Thread reader = new Thread(() -> {
            try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    synchronized (out) {
                        if (out.size() <= MAX_STDOUT) out.write(buf, 0, Math.min(n, MAX_STDOUT + 1 - out.size()));
                    }
                }
                readToEnd.set(true);
            } catch (IOException e) {
                // not read to its end: refused below (M33A-CDX-5)
            }
        }, "JOUST checker stdout");
        writer.setDaemon(true);
        reader.setDaemon(true);
        writer.start();
        reader.start();
        boolean finished = false;
        try {
            boolean done = p.waitFor(Math.max(0, end - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (!done) return new Refused(List.of(), "the transaction checker did not finish within " + deadline.toSeconds() + " s");
            writer.join(Math.max(1, (end - System.nanoTime()) / 1_000_000));
            reader.join(Math.max(1, (end - System.nanoTime()) / 1_000_000));
            if (writer.isAlive() || reader.isAlive() || !readToEnd.get()) {
                return new Refused(List.of(), "the transaction checker's output could not be read completely");
            }
            if (!wrote.get()) return new Refused(List.of(), "the transaction checker did not read its whole input");
            byte[] o;
            synchronized (out) {
                o = out.toByteArray();
            }
            finished = true;
            return verdict(p.exitValue(), o);
        } finally {
            if (!finished) {
                // Every unfinished exit, an interrupt included, ends the process tree and reaps it (M33A-CDX-4).
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                try {
                    p.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException again) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** The verdict for an exit status and stdout, strictly (I27). */
    static Verdict verdict(int exit, byte[] stdout) {
        if (stdout.length > MAX_STDOUT) return new Refused(List.of(), "the transaction checker printed too much");
        String s = new String(stdout, StandardCharsets.UTF_8);
        if (s.endsWith("\r\n")) s = s.substring(0, s.length() - 2);
        else if (s.endsWith("\n")) s = s.substring(0, s.length() - 1);
        if (exit == 0) {
            return s.equals("[]") ? new Accepted() : new Refused(List.of(), "the transaction checker's output was not a verdict");
        }
        if (exit == 1) {
            try {
                JsonNode a = HostJson.parse(s.getBytes(StandardCharsets.UTF_8));
                List<String> codes = new ArrayList<>();
                if (a.isArray() && !a.isEmpty()) {
                    for (JsonNode c : a) {
                        if (!c.isTextual() || !c.textValue().matches("[A-Za-z0-9/#._-]{1,120}")) {
                            return new Refused(List.of(), "the transaction checker's output was not a verdict");
                        }
                        codes.add(c.textValue());
                    }
                    return new Refused(List.copyOf(codes), "refused by the transaction checker: " + String.join(", ", codes));
                }
            } catch (HostJson.Malformed ignored) {
                // falls through
            }
            return new Refused(List.of(), "the transaction checker's output was not a verdict");
        }
        return new Refused(List.of(), "the transaction checker failed (exit status " + exit + ")");
    }

    private static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void restrict(Path dir) {
        try {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows: the user's temporary directory is already private to the user
        }
    }

    private static void deleteTree(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // best effort: a private temporary directory
        }
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
