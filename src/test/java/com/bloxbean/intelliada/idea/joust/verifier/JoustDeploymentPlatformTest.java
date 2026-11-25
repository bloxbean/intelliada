package com.bloxbean.intelliada.idea.joust.verifier;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.JoustHostService;
import com.bloxbean.intelliada.idea.joust.run.JoustRunSessionAccess;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** ADR-005 D13 "Deployment check" (I24): the host's deployment must equal what IntelliAda derives from the snapshot. */
public class JoustDeploymentPlatformTest extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            JoustHostService.confirmForTests = null;
        } finally {
            super.tearDown();
        }
    }

    /** The real host's answer for the fixture Counter, against anchors derived from its own verified snapshot. */
    public void testTheHostsDeploymentEqualsTheDerivedOneAndMutationsRefuse() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "testTheHostsDeploymentEqualsTheDerivedOne");
        String store = JoustTestTools.env("JOUST_DEVKIT_STORE", "testTheHostsDeploymentEqualsTheDerivedOne");
        if (fixture == null || store == null) return;
        TrustedProjects.setProjectTrusted(getProject(), true);
        JoustHostService.confirmForTests = (project, dir, spec, version) -> true;
        RemoteNode node = new RemoteNode();
        node.setId("devkit-deployment-test");
        node.setName("DevKit");
        node.setNodeType(NodeType.LocalYaciDevKit);
        node.setApiEndpoint(store);
        node.setProtocolMagic("42");
        RemoteNodeState.getInstance().addRemoteNode(node);
        JoustHostService.Running r = null;
        try {
            JoustRunGuard guard = JoustRunGuard.capture(getProject());
            var launched = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() ->
                    JoustRunSessionAccess.launch(getProject(), guard, node, Path.of(fixture))), 120_000);
            r = launched.running();
            String contract = r.initialize().path("contracts").get(0).path("name").textValue();
            JsonNode dep = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() ->
                    launched.deployment(contract)), 60_000);
            JoustAnchors.Anchors a = JoustAnchors.derive(r.snapshot(), contract, 0);
            assertNull("the host's deployment is the derived one: " + dep, JoustDeployment.check(a, dep));
            // Every field the check compares refuses when changed.
            for (String ptr : new String[]{"/addresses/contract", "/policies/contract", "/scriptHashes/spend", "/scriptHashes/mint"}) {
                ObjectNode m = dep.deepCopy();
                String field = ptr.substring(ptr.lastIndexOf('/') + 1);
                ((ObjectNode) m.at(ptr.substring(0, ptr.lastIndexOf('/')))).put(field, field.equals("contract") && ptr.startsWith("/addresses")
                        ? "addr_test1wz" + "q".repeat(51) : "ab".repeat(28));
                assertNotNull("changed " + ptr, JoustDeployment.check(a, m));
            }
            ObjectNode extraHash = dep.deepCopy();
            ((ObjectNode) extraHash.get("scriptHashes")).put("stake", a.contract().hash());
            assertNotNull("a stake hash the build does not imply", JoustDeployment.check(a, extraHash));
            ObjectNode gov = dep.deepCopy();
            gov.putArray("governors").add("x");
            assertNotNull(JoustDeployment.check(a, gov));
            ObjectNode refs = dep.deepCopy();
            ((ObjectNode) refs.get("referenceScripts")).put("other", "ab".repeat(32) + "#0");
            assertNotNull(JoustDeployment.check(a, refs));
            ObjectNode extra = dep.deepCopy();
            extra.put("note", "x");
            assertNotNull(JoustDeployment.check(a, extra));
        } finally {
            RemoteNodeState.getInstance().removeRemoteNode(node);
            if (r != null) {
                JoustHostService.getInstance(getProject()).stop(Path.of(fixture));
                assertTrue(r.process().waitFor(20, TimeUnit.SECONDS));
            }
        }
    }

    /** The expected deployment follows the description: no stake or verifier fields for a plain contract. */
    public void testThePlainContractShape() throws Exception {
        ObjectNode desc = HostJson.object();
        desc.putObject("x-joust").put("stakeSelf", false).putArray("actions").addObject().putObject("returns").put("sends", false);
        desc.putObject("definitions");
        JoustAnchors.Script s = new JoustAnchors.Script("PlutusScriptV3", "ab".repeat(28), "addr_test1w", "stake_test1u");
        JoustAnchors.Anchors a = new JoustAnchors.Anchors(desc, s, "stake_test1u", s, null, 0, 1);
        ObjectNode e = JoustDeployment.expected(a);
        assertFalse(e.get("addresses").has("reward"));
        assertFalse(e.get("scriptHashes").has("stake"));
        assertFalse(e.get("addresses").has("outputVerifier"));
        ((ObjectNode) desc.get("x-joust")).put("stakeSelf", true);
        ((ObjectNode) desc.at("/x-joust/actions/0/returns")).put("sends", true);
        ObjectNode e2 = JoustDeployment.expected(a);
        assertEquals("stake_test1u", e2.at("/addresses/reward").textValue());
        assertTrue(e2.get("scriptHashes").has("outputVerifier"));
        assertTrue(e2.get("addresses").has("outputVerifierReward"));
    }
}
