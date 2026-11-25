package com.bloxbean.intelliada.idea.joust.verifier;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ADR-005 I23 (M3.3a): recorded host results fed through IntelliAda's own input assembly, with anchors derived from
 * snapshots of JOUST's examples and chain facts from a recorded node, equal the kit's vectors and get the vectors'
 * verdicts from the bundled checker: every accepted vector is accepted and one rejection per rule family is refused
 * with its codes. Tool-gated on JOUST_EXAMPLES, the bundled checker and a JDK 25.
 */
public class JoustInputAssemblyPlatformTest extends BasePlatformTestCase {

    /** A node that answers what the vector recorded. */
    private record Recorded(JsonNode v) implements JoustCheckInput.Chain {
        @Override
        public @Nullable JsonNode output(String ref) {
            return v.path("resolved").get(ref);
        }

        @Override
        public List<String> scriptsAt(String address) {
            List<String> out = new ArrayList<>();
            v.path("node").path("scriptsAt").path(address).forEach(h -> out.add(h.textValue()));
            return out;
        }

        @Override
        public @Nullable Boolean registered(String scriptHash) {
            JsonNode r = v.path("node").path("registrations").get(scriptHash);
            return r == null || !r.isBoolean() ? null : r.booleanValue();
        }

        @Override
        public JsonNode protocolParams() {
            return v.get("protocolParams");
        }
    }

    private static String abbreviate(JsonNode n) {
        String t = String.valueOf(n);
        return t.length() > 160 ? t.substring(0, 160) + "…" : t;
    }

