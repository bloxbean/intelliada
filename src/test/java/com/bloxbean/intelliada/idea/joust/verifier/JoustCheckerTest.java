package com.bloxbean.intelliada.idea.joust.verifier;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

/** ADR-005 M3.3a: the bundled checker adapter (D13; I23, I27). */
public class JoustCheckerTest {

    // ---- the verdict and the manifest (no tools needed) -----------------------------------------------------------

    @Test
    public void onlyAnExactEmptyArrayAccepts() {
        assertTrue(JoustChecker.verdict(0, b("[]")).accepted());
        assertTrue(JoustChecker.verdict(0, b("[]\n")).accepted());
        assertTrue(JoustChecker.verdict(0, b("[]\r\n")).accepted());
        for (String bad : new String[]{"[]\n\n", "[] ", "[]x", "[]\n[]", "", " []", "[\"a\"]"}) {
            assertFalse("exit 0 with " + bad.replace("\n", "\\n") + " (I27)", JoustChecker.verdict(0, b(bad)).accepted());
        }
        JoustChecker.Verdict r = JoustChecker.verdict(1, b("[\"netOutflow/fee\",\"mint/keyed\"]\n"));
        assertEquals(List.of("netOutflow/fee", "mint/keyed"), ((JoustChecker.Refused) r).codes());
        assertTrue(((JoustChecker.Refused) JoustChecker.verdict(1, b("[]\n"))).codes().isEmpty());
        assertTrue(((JoustChecker.Refused) JoustChecker.verdict(1, b("[1]\n"))).codes().isEmpty());
        assertFalse(JoustChecker.verdict(2, b("[]\n")).accepted());
        assertFalse(JoustChecker.verdict(137, b("")).accepted());
    }

    @Test
    public void aManifestFailsClosed() {
        assertFalse(JoustChecker.Manifest.parse(null).bundled());
        assertFalse(JoustChecker.Manifest.parse("format=1\nbundled=false\n").bundled());
        for (String bad : new String[]{"bundled=true\n", "format=1\nbundled=true\nrunner=r.jar\nmain=M\n",
                "format=1\nbundled=true\nrunner=r.jar\nmain=M\nfile=" + "a".repeat(64) + " r.jar\n",
                "format=1\nbundled=true\nrunner=r.jar\nmain=M\nfile=" + "a".repeat(64) + " r.jar\nfile=" + "b".repeat(64) + " ../x.jar\n"}) {
            try {
                JoustChecker.Manifest.parse(bad);
                fail("accepted an incomplete or unsafe manifest: " + bad);
            } catch (IllegalStateException expected) {
                // refused
            }
        }
    }

    @Test
    public void aBuildWithoutTheCheckerRefusesEveryCheck() throws Exception {
        JoustChecker c = new JoustChecker(JoustChecker.Manifest.parse("format=1\nbundled=false\n"), null, Path.of("java"));
        assertFalse(c.isBundled());
        JoustChecker.Verdict v = c.check(HostJson.object());
        assertFalse("no bypass", v.accepted());
        assertTrue(((JoustChecker.Refused) v).why().contains("not bundled"));
    }

    @Test
    public void theBuiltInManifestNamesTheBundle() throws Exception {
        try (InputStream in = JoustChecker.class.getResourceAsStream(JoustChecker.MANIFEST)) {
            assertNotNull("the build generates the manifest into the plugin's resources", in);
            JoustChecker.Manifest m = JoustChecker.Manifest.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            if (!m.bundled()) {
                assertFalse("a checker-less build only with -Pjoust.checkerOptional", JoustTestTools.required());
                return;
            }
            assertEquals("21 locked jars and the runner", 22, m.files().size());
            assertTrue(m.files().containsKey(m.runner()));
        }
    }

    // ---- the real checker (tool-gated: the bundle and a JDK 25) --------------------------------------------------

    private record Tools(JoustChecker checker, Path dir, JoustChecker.Manifest manifest, Path java, Path kitJar) {
    }

