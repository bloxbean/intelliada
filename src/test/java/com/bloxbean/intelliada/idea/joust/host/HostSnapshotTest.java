package com.bloxbean.intelliada.idea.joust.host;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.Assert.*;

/**
 * ADR-005 D11 steps 2–4, I24, I26: the launch spec is validated, the launch set is copied into a private read-only
 * snapshot whose digest is recomputed from the copies by protocol 1's fingerprint format, and the descriptions and
 * blueprints are read into memory. The expected digest here is computed independently of {@link HostSnapshot}.
 */
public class HostSnapshotTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static String sha(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    /** A classes directory (with a description and a nested file) and a jar (with a blueprint). */
    private ObjectNode launchSet(boolean classPathManifest) throws Exception {
        Path classes = tmp.newFolder().toPath().resolve("classes");
        Files.createDirectories(classes);
        Files.createDirectories(classes.resolve("META-INF/joust"));
        Files.createDirectories(classes.resolve("org/example"));
        Files.writeString(classes.resolve("META-INF/joust/org.example.Counter.json"), "{\"x-joust\":{\"version\":2}}");
        Files.writeString(classes.resolve("org/example/Counter.class"), "classfile");
        Files.writeString(classes.resolve("Z.txt"), "z");
        Path jar = classes.getParent().resolve("verifier.jar");
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (classPathManifest) mf.getMainAttributes().put(Attributes.Name.CLASS_PATH, "other.jar");
        try (OutputStream o = Files.newOutputStream(jar); JarOutputStream j = new JarOutputStream(o, mf)) {
            j.putNextEntry(new ZipEntry("META-INF/plutus/OutputVerifier.plutus.json"));
            j.write("{\"cborHex\":\"00\"}".getBytes(StandardCharsets.UTF_8));
            j.closeEntry();
        }
        ObjectNode spec = HostJson.object().put("protocol", 1).put("mainClass", HostSpec.MAIN_CLASS).put("java", "/opt/jdk/bin/java");
        ArrayNode launch = spec.putArray("launch");
        StringBuilder lines = new StringBuilder();
        ObjectNode dir = launch.addObject().put("ordinal", 0).put("path", classes.toString()).put("kind", "dir");
        ObjectNode files = dir.putObject("files");
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String rel : List.of("META-INF/joust/org.example.Counter.json", "org/example/Counter.class", "Z.txt")) {
            sorted.put(rel, sha(Files.readAllBytes(classes.resolve(rel))));
        }
        // Unsigned UTF-8 byte order equals String order for these ASCII names.
        for (var e : sorted.entrySet()) {
            files.put(e.getKey(), e.getValue());
            lines.append(e.getValue()).append(" 0 dir ").append(e.getKey()).append('\n');
        }
        String jh = sha(Files.readAllBytes(jar));
        launch.addObject().put("ordinal", 1).put("path", jar.toString()).put("kind", "jar").put("sha256", jh);
        lines.append(jh).append(" 1 jar \n");
        spec.put("digest", sha(lines.toString().getBytes(StandardCharsets.UTF_8)));
        return spec;
    }

    @Test
    public void theSnapshotMatchesTheDigestAndHoldsTheAnchors() throws Exception {
        HostSpec spec = HostSpec.parse(launchSet(false));
        Path base = tmp.newFolder("snapshots").toPath();
        HostSnapshot s = HostSnapshot.create(spec, base);
        try {
            assertEquals(spec.digest(), s.digest());
            assertEquals(2, s.classPath().size());
            assertTrue(s.classPath().get(0).startsWith(s.root()));
            assertTrue(Files.isRegularFile(s.classPath().get(1)));
            assertFalse("the snapshot is read-only", Files.isWritable(s.classPath().get(1)));
            assertTrue(s.firstByPath().containsKey("META-INF/joust/org.example.Counter.json"));
            assertTrue(s.firstByPath().containsKey("META-INF/plutus/OutputVerifier.plutus.json"));
            assertEquals(1, s.firstByPath().get("META-INF/plutus/OutputVerifier.plutus.json").ordinal());
            try (var list = Files.list(base)) {
                assertTrue("no temporary directory is left", list.noneMatch(p -> p.getFileName().toString().startsWith(".tmp-")));
            }
        } finally {
            s.delete();
        }
        assertFalse(Files.exists(s.root()));
    }

    @Test
    public void aFileChangedAfterTheSpecIsRefusedAndNothingIsLeft() throws Exception {
        HostSpec spec = HostSpec.parse(launchSet(false));
        Files.writeString(spec.launch().get(0).path().resolve("Z.txt"), "changed");
        Path base = tmp.newFolder("snapshots").toPath();
        try {
            HostSnapshot.create(spec, base);
            fail("a changed file was copied");
        } catch (HostSpec.Invalid expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("changed"));
        }
        try (var list = Files.list(base)) {
            assertEquals(0, list.count());
        }
    }

    @Test
    public void aWrongDigestIsRefused() throws Exception {
        ObjectNode n = launchSet(false);
        n.put("digest", "0".repeat(64));
        try {
            HostSnapshot.create(HostSpec.parse(n), tmp.newFolder("s").toPath());
            fail();
        } catch (HostSpec.Invalid expected) {
            assertTrue(expected.getMessage().contains("digest"));
        }
    }

    @Test
    public void aLinkInPlaceOfAFileIsRefused() throws Exception {
        HostSpec spec = HostSpec.parse(launchSet(false));
        Path z = spec.launch().get(0).path().resolve("Z.txt");
        Path elsewhere = tmp.newFile("elsewhere").toPath();
        Files.writeString(elsewhere, "z");
        Files.delete(z);
        Files.createSymbolicLink(z, elsewhere);
        try {
            HostSnapshot.create(spec, tmp.newFolder("s").toPath());
            fail("a symbolic link was followed");
        } catch (HostSpec.Invalid expected) {
            assertTrue(expected.getMessage().contains("regular file"));
        }
    }

    @Test
    public void aJarWithAManifestClassPathIsRefused() throws Exception {
        try {
            HostSnapshot.create(HostSpec.parse(launchSet(true)), tmp.newFolder("s").toPath());
            fail();
        } catch (HostSpec.Invalid expected) {
            assertTrue(expected.getMessage().contains("Class-Path"));
        }
    }

    @Test
    public void theSpecIsValidated() throws Exception {
        String[][] bad = {
                {"protocol", "2"}, {"mainClass", "\"org.example.Main\""}, {"java", "\"bin/java\""}, {"digest", "\"abc\""},
        };
        for (String[] b : bad) {
            ObjectNode n = launchSet(false);
            n.set(b[0], HostJson.MAPPER.readTree(b[1]));
            try {
                HostSpec.parse(n);
                fail("accepted " + b[0] + "=" + b[1]);
            } catch (HostSpec.Invalid expected) {
                // refused
            }
        }
        ObjectNode gap = launchSet(false);
        ((ObjectNode) gap.get("launch").get(1)).put("ordinal", 2);
        assertInvalid(gap);
        ObjectNode up = launchSet(false);
        ((ObjectNode) up.get("launch").get(0).get("files")).put("../escape", "0".repeat(64));
        assertInvalid(up);
        ObjectNode rel = launchSet(false);
        ((ObjectNode) rel.get("launch").get(1)).put("path", "relative.jar");
        assertInvalid(rel);
        for (String r : new String[]{"a/./b", "/abs", "a//b", "a\\b", "C:/x", "a\u0001b", ""}) {
            assertFalse(r, HostSpec.safeRelative(r));
        }
        assertTrue(HostSpec.safeRelative("META-INF/joust/a.json"));
    }

    @Test
    public void integersAreComparedWithoutNarrowing() throws Exception {
        ObjectNode n = launchSet(false);
        n.set("protocol", HostJson.MAPPER.readTree("4294967297"));
        assertInvalid(n);
        ObjectNode o = launchSet(false);
        ((ObjectNode) o.get("launch").get(0)).set("ordinal", HostJson.MAPPER.readTree("4294967296"));
        assertInvalid(o);
    }

    @Test
    public void aLinkUnderADirectoryEntryAndAMissingRootAreRefused() throws Exception {
        ObjectNode n = launchSet(false);
        HostSpec spec = HostSpec.parse(n);
        Path classes = spec.launch().get(0).path();
        Path real = tmp.newFolder("elsewhere-dir").toPath();
        Files.move(classes.resolve("org"), real.resolve("org"));
        Files.createSymbolicLink(classes.resolve("org"), real.resolve("org"));
        try {
            HostSnapshot.create(spec, tmp.newFolder("s1").toPath());
            fail("a linked subdirectory was followed (M31A-CDX-12)");
        } catch (HostSpec.Invalid expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("link"));
        }
        ObjectNode empty = launchSet(false);
        ObjectNode dir = (ObjectNode) empty.get("launch").get(0);
        dir.put("path", tmp.getRoot().toPath().resolve("missing-dir").toString());
        dir.putObject("files");
        // The digest no longer matters: the missing root is refused first.
        try {
            HostSnapshot.create(HostSpec.parse(empty), tmp.newFolder("s2").toPath());
            fail("a missing directory with no files passed");
        } catch (HostSpec.Invalid expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("missing"));
        }
    }

    @Test
    public void aJarThatReadsDifferentlyToTheJvmIsRefused() throws Exception {
        // M31A-SEC-2's proof of concept: junk before a manifest only the central directory lists, adding a Class-Path.
        Path jar = tmp.newFolder("poc").toPath().resolve("0.jar");
        try (var in = HostSnapshotTest.class.getResourceAsStream("/joust-m3/hidden-manifest.jar")) {
            Files.copy(in, jar);
        }
        ObjectNode spec = HostJson.object().put("protocol", 1).put("mainClass", HostSpec.MAIN_CLASS).put("java", "/opt/jdk/bin/java");
        String h = sha(Files.readAllBytes(jar));
        spec.putArray("launch").addObject().put("ordinal", 0).put("path", jar.toString()).put("kind", "jar").put("sha256", h);
        spec.put("digest", sha((h + " 0 jar \n").getBytes(StandardCharsets.UTF_8)));
        try {
            HostSnapshot.create(HostSpec.parse(spec), tmp.newFolder("s3").toPath());
            fail("a jar with a hidden manifest was accepted");
        } catch (HostSpec.Invalid expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Class-Path") || expected.getMessage().contains("differently"));
        }
    }

    @Test
    public void aWhitespaceClassPathIsRefusedAsTheHostRefusesIt() throws Exception {
        Path dir = tmp.newFolder("ws").toPath();
        Path jar = dir.resolve("w.jar");
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().put(Attributes.Name.CLASS_PATH, " ");
        try (OutputStream o = Files.newOutputStream(jar); JarOutputStream j = new JarOutputStream(o, mf)) {
            j.putNextEntry(new ZipEntry("a.txt"));
            j.closeEntry();
        }
        ObjectNode spec = HostJson.object().put("protocol", 1).put("mainClass", HostSpec.MAIN_CLASS).put("java", "/opt/jdk/bin/java");
        String h = sha(Files.readAllBytes(jar));
        spec.putArray("launch").addObject().put("ordinal", 0).put("path", jar.toString()).put("kind", "jar").put("sha256", h);
        spec.put("digest", sha((h + " 0 jar \n").getBytes(StandardCharsets.UTF_8)));
        try {
            HostSnapshot.create(HostSpec.parse(spec), tmp.newFolder("s4").toPath());
            fail("a whitespace Class-Path was accepted");
        } catch (HostSpec.Invalid expected) {
            assertTrue(expected.getMessage().contains("Class-Path"));
        }
    }

    @Test
    public void theRuntimeVersionIsReadOnlyFromTheExpectedInitializer() throws Exception {
        var cw = new org.jetbrains.org.objectweb.asm.ClassWriter(0);
        cw.visit(org.jetbrains.org.objectweb.asm.Opcodes.V17, org.jetbrains.org.objectweb.asm.Opcodes.ACC_PUBLIC,
                "org/julclang/state/runtime/RuntimeVersion", null, "java/lang/Object", null);
        var mv = cw.visitMethod(org.jetbrains.org.objectweb.asm.Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn("9.9.9");
        mv.visitFieldInsn(org.jetbrains.org.objectweb.asm.Opcodes.PUTSTATIC, "org/julclang/state/runtime/RuntimeVersion", "VERSION", "Ljava/lang/String;");
        mv.visitLdcInsn("1.2.3");
        mv.visitMethodInsn(org.jetbrains.org.objectweb.asm.Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
        mv.visitFieldInsn(org.jetbrains.org.objectweb.asm.Opcodes.PUTSTATIC, "org/julclang/state/runtime/RuntimeVersion", "VERSION", "Ljava/lang/String;");
        mv.visitInsn(org.jetbrains.org.objectweb.asm.Opcodes.RETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
        cw.visitEnd();
        assertEquals("only LDC; String.valueOf; PUTSTATIC RuntimeVersion.VERSION counts", "1.2.3", HostVersions.versionOf(cw.toByteArray()));
    }

    @Test
    public void theVersionIsReadAsTheJvmResolvesAMultiReleaseJar() throws Exception {
        // M31A-SEC-10's jar: the base class says 0.1.0-reviewed, META-INF/versions/21 says 9.9.9-other (what a JVM loads).
        Path dir = tmp.newFolder("mr").toPath();
        Path jar = dir.resolve("rt.jar");
        try (var in = HostSnapshotTest.class.getResourceAsStream("/joust-m3/multi-release-runtime.jar")) {
            Files.copy(in, jar);
        }
        ObjectNode spec = HostJson.object().put("protocol", 1).put("mainClass", HostSpec.MAIN_CLASS).put("java", "/opt/jdk/bin/java");
        String h = sha(Files.readAllBytes(jar));
        spec.putArray("launch").addObject().put("ordinal", 0).put("path", jar.toString()).put("kind", "jar").put("sha256", h);
        spec.put("digest", sha((h + " 0 jar \n").getBytes(StandardCharsets.UTF_8)));
        HostSnapshot s = HostSnapshot.create(HostSpec.parse(spec), tmp.newFolder("s5").toPath());
        try {
            assertEquals("9.9.9-other", HostVersions.runtimeVersion(s, 25));
        } finally {
            s.delete();
        }
    }

    @Test
    public void deletingASnapshotNeverChangesALinkTarget() throws Exception {
        Path base = tmp.newFolder("del").toPath();
        Path snap = base.resolve("s");
        Files.createDirectories(snap);
        Path outside = tmp.newFile("outside.txt").toPath();
        Files.setPosixFilePermissions(outside, java.nio.file.attribute.PosixFilePermissions.fromString("r--------"));
        Files.createSymbolicLink(snap.resolve("link"), outside);
        HostSnapshot.delete(snap);
        assertFalse(Files.exists(snap));
        assertEquals("the target's permissions are untouched (M31A-CDX-28)", "r--------",
                java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(outside)));
    }

    private static void assertInvalid(ObjectNode n) {
        try {
            HostSpec.parse(n);
            fail("accepted " + n);
        } catch (HostSpec.Invalid expected) {
            // refused
        }
    }

    @Test
    public void theJavaMustBeAJdk25OrLater() throws Exception {
        Path home = tmp.newFolder("jdk").toPath();
        Path java = home.resolve("bin/java");
        Files.createDirectories(java.getParent());
        Files.writeString(java, "#!/bin/sh\n");
        java.toFile().setExecutable(true);
        Files.writeString(home.resolve("release"), "IMPLEMENTOR=\"x\"\nJAVA_VERSION=\"25.0.2\"\n");
        assertEquals(25, HostLauncher.javaVersion(java));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"21.0.3\"\n");
        try {
            HostLauncher.javaVersion(java);
            fail("JDK 21 accepted");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("25"));
        }
    }

    @Test
    public void theEnvironmentIsTheAllowList() {
        var env = HostLauncher.environment(java.util.Map.of("PATH", "/bin", "HOME", "/h", "LANG", "C", "LC_ALL", "C",
                "JAVA_TOOL_OPTIONS", "-javaagent:x", "CLASSPATH", "x", "JDK_JAVA_OPTIONS", "y", "BLOCKFROST", "secret"), false);
        assertEquals(java.util.Set.of("PATH", "HOME", "LANG", "LC_ALL"), env.keySet());
        var win = HostLauncher.environment(java.util.Map.of("Path", "C:\\", "SystemRoot", "C:\\Windows", "TEMP", "t",
                "USERPROFILE", "u", "NODE_OPTIONS", "x"), true);
        assertEquals(java.util.Set.of("Path", "SystemRoot", "TEMP", "USERPROFILE"), win.keySet());
    }
}
