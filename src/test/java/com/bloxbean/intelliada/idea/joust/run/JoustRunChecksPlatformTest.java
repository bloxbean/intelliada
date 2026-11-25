package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.JoustHostService;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.bloxbean.intelliada.idea.joust.verifier.JoustAnchors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * ADR-005 D13 in the live session (M33A-CDX-1, -CDX-2): the host's catalog is checked at launch, and a deployment that
 * differs from the derived one stops the host before prerequisites are sent. Tool-gated on the fixture and the DevKit.
 */
public class JoustRunChecksPlatformTest extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            JoustHostService.confirmForTests = null;
        } finally {
            super.tearDown();
        }
    }

    public void testTheCatalogAndTheDeploymentAreCheckedInTheSession() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "testTheCatalogAndTheDeploymentAreChecked");
        String store = JoustTestTools.env("JOUST_DEVKIT_STORE", "testTheCatalogAndTheDeploymentAreChecked");
        if (fixture == null || store == null) return;
        TrustedProjects.setProjectTrusted(getProject(), true);
        JoustHostService.confirmForTests = (project, dir, spec, version) -> true;
        RemoteNode node = new RemoteNode();
        node.setId("devkit-checks-test");
        node.setName("DevKit");
        node.setNodeType(NodeType.LocalYaciDevKit);
        node.setApiEndpoint(store);
        node.setProtocolMagic("42");
        RemoteNodeState.getInstance().addRemoteNode(node);
        JoustRunSession s = null;
        try {
            JoustRunGuard guard = JoustRunGuard.capture(getProject());
            // The launch path verifies the real host's catalog (it agrees).
            s = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() ->
                    JoustRunSessionAccess.launch(getProject(), guard, node, Path.of(fixture))), 120_000);
            JoustRunSession live = s;
            int java = live.javaFeature();
            JsonNode init = live.running().initialize();
            assertNull(JoustRunSession.catalogDifference(init, live.running().snapshot(), 0, java));
            // A wrong hash, an omitted contract and an extra one are each a difference (M33A-CDX-2).
            ObjectNode wrong = init.deepCopy();
            ((ObjectNode) wrong.at("/contracts/0/scriptHashes")).put("spend", "00".repeat(28));
            assertNotNull(JoustRunSession.catalogDifference(wrong, live.running().snapshot(), 0, java));
            ObjectNode omitted = init.deepCopy();
            ((ArrayNode) omitted.get("contracts")).removeAll();
            assertNotNull(JoustRunSession.catalogDifference(omitted, live.running().snapshot(), 0, java));
            ObjectNode extra = init.deepCopy();
            ((ArrayNode) extra.get("contracts")).addObject().put("name", "org.evil.Extra").putObject("scriptHashes").put("unapplied", "11".repeat(28));
            assertNotNull(JoustRunSession.catalogDifference(extra, live.running().snapshot(), 0, java));
            // A deployment that differs from the derived one stops the host before prerequisites (M33A-CDX-1).
            String contract = init.path("contracts").get(0).path("name").textValue();
            JoustAnchors.Anchors real = live.anchors(contract);
            JoustAnchors.Script forged = new JoustAnchors.Script(real.contract().language(), "00".repeat(28), real.contract().address(),
                    real.contract().reward());
            live.anchorsForTests(contract, new JoustAnchors.Anchors(real.description(), real.networkId(), forged, real.outputVerifier(),
                    real.mapVerifier(), real.validatorEntry(), real.outputVerifierEntry(), real.mapVerifierEntry()));
            try {
                PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() -> live.prerequisites(contract)), 60_000);
                fail("prerequisites were sent after a differing deployment");
            } catch (Throwable expected) {
                assertTrue(String.valueOf(expected), String.valueOf(expected).contains("host was stopped"));
            }
            assertTrue("the host was stopped", live.running().process().waitFor(20, TimeUnit.SECONDS));
            assertNotNull(live.running().session().ended());
        } finally {
            RemoteNodeState.getInstance().removeRemoteNode(node);
            if (s != null) {
                JoustHostService.getInstance(getProject()).stop(Path.of(fixture));
                s.running().process().waitFor(20, TimeUnit.SECONDS);
            }
        }
    }
}
