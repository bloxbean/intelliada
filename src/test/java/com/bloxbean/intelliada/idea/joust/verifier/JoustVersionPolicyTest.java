package com.bloxbean.intelliada.idea.joust.verifier;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/** ADR-005 D12 (L31), I29: the panel signs only with a reviewed JOUST, else it stays read-only. */
public class JoustVersionPolicyTest {

    private static final String LABEL = "0.1.0-pre3-01bad1f-SNAPSHOT";

    /** The local kit's five runtime jars (tool-gated: the reviewed JOUST in ~/.m2). */
    private static List<Path> kitJars(String test) {
        List<Path> out = new ArrayList<>();
        for (String a : JoustVersionPolicy.ARTIFACTS) {
            Path p = Path.of(System.getProperty("user.home"), ".m2/repository/org/julclang", a, LABEL, a + "-" + LABEL + ".jar");
            if (!Files.isRegularFile(p)) {
                if (JoustTestTools.required()) throw new AssertionError(test + " needs the reviewed JOUST kit in ~/.m2");
                System.out.println("JOUST-SKIPPED " + test);
                return null;
            }
            out.add(p);
        }
        return out;
    }

    private static JsonNode init(String engine) {
        ObjectNode n = HostJson.object();
        n.putObject("versions").putObject("engine").put("joust", engine);
        return n;
    }

    private static JsonNode desc(int version) {
        ObjectNode d = HostJson.object();
        d.putObject("x-joust").put("version", version);
        return d;
    }

    @Test
    public void theBuiltInSetIsWellFormed() {
        var sets = JoustVersionPolicy.reviewed();
        assertEquals(1, sets.size());
        assertEquals(LABEL, sets.get(0).label());
        assertEquals(Set.of(2), sets.get(0).xJoustVersions());
    }

    @Test
    public void theReviewedJarsMaySignAndEveryDeviationIsReadOnly() throws Exception {
        List<Path> jars = kitJars("theReviewedJarsMaySignAndEveryDeviationIsReadOnly");
        if (jars == null) return;
        var sets = JoustVersionPolicy.reviewed();
        var ok = SnapshotOf.create(jars, Files.createTempDirectory("policy"));
        assertTrue(JoustVersionPolicy.check(sets, ok.spec(), ok.snapshot(), init(LABEL), 25, List.of(desc(2))).maySign());
        // I29: a bumped x-joust.version.
        assertFalse(JoustVersionPolicy.check(sets, ok.spec(), ok.snapshot(), init(LABEL), 25, List.of(desc(3))).maySign());
        // I29: the host reporting another engine version than the runtime jar's RuntimeVersion.
        assertFalse(JoustVersionPolicy.check(sets, ok.spec(), ok.snapshot(), init("0.1.0-other"), 25, List.of(desc(2))).maySign());
        // I29: a runtime jar whose hash is not reviewed.
        var other = new JoustVersionPolicy.ReviewedSet(LABEL, Map.of("julc-state-api", "00".repeat(32), "julc-state-runtime",
                "00".repeat(32), "julc-state-serve", "00".repeat(32), "julc-state-host", "00".repeat(32), "julc-state-verifier",
                "00".repeat(32)), Set.of(2));
        assertFalse(JoustVersionPolicy.check(List.of(other), ok.spec(), ok.snapshot(), init(LABEL), 25, List.of(desc(2))).maySign());
        ok.snapshot().delete();
        // I29: an earlier launch entry holding a same-version copy of the runtime's classes (a dir runtime, a shaded jar).
        Path shadow = Files.createTempDirectory("shadow");
        Files.createDirectories(shadow.resolve("org/julclang/state/runtime"));
        Files.writeString(shadow.resolve("org/julclang/state/runtime/RuntimeVersion.class"), "x");
        List<Path> shadowed = new ArrayList<>();
        shadowed.add(shadow);
        shadowed.addAll(jars);
        var sh = SnapshotOf.create(shadowed, Files.createTempDirectory("policy"));
        var v = JoustVersionPolicy.check(sets, sh.spec(), sh.snapshot(), init(LABEL), 25, List.of(desc(2)));
        assertFalse(v.maySign());
        assertTrue(v.why(), v.why().contains("another class-path entry"));
        sh.snapshot().delete();
    }
}
