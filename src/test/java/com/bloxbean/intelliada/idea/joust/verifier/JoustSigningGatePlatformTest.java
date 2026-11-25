package com.bloxbean.intelliada.idea.joust.verifier;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostSnapshot;
import com.bloxbean.intelliada.idea.joust.host.HostSpec;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** M33A-DES-3: the composed gate on the real fixture launch set (tool-gated on JOUST_HOST_FIXTURE). */
public class JoustSigningGatePlatformTest extends BasePlatformTestCase {

    public void testTheFixtureMaySignAndAShadowedVerifierMayNot() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "testTheFixtureMaySign");
        if (fixture == null) return;
        HostSpec spec = HostSpec.read(Path.of(fixture));
        HostSnapshot s = HostSnapshot.create(spec, Files.createTempDirectory("gate"));
        try {
            ObjectNode init = HostJson.object();
            init.putObject("versions").putObject("engine").put("joust", "0.1.0-pre3-01bad1f-SNAPSHOT");
            var gate = JoustSigningGate.evaluate(JoustVersionPolicy.reviewed(), spec, s, init, List.of("org.example.counter.Counter"), 0, 25);
            assertTrue(gate.why(), gate.maySign());
            assertEquals("5de2e73fb4674a846230cb548335f0e095c3997043fcedfc7fc9c4ed", gate.anchors().get("org.example.counter.Counter").contract().hash());
            var other = JoustSigningGate.evaluate(JoustVersionPolicy.reviewed(), spec, s, init, List.of("org.example.counter.Missing"), 0, 25);
            assertFalse(other.maySign());
        } finally {
            s.delete();
        }
    }
}