    private static Tools tools(String test) throws Exception {
        String dir = System.getProperty("joust.verifier.dir", "");
        String java = System.getProperty("joust.lint.java", "");
        Path kit = null;
        for (String p : System.getProperty("joust.conformance.classpath", "").split(File.pathSeparator)) {
            if (p.endsWith("-conformance.jar")) kit = Path.of(p);
        }
        JoustChecker.Manifest m;
        try (InputStream in = JoustChecker.class.getResourceAsStream(JoustChecker.MANIFEST)) {
            m = JoustChecker.Manifest.parse(in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        if (dir.isEmpty() || java.isEmpty() || kit == null || !m.bundled() || !Files.isDirectory(Path.of(dir, "lib"))) {
            if (JoustTestTools.required()) throw new AssertionError(test + " needs the bundled checker (the JOUST kit in ~/.m2) and a JDK 25");
            System.out.println("JOUST-SKIPPED " + test + ": no bundled checker or JDK 25");
            return null;
        }
        return new Tools(new JoustChecker(m, Path.of(dir), Path.of(java)), Path.of(dir), m, Path.of(java), kit);
    }

    /** I23, G3.2: the adapter agrees with every one of the kit's verifier vectors, through the real process. */
    @Test
    public void theAdapterAgreesWithEveryVector() throws Exception {
        Tools t = tools("theAdapterAgreesWithEveryVector");
        if (t == null) return;
        List<JsonNode> vectors = new ArrayList<>();
        try (ZipFile z = new ZipFile(t.kitJar().toFile())) {
            for (ZipEntry e : Collections.list(z.entries())) {
                if (e.getName().startsWith("verifier/") && e.getName().endsWith(".json")) {
                    vectors.add(HostJson.MAPPER.readTree(z.getInputStream(e)));
                }
            }
        }
        assertEquals(333, vectors.size());
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (JsonNode v : vectors) {
                results.add(pool.submit(() -> {
                    ObjectNode in = v.deepCopy();
                    List<String> want = new ArrayList<>();
                    in.remove("violations").forEach(x -> want.add(x.asText()));
                    in.remove("source");
                    JoustChecker.Verdict got = t.checker().check(in);
                    List<String> codes = got.accepted() ? List.of() : ((JoustChecker.Refused) got).codes();
                    boolean ok = want.isEmpty() ? got.accepted() : !got.accepted() && codes.equals(want);
                    return ok ? null : v.path("source").asText("?") + ": want " + want + ", got " + got;
                }));
            }
            List<String> mismatches = new ArrayList<>();
            for (Future<String> f : results) {
                String m = f.get();
                if (m != null) mismatches.add(m);
            }
            assertTrue(mismatches.size() + " mismatches, e.g. " + mismatches.stream().limit(3).toList(), mismatches.isEmpty());
        } finally {
            pool.shutdownNow();
        }
    }

    /** I27: a changed or replaced checker file refuses before any process starts. */
    @Test
    public void aChangedFileRefusesBeforeAnyProcessStarts() throws Exception {
        Tools t = tools("aChangedFileRefusesBeforeAnyProcessStarts");
        if (t == null) return;
        Path copy = Files.createTempDirectory("checker-tamper");
        copyTree(t.dir(), copy);
        Path jar = copy.resolve("lib/julc-core-0.1.0-pre22.jar");
        byte[] b = Files.readAllBytes(jar);
        b[b.length / 2] ^= 1;
        Files.write(jar, b);
        int starts = JoustChecker.STARTS.get();
        JoustChecker.Verdict v = new JoustChecker(t.manifest(), copy, t.java()).check(HostJson.object());
        assertFalse(v.accepted());
        assertTrue(((JoustChecker.Refused) v).why(), ((JoustChecker.Refused) v).why().contains("changed"));
        assertEquals("no process started", starts, JoustChecker.STARTS.get());
        if (!JoustTestTools.windows()) {
            Path copy2 = Files.createTempDirectory("checker-link");
            copyTree(t.dir(), copy2);
            Path runner = copy2.resolve(t.manifest().runner());
            Files.delete(runner);
            Files.createSymbolicLink(runner, t.dir().resolve(t.manifest().runner()));
            assertFalse("a link, even to the right bytes, refuses",
                    new JoustChecker(t.manifest(), copy2, t.java()).check(HostJson.object()).accepted());
            assertEquals(starts, JoustChecker.STARTS.get());
        }
    }

    /** I23: a checker that crashes, times out or prints extra output refuses (a stand-in runtime runs instead). */
    @Test
    public void aCrashATimeoutOrExtraOutputRefuses() throws Exception {
        Tools t = tools("aCrashATimeoutOrExtraOutputRefuses");
        if (t == null || JoustTestTools.windows()) return;
        for (String[] c : new String[][]{{"cat >/dev/null; echo '[]'; echo extra", "not a verdict"},
                {"cat >/dev/null; exit 3", "exit status 3"}, {"exec sleep 30", "did not finish"},
                {"cat >/dev/null; printf '[]'; exit 1", "not a verdict"}}) {
            Path home = Files.createTempDirectory("fake-jdk");
            Files.writeString(home.resolve("release"), "JAVA_VERSION=\"25.0.2\"\n");
            Path bin = Files.createDirectory(home.resolve("bin"));
            Path java = bin.resolve("java");
            Files.writeString(java, "#!/bin/sh\n" + c[0] + "\n");
            java.toFile().setExecutable(true);
            JoustChecker.Verdict v = new JoustChecker(t.manifest(), t.dir(), java, Duration.ofSeconds(2)).check(HostJson.object());
            assertFalse(c[0], v.accepted());
            assertTrue(c[0] + " -> " + v, ((JoustChecker.Refused) v).why().contains(c[1]));
        }
        Path old = Files.createTempDirectory("old-jdk");
        Files.writeString(old.resolve("release"), "JAVA_VERSION=\"21.0.3\"\n");
        Path ob = Files.createDirectory(old.resolve("bin"));
        Files.writeString(ob.resolve("java"), "#!/bin/sh\necho '[]'\n");
        ob.resolve("java").toFile().setExecutable(true);
        JoustChecker.Verdict v = new JoustChecker(t.manifest(), t.dir(), ob.resolve("java")).check(HostJson.object());
        assertTrue(((JoustChecker.Refused) v).why().contains("Java 25"));
    }

    /** I19: the checker process opens no listening socket (its stdin held open, so it stays alive to be inspected). */
    @Test
    public void theCheckerListensOnNoSocket() throws Exception {
        Tools t = tools("theCheckerListensOnNoSocket");
        if (t == null || JoustTestTools.windows()) return;
        Path lsof = Path.of("/usr/sbin/lsof");
        if (!Files.isExecutable(lsof)) lsof = Path.of("/usr/bin/lsof");
        if (!Files.isExecutable(lsof)) {
            assertFalse("lsof is needed for I19", JoustTestTools.required());
            return;
        }
        List<String> cp = new ArrayList<>();
        for (String f : t.manifest().files().keySet()) cp.add(t.dir().resolve(f).toString());
        Process p = new ProcessBuilder(t.java().toString(), "-cp", String.join(File.pathSeparator, cp), t.manifest().main())
                .redirectErrorStream(true).start();
        try {
            Thread.sleep(1500);   // the JVM is up and blocked reading stdin
            assertTrue("the runner waits for its input", p.isAlive());
            Process l = new ProcessBuilder(lsof.toString(), "-nP", "-a", "-p", String.valueOf(p.pid()), "-i").redirectErrorStream(true).start();
            String sockets = new String(l.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            l.waitFor();
            assertFalse("I19: the checker holds no network socket: " + sockets, sockets.contains("LISTEN") || sockets.contains("UDP")
                    || sockets.contains("TCP"));
        } finally {
            p.getOutputStream().close();
            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            p.destroyForcibly();
        }
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void copyTree(Path from, Path to) throws Exception {
        try (var w = Files.walk(from)) {
            for (Path p : (Iterable<Path>) w::iterator) {
                Path d = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(d);
                else Files.copy(p, d);
            }
        }
    }
}
