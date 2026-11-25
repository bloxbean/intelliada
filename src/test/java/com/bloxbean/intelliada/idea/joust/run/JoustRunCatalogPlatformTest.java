package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostSnapshot;
import com.bloxbean.intelliada.idea.joust.verifier.SnapshotOf;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * M33A-DES-18: the catalog check over real snapshots of JOUST's Bank example (tool-gated on JOUST_EXAMPLES): read-only
 * when a contract cannot be anchored, the host's discovery rule, the status rules, and repeats IntelliAda never reads.
 */
public class JoustRunCatalogPlatformTest extends BasePlatformTestCase {

    private static final String BANK = "org.julclang.state.examples.bank.Bank";
    private static final String HASH = "c3acc5ef93aca54c379dbe03524f74254f02581a5956c92fa1adc671";

    private Path root, verifierJar;

    private boolean tools(String test) throws Exception {
        String r = JoustTestTools.env("JOUST_EXAMPLES", test);
        if (r == null) return false;
        root = Path.of(r);
        Path jar = root.resolve("julc-state-verifier/build/libs/julc-state-verifier-0.1.0-pre3-01bad1f-SNAPSHOT.jar");
        if (Files.isRegularFile(jar)) verifierJar = jar;   // a missing folder is a missing jar (M33A-CDX-53)
        if (verifierJar == null) {
            Path libs = root.resolve("julc-state-verifier/build/libs");
            if (JoustTestTools.required()) throw new AssertionError(test + " needs julc-state-verifier-0.1.0-pre3-01bad1f-SNAPSHOT.jar in " + libs);
            System.out.println("JOUST-SKIPPED " + test + ": no verifier jar in " + libs);   // M33A-CDX-44
            return false;
        }
        return true;
    }

    private static ObjectNode init(String status, String value) {
        ObjectNode n = HostJson.object();
        ObjectNode c = n.putArray("contracts").addObject().put("name", BANK);
        c.putObject("scriptHashes").put("spend", HASH).put("mint", HASH);
        if (status.equals("served")) c.put("served", true);
        else if (status.equals("unsupported")) c.put("unsupported", value);
        else if (status.equals("both")) c.put("served", true).put("unsupported", value);
        return n;
    }

    private HostSnapshot snap(List<Path> entries) throws Exception {
        return SnapshotOf.create(entries, Files.createTempDirectory("catalog")).snapshot();
    }

    public void testTheCatalogRules() throws Exception {
        if (!tools("testTheCatalogRules")) return;
        Path bank = root.resolve("examples/bank/build/classes/java/main");
        HostSnapshot clean = snap(List.of(bank, verifierJar));
        var c = JoustRunSession.catalog(init("served", null), clean, 0, 25);
        assertNull(c.conflict());
        assertTrue(c.unanchored().isEmpty());
        // A description without x-joust is not a contract (the host's rule).
        Path types = Files.createTempDirectory("types");
        Files.createDirectories(types.resolve("META-INF/joust"));
        Files.writeString(types.resolve("META-INF/joust/lib.Types.json"), "﻿{}");
        assertNull("ignored, BOM included", JoustRunSession.catalog(init("served", null), snap(List.of(bank, types, verifierJar)), 0, 25).conflict());
        // The validator's class in a second entry: read-only, not a conflict.
        Path dup = Files.createTempDirectory("dup");
        Path cls = Path.of("org/julclang/state/examples/bank/BankValidator.class");
        Files.createDirectories(dup.resolve(cls).getParent());
        Files.copy(bank.resolve(cls), dup.resolve(cls));
        var ro = JoustRunSession.catalog(init("served", null), snap(List.of(bank, dup, verifierJar)), 0, 25);
        assertNull("not a conflict: " + ro.conflict(), ro.conflict());
        assertFalse("but signing is off", ro.unanchored().isEmpty());
        // Status rules: another contract-level reason is read-only; served and unsupported together, or neither, conflict.
        var other = JoustRunSession.catalog(init("unsupported", "authorizedBy"), clean, 0, 25);
        assertNull(other.conflict());
        assertTrue(other.unanchored().get(0).contains("authorizedBy"));
        assertNotNull(JoustRunSession.catalog(init("both", "x"), clean, 0, 25).conflict());
        assertNotNull(JoustRunSession.catalog(init("none", null), clean, 0, 25).conflict());
        assertNotNull("a non-parameterized contract reported as params",
                JoustRunSession.catalog(init("unsupported", "params"), clean, 0, 25).conflict());
        // A jar repeating only an entry IntelliAda never reads is accepted at snapshot time.
        Path lic = Files.createTempDirectory("lic").resolve("license-repeat.jar");
        try (var in = getClass().getResourceAsStream("/joust-m33a/license-repeat.jar")) {
            Files.copy(in, lic);
        }
        assertNull(JoustRunSession.catalog(init("served", null), snap(List.of(bank, lic, verifierJar)), 0, 25).conflict());
    }
}
