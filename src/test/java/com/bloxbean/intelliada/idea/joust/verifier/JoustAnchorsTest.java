package com.bloxbean.intelliada.idea.joust.verifier;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostSnapshot;
import com.bloxbean.intelliada.idea.joust.host.HostSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/** ADR-005 M3.3a, I24: anchors IntelliAda derives itself from a verified snapshot (D13). A platform test: CCL logs through the IDE. */
public class JoustAnchorsTest extends BasePlatformTestCase {

    private Path newFolder() throws Exception {
        return Files.createTempDirectory("joust-anchors");
    }

    /** A real snapshot of {@code entries} (directories or jars). */
    private HostSnapshot snapshot(List<Path> entries) throws Exception {
        return SnapshotOf.create(entries, newFolder()).snapshot();
    }

    private static String sha(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    // ---- no tools needed ----------------------------------------------------------------------------------------

    public void testTheScriptHashFollowsCcl() throws Exception {
        // A byte string of 0x4401020304 (itself a byte string): the outer header is removed once.
        byte[] wrapped = HexFormat.of().parseHex("45" + "4401020304");
        String h = JoustAnchors.scriptHash(wrapped);
        byte[] expect = new byte[]{0x03, 0x44, 1, 2, 3, 4};
        assertEquals(HexFormat.of().formatHex(com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash224(expect)), h);
        for (String bad : new String[]{"", "44010203", "4601020304ff", "5f01ff", "a0"}) {
            try {
                JoustAnchors.scriptHash(HexFormat.of().parseHex(bad));
                fail("accepted " + bad);
            } catch (JoustAnchors.Refused expected) {
                // refused
            }
        }
    }

    // ---- JOUST's examples (tool-gated: JOUST_EXAMPLES, a JOUST checkout with the examples and verifier built) ----

    private record Examples(Path root, Path verifierJar, Path kitJar) {
        Path classes(String example) {
            return root.resolve("examples/" + example + "/build/classes/java/main");
        }
    }

    private static Examples examples(String test) throws Exception {
        String root = JoustTestTools.env("JOUST_EXAMPLES", test);
        if (root == null) return null;
        Path libs = Path.of(root, "julc-state-verifier/build/libs");
        Path jar = null;
        try (var l = Files.list(libs)) {
            for (Path p : (Iterable<Path>) l::iterator) {
                String n = p.getFileName().toString();
                if (n.startsWith("julc-state-verifier-") && n.endsWith(".jar") && !n.contains("sources") && !n.contains("javadoc")
                        && n.contains(System.getProperty("joust.conformance.version", "SNAPSHOT").replace("-SNAPSHOT", ""))) jar = p;
            }
        }
        Path kit = null;
        for (String p : System.getProperty("joust.conformance.classpath", "").split(File.pathSeparator)) {
            if (p.endsWith("-conformance.jar")) kit = Path.of(p);
        }
        assertNotNull("the julc-state-verifier jar is built in " + libs, jar);
        assertNotNull("the conformance kit", kit);
        return new Examples(Path.of(root), jar, kit);
    }

    /** I24: anchors derived from JOUST examples' snapshots equal the vectors' anchors. */
    public void testDerivedAnchorsEqualTheVectors() throws Exception {
        Examples ex = examples("derivedAnchorsEqualTheVectors");
        if (ex == null) return;
        List<JsonNode> vectors = new ArrayList<>();
        try (ZipFile z = new ZipFile(ex.kitJar().toFile())) {
            for (ZipEntry e : Collections.list(z.entries())) {
                if (e.getName().startsWith("verifier/") && e.getName().endsWith(".json")) vectors.add(HostJson.MAPPER.readTree(z.getInputStream(e)));
            }
        }
        int compared = 0, mutated = 0;
        String[][] contracts = {{"bank", "Bank"}, {"counter", "Counter"}, {"token", "Token"}, {"registry", "Registry"}, {"dex", "Dex"}};
        for (String[] c : contracts) {
            HostSnapshot s = snapshot(List.of(ex.classes(c[0]), ex.verifierJar()));
            String name = "org.julclang.state.examples." + c[0] + "." + c[1];
            JoustAnchors.Anchors a = JoustAnchors.derive(s, name, 0, 25);
            ObjectNode mine = a.toJson();
            int matched = 0;
            for (JsonNode v : vectors) {
                JsonNode va = v.get("anchors");
                if (!va.get("description").equals(mine.get("description"))) continue;   // a vector with a mutated description
                // An accepted vector's anchors are the real ones; a rejection may mutate them on purpose.
                if (!v.path("violations").isEmpty()) {
                    if (!va.equals(mine)) mutated++;
                    continue;
                }
                ObjectNode want = va.deepCopy(), got = mine.deepCopy();
                want.remove("description");
                got.remove("description");
                assertEquals(name + " anchors in " + v.path("source").asText(), want, got);
                assertEquals(va, mine);
                matched++;
            }
            assertTrue(name + " has accepted vectors with its built description", matched > 0);
            compared += matched;
            s.delete();
        }
        assertTrue("accepted vectors compared: " + compared + " (rejections with mutated anchors: " + mutated + ")", compared >= 10);
        System.out.println("I24: anchors equal " + compared + " accepted vectors; " + mutated + " rejections mutate their anchors");
    }

    /** I24: a tampered blueprint hash, a duplicate description, and a project blueprint named OutputVerifier refuse. */
    public void testTamperedOrAmbiguousResourcesRefuse() throws Exception {
        Examples ex = examples("tamperedOrAmbiguousResourcesRefuse");
        if (ex == null) return;
        Path bank = ex.classes("bank");
        String name = "org.julclang.state.examples.bank.Bank";
        // A blueprint whose hash field disagrees with its script.
        Path t1 = copy(bank);
        Path bp = t1.resolve("META-INF/plutus/BankValidator.plutus.json");
        ObjectNode b = (ObjectNode) HostJson.MAPPER.readTree(Files.readAllBytes(bp));
        b.put("hash", "00".repeat(28));
        Files.write(bp, HostJson.write(b));
        assertRefused(snapshot(List.of(t1, ex.verifierJar())), name, "claims hash");
        // A second, different copy of the description in another entry.
        Path t2 = newFolder();
        Files.createDirectories(t2.resolve("META-INF/joust"));
        Files.writeString(t2.resolve("META-INF/joust/" + name + ".json"), "{\"x-joust\":{}}");
        assertRefused(snapshot(List.of(bank, t2, ex.verifierJar())), name, "two different descriptions");
        // A project entry carrying a blueprint named OutputVerifier (a project class of that name).
        Path t3 = newFolder();
        Files.createDirectories(t3.resolve("META-INF/plutus"));
        Files.createDirectories(t3.resolve("com/evil"));
        Files.write(t3.resolve("META-INF/plutus/OutputVerifier.plutus.json"), Files.readAllBytes(bp));
        Files.writeString(t3.resolve("com/evil/OutputVerifier.class"), "x");
        assertRefused(snapshot(List.of(bank, t3, ex.verifierJar())), name, "another class-path entry holds a blueprint");
        // A second validator class of the same simple name in the validator's entry.
        Path t4 = copy(bank);
        Files.createDirectories(t4.resolve("com/other"));
        Files.writeString(t4.resolve("com/other/BankValidator.class"), "x");
        assertRefused(snapshot(List.of(t4, ex.verifierJar())), name, "classes of that name");
    }

    /** M33A-SEC-1: a dependency shipping the validator's class and a consistent blueprint of its own is refused. */
    public void testADependencyCannotBecomeTheContract() throws Exception {
        Examples ex = examples("testADependencyCannotBecomeTheContract");
        if (ex == null) return;
        Path bank = ex.classes("bank");
        Path evil = newFolder().resolve("evil-dep.jar");
        String cls = "org/julclang/state/examples/bank/BankValidator.class";
        try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(evil))) {
            out.putNextEntry(new java.util.zip.ZipEntry(cls));
            out.write(Files.readAllBytes(bank.resolve(cls)));
            out.closeEntry();
            out.putNextEntry(new java.util.zip.ZipEntry("META-INF/plutus/BankValidator.plutus.json"));
            out.write(Files.readAllBytes(ex.classes("counter").resolve("META-INF/plutus/CounterValidator.plutus.json")));
            out.closeEntry();
        }
        assertRefused(snapshot(List.of(evil, bank, ex.verifierJar())), "org.julclang.state.examples.bank.Bank", "exactly one");
    }

    /**
     * M33A-CDX-35, -CDX-36, -CDX-54, -DES-24: a directory holds what its file system serves under a name. A differently
     * spelled class file or description counts as a copy exactly when the file system resolves the canonical name to it;
     * otherwise the build anchors normally.
     */
    public void testDirectoriesHoldWhatTheirFileSystemServes() throws Exception {
        Examples ex = examples("testDirectoriesHoldWhatTheirFileSystemServes");
        if (ex == null) return;
        Path bank = ex.classes("bank");
        String name = "org.julclang.state.examples.bank.Bank";
        String validator = "org/julclang/state/examples/bank/BankValidator.class";
        // An upper-case suffix.
        Path upper = newFolder();
        Path cls = upper.resolve("org/julclang/state/examples/bank/BankValidator.CLASS");
        Files.createDirectories(cls.getParent());
        Files.copy(bank.resolve(validator), cls);
        expect(Files.isRegularFile(upper.resolve(validator)), snapshot(List.of(upper, bank, ex.verifierJar())), name, "exactly one");
        // A lower-case prefix, with a different description.
        Path lower = newFolder();
        Files.createDirectories(lower.resolve("meta-inf/joust"));
        Files.writeString(lower.resolve("meta-inf/joust/" + name + ".json"), "{\"x-joust\":{\"contract\":\"Other\"}}");
        expect(Files.isRegularFile(lower.resolve("META-INF/joust/" + name + ".json")), snapshot(List.of(lower, bank, ex.verifierJar())),
                name, "two different descriptions");
        // A Unicode spelling (long s), which some file systems fold to s.
        Path longS = newFolder();
        Files.createDirectories(longS.resolve("META-INF/jou\u017ft"));
        Files.writeString(longS.resolve("META-INF/jou\u017ft/" + name + ".json"), "{\"x-joust\":{\"contract\":\"Other\"}}");
        expect(Files.isRegularFile(longS.resolve("META-INF/joust/" + name + ".json")), snapshot(List.of(longS, bank, ex.verifierJar())),
                name, "two different descriptions");
    }

    /** The refusal when the file system serves the variant under the canonical name, normal anchoring when it does not. */
    private static void expect(boolean served, HostSnapshot s, String contract, String why) throws Exception {
        if (served) {
            assertRefused(s, contract, why);
            return;
        }
        try {
            assertNotNull(JoustAnchors.derive(s, contract, 0, 25));
        } finally {
            s.delete();
        }
    }

    /** M33A-CDX-54: a directory serving a protected package under another spelling serves the package. */
    public void testAProtectedPackageIsServedUnderAnotherSpelling() throws Exception {
        Path dir = newFolder();
        Path cls = dir.resolve("ORG/julclang/state/runtime/X.class");
        Files.createDirectories(cls.getParent());
        Files.write(cls, new byte[]{(byte) 0xca, (byte) 0xfe});
        Path other = newFolder();
        Files.writeString(other.resolve("a.txt"), "a");
        HostSnapshot s = snapshot(List.of(dir, other));
        try {
            assertEquals(Files.isDirectory(dir.resolve("org/julclang/state/runtime")), s.servesPackage(0, "org/julclang/state/runtime/"));
            assertFalse(s.servesPackage(1, "org/julclang/state/runtime/"));
        } finally {
            s.delete();
        }
    }

    /** M33A-CDX-55: a version directory with a leading zero is not one the JDK reads; a validator only there is not loaded. */
    public void testAVersionDirectoryWithALeadingZeroIsNotAVersion() throws Exception {
        Examples ex = examples("testAVersionDirectoryWithALeadingZeroIsNotAVersion");
        if (ex == null) return;
        Path bank = ex.classes("bank");
        String name = "org.julclang.state.examples.bank.Bank";
        Path jar = newFolder().resolve("mr.jar");
        var mf = new java.util.jar.Manifest();
        mf.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().putValue("Multi-Release", "true");
        try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(jar), mf)) {
            out.putNextEntry(new java.util.zip.ZipEntry("META-INF/versions/025/org/julclang/state/examples/bank/BankValidator.class"));
            out.write(Files.readAllBytes(bank.resolve("org/julclang/state/examples/bank/BankValidator.class")));
            out.closeEntry();
            for (String r : List.of("META-INF/plutus/BankValidator.plutus.json", "META-INF/joust/" + name + ".json")) {
                out.putNextEntry(new java.util.zip.ZipEntry(r));
                out.write(Files.readAllBytes(bank.resolve(r)));
                out.closeEntry();
            }
        }
        assertRefused(snapshot(List.of(jar, ex.verifierJar())), name, "is not in the build");
    }

    /** M33A-CDX-55, -DES-26: a Multi-Release value read differently across JDKs keeps the snapshot and anchors nothing. */
    public void testAnAmbiguousMultiReleaseValueAnchorsNothing() throws Exception {
        Path jar = newFolder().resolve("mr.jar");
        try (var out = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new java.util.zip.ZipEntry("META-INF/MANIFEST.MF"));
            out.write("Manifest-Version: 1.0\r\nMulti-Release: true  \r\n\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new java.util.zip.ZipEntry("META-INF/versions/21/a/B.class"));
            out.write(new byte[]{(byte) 0xca, (byte) 0xfe});
            out.closeEntry();
        }
        HostSnapshot s = snapshot(List.of(jar));
        try {
            assertFalse(s.ambiguities().isEmpty());
            assertTrue(s.ambiguities().get(0).contains("Multi-Release"));
        } finally {
            s.delete();
        }
    }

    /** M33A-CDX-61: a base and a versioned validator in one jar are two classes of that name to the runtime. */
    public void testABaseAndAVersionedValidatorAreTwo() throws Exception {
        Examples ex = examples("testABaseAndAVersionedValidatorAreTwo");
        if (ex == null) return;
        Path bank = ex.classes("bank");
        String name = "org.julclang.state.examples.bank.Bank";
        String v = "org/julclang/state/examples/bank/BankValidator.class";
        Path jar = newFolder().resolve("mr.jar");
        var mf = new java.util.jar.Manifest();
        mf.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().putValue("Multi-Release", "true");
        try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(jar), mf)) {
            for (String e : List.of(v, "META-INF/versions/21/" + v)) {
                out.putNextEntry(new java.util.zip.ZipEntry(e));
                out.write(Files.readAllBytes(bank.resolve(v)));
                out.closeEntry();
            }
            for (String r : List.of("META-INF/plutus/BankValidator.plutus.json", "META-INF/joust/" + name + ".json")) {
                out.putNextEntry(new java.util.zip.ZipEntry(r));
                out.write(Files.readAllBytes(bank.resolve(r)));
                out.closeEntry();
            }
        }
        assertRefused(snapshot(List.of(jar, ex.verifierJar())), name, "holds 2 classes of that name");
    }

    /**
     * M33A-CDX-62, -DES-25: a sole validator reached through a differently cased package folder is one class, and anchors
     * where the file system serves it.
     */
    public void testASoleValidatorUnderAnotherSpellingIsOne() throws Exception {
        Examples ex = examples("testASoleValidatorUnderAnotherSpellingIsOne");
        if (ex == null) return;
        Path bank = copy(ex.classes("bank"));
        String name = "org.julclang.state.examples.bank.Bank";
        Path from = bank.resolve("org");
        Path tmp = bank.resolve("tmp-rename");
        Files.move(from, tmp);
        Files.move(tmp, bank.resolve("ORG"));
        HostSnapshot s = snapshot(List.of(bank, ex.verifierJar()));
        boolean served = Files.isRegularFile(bank.resolve("org/julclang/state/examples/bank/BankValidator.class"));
        if (!served) {
            assertRefused(s, name, "is not in the build");
            return;
        }
        try {
            assertNotNull(JoustAnchors.derive(s, name, 0, 25));
        } finally {
            s.delete();
        }
    }

    /** M33A-CDX-63: a protected package folder holding only resources serves no JOUST class. */
    public void testAResourceOnlyProtectedPackageIsNotServed() throws Exception {
        Path dir = newFolder();
        Path res = dir.resolve("org/julclang/state/runtime/messages.properties");
        Files.createDirectories(res.getParent());
        Files.writeString(res, "a=b");
        HostSnapshot s = snapshot(List.of(dir));
        try {
            assertFalse(s.servesPackage(0, "org/julclang/state/runtime/"));
        } finally {
            s.delete();
        }
    }

    /** M33A-CDX-68: a protected folder's file counts as a class only where the literal .class name reaches it. */
    public void testOnlyALiteralClassNameServesAProtectedPackage() throws Exception {
        for (String name : List.of("schema.CLASS", "schema.\uff43\uff4c\uff41\uff53\uff53")) {
            Path dir = newFolder();
            Path f = dir.resolve("org/julclang/state/runtime/" + name);
            Files.createDirectories(f.getParent());
            Files.write(f, new byte[]{(byte) 0xca, (byte) 0xfe});
            boolean served = Files.isRegularFile(dir.resolve("org/julclang/state/runtime/schema.class"));
            HostSnapshot s = snapshot(List.of(dir));
            try {
                assertEquals(name, served, s.servesPackage(0, "org/julclang/state/runtime/"));
            } finally {
                s.delete();
            }
        }
    }

    /** M33A-CDX-69: a jar's package-info class in a protected package is a class of that package. */
    public void testAPackageInfoClassCounts() throws Exception {
        Path jar = newFolder().resolve("pi.jar");
        try (var out = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new java.util.zip.ZipEntry("org/julclang/state/runtime/package-info.class"));
            out.write(new byte[]{(byte) 0xca, (byte) 0xfe});
            out.closeEntry();
        }
        HostSnapshot s = snapshot(List.of(jar));
        try {
            assertTrue(s.classesAt(0).contains("org/julclang/state/runtime/package-info.class"));
        } finally {
            s.delete();
        }
    }

    /** M33A-CDX-56: a blueprint with a UTF-8 BOM, which the runtime reads, anchors to the same hash. */
    public void testABlueprintWithAByteOrderMarkAnchors() throws Exception {
        Examples ex = examples("testABlueprintWithAByteOrderMarkAnchors");
        if (ex == null) return;
        Path bank = copy(ex.classes("bank"));
        String name = "org.julclang.state.examples.bank.Bank";
        HostSnapshot plain = snapshot(List.of(bank, ex.verifierJar()));
        String want;
        try {
            want = JoustAnchors.contractScript(plain, name, 25).hash();
        } finally {
            plain.delete();
        }
        Path bp = bank.resolve("META-INF/plutus/BankValidator.plutus.json");
        byte[] b = Files.readAllBytes(bp);
        byte[] bom = new byte[b.length + 3];
        bom[0] = (byte) 0xef;
        bom[1] = (byte) 0xbb;
        bom[2] = (byte) 0xbf;
        System.arraycopy(b, 0, bom, 3, b.length);
        Files.write(bp, bom);
        HostSnapshot s = snapshot(List.of(bank, ex.verifierJar()));
        try {
            assertEquals(want, JoustAnchors.contractScript(s, name, 25).hash());
        } finally {
            s.delete();
        }
    }

    /** M33A-CDX-45: in a jar, BankValidator.CLASS is not the class (jar names are exact), so nothing anchors. */
    public void testAJarEntryWithAnUpperCaseSuffixIsNotAClass() throws Exception {
        Examples ex = examples("testAJarEntryWithAnUpperCaseSuffixIsNotAClass");
        if (ex == null) return;
        Path bank = ex.classes("bank");
        Path jar = newFolder().resolve("upper.jar");
        String name = "org.julclang.state.examples.bank.Bank";
        try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new java.util.zip.ZipEntry("org/julclang/state/examples/bank/BankValidator.CLASS"));
            out.write(Files.readAllBytes(bank.resolve("org/julclang/state/examples/bank/BankValidator.class")));
            out.closeEntry();
            out.putNextEntry(new java.util.zip.ZipEntry("META-INF/plutus/BankValidator.plutus.json"));
            out.write(Files.readAllBytes(bank.resolve("META-INF/plutus/BankValidator.plutus.json")));
            out.closeEntry();
            out.putNextEntry(new java.util.zip.ZipEntry("META-INF/joust/" + name + ".json"));
            out.write(Files.readAllBytes(bank.resolve("META-INF/joust/" + name + ".json")));
            out.closeEntry();
        }
        assertRefused(snapshot(List.of(jar, ex.verifierJar())), name, "is not in the build");
    }

    /** M33A-CDX-7: two blueprints of the validator's name in its own jar are ambiguous. */
    public void testDuplicateBlueprintsInOneJarRefuse() throws Exception {
        Examples ex = examples("testDuplicateBlueprintsInOneJarRefuse");
        if (ex == null) return;
        Path jar = newFolder().resolve("dup.jar");
        try (var in = getClass().getResourceAsStream("/joust-m33a/duplicate-blueprint.jar")) {
            Files.copy(in, jar);
        }
        HostSnapshot s;
        try {
            s = snapshot(List.of(jar, ex.verifierJar()));
        } catch (HostSpec.Invalid refusedEarlier) {
            return;   // the snapshot itself refuses the ambiguous jar: as good
        }
        assertRefused(s, "org.julclang.state.examples.bank.Bank", "blueprints of that name");
    }

    /** M33A-CDX-8: a versioned copy counts only where the launch JVM would load it. */
    public void testMultiReleaseCopiesResolveForTheLaunchJdk() throws Exception {
        Examples ex = examples("testMultiReleaseCopiesResolveForTheLaunchJdk");
        if (ex == null) return;
        Path bank = ex.classes("bank");
        String cls = "org/julclang/state/examples/bank/BankValidator.class";
        Path mr = newFolder().resolve("mr.jar");
        java.util.jar.Manifest mf = new java.util.jar.Manifest();
        mf.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().putValue("Multi-Release", "true");
        ObjectNode forged = (ObjectNode) HostJson.MAPPER.readTree(Files.readAllBytes(bank.resolve("META-INF/plutus/BankValidator.plutus.json")));
        forged.put("hash", "00".repeat(28));
        try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(mr), mf)) {
            out.putNextEntry(new java.util.zip.ZipEntry("META-INF/versions/26/" + cls));
            out.write(Files.readAllBytes(bank.resolve(cls)));
            out.closeEntry();
            out.putNextEntry(new java.util.zip.ZipEntry("META-INF/plutus/BankValidator.plutus.json"));
            out.write(HostJson.write(forged));
            out.closeEntry();
        }
        String name = "org.julclang.state.examples.bank.Bank";
        HostSnapshot s = snapshot(List.of(mr, bank, ex.verifierJar()));
        assertEquals("on Java 25 the base class in the next entry is used", 1, s.firstWithClass(cls, 25));
        assertEquals("on Java 26 the jar's copy wins", 0, s.firstWithClass(cls, 26));
        // The validator's class is now in two entries, which refuses under any JDK (M33A-SEC-1).
        for (int feature : new int[]{25, 26}) {
            try {
                JoustAnchors.derive(s, name, 0, feature);
                fail("a validator class in two entries was anchored");
            } catch (JoustAnchors.Refused expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("exactly one"));
            }
        }
        s.delete();
    }

    /** I24: the recomputed Counter hash equals the host's (the fixture's real launch spec, F38). */
    public void testTheFixtureCounterHashIsTheHosts() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "theFixtureCounterHashIsTheHosts");
        if (fixture == null) return;
        HostSpec spec = HostSpec.read(Path.of(fixture));
        HostSnapshot s = HostSnapshot.create(spec, newFolder());
        JoustAnchors.Anchors a = JoustAnchors.derive(s, "org.example.counter.Counter", 0, 25);
        assertEquals("5de2e73fb4674a846230cb548335f0e095c3997043fcedfc7fc9c4ed", a.contract().hash());
        assertTrue(a.contract().address().startsWith("addr_test1w"));
        s.delete();
    }

    private static void assertRefused(HostSnapshot s, String contract, String why) {
        try {
            JoustAnchors.derive(s, contract, 0, 25);
            fail("derived anchors from an ambiguous or tampered build (" + why + ")");
        } catch (JoustAnchors.Refused e) {
            assertTrue(e.getMessage(), e.getMessage().contains(why));
        } finally {
            s.delete();
        }
    }

    private Path copy(Path from) throws Exception {
        Path to = newFolder();
        try (var w = Files.walk(from)) {
            for (Path p : (Iterable<Path>) w::iterator) {
                Path d = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(d);
                else Files.copy(p, d);
            }
        }
        return to;
    }
}