    public void testRecordedResultsThroughOurAssemblyGetTheVectorsVerdicts() throws Exception {
        String root = JoustTestTools.env("JOUST_EXAMPLES", "testRecordedResultsThroughOurAssembly");
        if (root == null) return;
        String dir = System.getProperty("joust.verifier.dir", ""), java = System.getProperty("joust.lint.java", "");
        Path kit = null;
        for (String p : System.getProperty("joust.conformance.classpath", "").split(File.pathSeparator)) {
            if (p.endsWith("-conformance.jar")) kit = Path.of(p);
        }
        JoustChecker.Manifest m;
        try (InputStream in = JoustChecker.class.getResourceAsStream(JoustChecker.MANIFEST)) {
            m = JoustChecker.Manifest.parse(in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertTrue("the bundled checker, a JDK 25 and the kit", m.bundled() && !java.isEmpty() && kit != null);
        JoustChecker checker = new JoustChecker(m, Path.of(dir), Path.of(java));
        Path verifierJar = null;
        try (var l = Files.list(Path.of(root, "julc-state-verifier/build/libs"))) {
            for (Path p : (Iterable<Path>) l::iterator) {
                String n = p.getFileName().toString();
                if (n.matches("julc-state-verifier-0\\.1\\.0-pre3-01bad1f-SNAPSHOT\\.jar")) verifierJar = p;
            }
        }
        assertNotNull(verifierJar);
        Map<String, JoustAnchors.Anchors> anchors = new HashMap<>();
        for (String[] c : new String[][]{{"bank", "Bank"}, {"counter", "Counter"}, {"token", "Token"}, {"registry", "Registry"}, {"dex", "Dex"}}) {
            String name = "org.julclang.state.examples." + c[0] + "." + c[1];
            var s = SnapshotOf.create(List.of(Path.of(root, "examples/" + c[0] + "/build/classes/java/main"), verifierJar),
                    Files.createTempDirectory("assembly"));
            anchors.put(name, JoustAnchors.derive(s.snapshot(), name, 0));
            s.snapshot().delete();
        }
        List<JsonNode> vectors = new ArrayList<>();
        try (ZipFile z = new ZipFile(kit.toFile())) {
            for (ZipEntry e : Collections.list(z.entries())) {
                if (e.getName().startsWith("verifier/") && e.getName().endsWith(".json")) vectors.add(HostJson.MAPPER.readTree(z.getInputStream(e)));
            }
        }
        int accepted = 0, families = 0, assemblyRefusals = 0;
        java.util.Set<String> seenFamilies = new java.util.HashSet<>();
        List<String> problems = new ArrayList<>();
        for (JsonNode v : vectors) {
            JsonNode d = v.path("anchors").path("description");
            String name = d.path("x-joust").path("package").asText() + "." + d.path("x-joust").path("contract").asText();
            JoustAnchors.Anchors a = anchors.get(name);
            if (a == null || !a.toJson().equals(v.get("anchors"))) continue;   // another contract, or anchors mutated on purpose
            List<String> want = new ArrayList<>();
            v.path("violations").forEach(x -> want.add(x.asText()));
            String family = want.isEmpty() ? null : want.get(0).split("/")[0];
            if (family != null && !seenFamilies.add(family)) continue;   // one rejection per rule family
            ObjectNode in;
            try {
                in = JoustCheckInput.assemble(v.path("method").asText(), (ObjectNode) v.get("request"), a,
                        v.path("networkId").asInt(), Long.parseLong(v.path("maxFee").asText()), v.get("result"), new Recorded(v),
                        v.get("clock") instanceof ObjectNode c ? c : null);
            } catch (JoustCheckInput.Refused r) {
                // The assembly itself refuses (an input the node does not have): fail-closed before the checker runs.
                if (want.isEmpty()) problems.add(v.path("source").asText() + ": an accepted vector was refused by the assembly: " + r.getMessage());
                else {
                    families++;
                    assemblyRefusals++;
                }
                continue;
            }
            // Equal to the vector, except that the recorder also kept unspent wallet outputs in resolved, which the
            // checker never reads: ours holds exactly the vector's entries for the refs the body uses.
            ObjectNode stripped = (ObjectNode) HostJson.parse(HostJson.write(v));
            // orderedReads is replay evidence the checker does not read, so IntelliAda does not send it (D13).
            // Only the checker's input fields: the rest is the generator's metadata (violations, source, check, mutation).
            stripped.retain(List.of("method", "request", "networkId", "maxFee", "anchors", "resolved", "node", "protocolParams",
                    "result", "clock"));
            // As the checker receives it: serialized and parsed (numbers compare by value).
            ObjectNode mine = (ObjectNode) HostJson.parse(HostJson.write(in));
            JsonNode theirs = stripped.remove("resolved"), ours = mine.remove("resolved");
            boolean subset = true;
            for (var it = ours.fields(); it.hasNext(); ) {
                var f = it.next();
                subset &= f.getValue().equals(theirs.get(f.getKey()));
            }
            if (!mine.equals(stripped) || !subset) {
                List<String> diff = new ArrayList<>();
                for (var it = stripped.fieldNames(); it.hasNext(); ) {
                    String k = it.next();
                    if (!stripped.get(k).equals(mine.get(k))) diff.add(k + " want " + abbreviate(stripped.get(k)) + " got " + abbreviate(mine.get(k)));
                }
                for (var it = mine.fieldNames(); it.hasNext(); ) {
                    String k = it.next();
                    if (!stripped.has(k)) diff.add("extra " + k);
                }
                if (!subset) diff.add("resolved");
                problems.add(v.path("source").asText() + ": our assembly differs from the vector: " + diff);
                continue;
            }
            JoustChecker.Verdict got = checker.check(in);
            List<String> codes = got.accepted() ? List.of() : ((JoustChecker.Refused) got).codes();
            if (!codes.equals(want) || got.accepted() != want.isEmpty()) {
                problems.add(v.path("source").asText() + ": want " + want + ", got " + got);
            }
            if (want.isEmpty()) accepted++;
            else families++;
        }
        assertTrue(problems.size() + " problems, e.g. " + problems.stream().limit(4).toList(), problems.isEmpty());
        assertTrue("accepted " + accepted, accepted >= 40);
        assertTrue("rule families " + families, families >= 10);
        System.out.println("I23: " + accepted + " accepted vectors and " + families + " rule families (" + assemblyRefusals
                + " refused already by the assembly) through IntelliAda's assembly");
    }
}
