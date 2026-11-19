package com.bloxbean.intelliada.idea.joust.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADR-004 D9 with D4's rule: the only execution of the native {@code joust} at configuration time,
 * {@code <joust> version}, on its validated absolute real path. Like {@link NodeVersionProbe} it runs <b>only</b> from
 * the explicit Save of Settings | Tools | JOUST: never on open, never from Detect…, never at wizard start (I1).
 *
 * <p>It runs in a fresh, empty temporary directory (the CLI's {@code version} looks for an npm project from its working
 * directory), with a built environment: {@code PATH} = the system directories only, plus the user's home and locale.
 */
public final class JoustCliVersionProbe {

    private static final AtomicInteger INVOCATIONS = new AtomicInteger();
    private static final int OUTPUT_CAP = 1024;
    private static final Pattern VERSION = Pattern.compile("^joust (\\S+)");

    private JoustCliVersionProbe() {
    }

    /** Runs {@code <realJoust> version} with a 10 s timeout; returns its trimmed first line, or throws. */
    public static String probe(Path realJoust) throws IOException {
        INVOCATIONS.incrementAndGet();
        Path dir = Files.createTempDirectory("intelliada-joust-version-");
        try {
            ProcessBuilder pb = new ProcessBuilder(List.of(realJoust.toString(), "version"));
            pb.environment().clear();
            pb.environment().putAll(minimalEnv(System.getenv()));
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);
            pb.redirectInput(ProcessBuilder.Redirect.from(new File(File.separatorChar == '/' ? "/dev/null" : "NUL")));
            Process p = pb.start();
            try (InputStream in = p.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[256];
                int n;
                while (out.size() < OUTPUT_CAP && (n = in.read(buf)) > 0) out.write(buf, 0, Math.min(n, OUTPUT_CAP - out.size()));
                if (!p.waitFor(10, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    throw new IOException("joust version did not finish in 10 s");
                }
                if (p.exitValue() != 0) throw new IOException("joust version exited with " + p.exitValue());
                String text = out.toString(StandardCharsets.UTF_8).trim();
                int nl = text.indexOf('\n');
                return nl < 0 ? text : text.substring(0, nl).trim();
            } catch (InterruptedException e) {
                p.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            }
        } finally {
            try {
                Files.deleteIfExists(dir);
            } catch (IOException ignored) {
                // a temporary directory
            }
        }
    }

    /** The version in a {@code joust 0.1.0-pre2 (native)} line, or {@code null}. */
    public static String versionOf(String line) {
        if (line == null) return null;
        Matcher m = VERSION.matcher(line.trim());
        return m.find() ? m.group(1) : null;
    }

    static Map<String, String> minimalEnv(Map<String, String> ide) {
        Map<String, String> env = new LinkedHashMap<>();
        boolean windows = File.separatorChar == '\\';
        if (windows) {
            String root = ide.getOrDefault("SystemRoot", "C:\\Windows");
            env.put("SystemRoot", root);
            env.put("PATH", root + "\\System32;" + root);
        } else {
            env.put("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
        }
        for (String k : List.of("HOME", "USERPROFILE", "LANG", "LC_ALL", "TMPDIR", "TEMP", "TMP")) {
            String v = ide.get(k);
            if (v != null && !v.isEmpty()) env.put(k, v);
        }
        return env;
    }

    public static int invocations() {
        return INVOCATIONS.get();
    }
}
