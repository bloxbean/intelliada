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
            JoustAnchors.Anchors a = JoustAnchors.derive(s, name, 0);
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

    /** I24: the recomputed Counter hash equals the host's (the fixture's real launch spec, F38). */
    public void testTheFixtureCounterHashIsTheHosts() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "theFixtureCounterHashIsTheHosts");
        if (fixture == null) return;
        HostSpec spec = HostSpec.read(Path.of(fixture));
        HostSnapshot s = HostSnapshot.create(spec, newFolder());
        JoustAnchors.Anchors a = JoustAnchors.derive(s, "org.example.counter.Counter", 0);
        assertEquals("5de2e73fb4674a846230cb548335f0e095c3997043fcedfc7fc9c4ed", a.contract().hash());
        assertTrue(a.contract().address().startsWith("addr_test1w"));
        s.delete();
    }

    private static void assertRefused(HostSnapshot s, String contract, String why) {
        try {
            JoustAnchors.derive(s, contract, 0);
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
