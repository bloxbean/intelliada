package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.JoustHostService;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ADR-005 M3.1b end to end (tool-gated on {@code JOUST_HOST_FIXTURE} and a running Yaci DevKit at
 * {@code JOUST_DEVKIT_STORE}): bind the DevKit node configuration (D14), start the project's host with the binding's
 * parameters (D11, D14b), and read through it (D15, D18), with a monotone {@code now} (I28).
 */
public class JoustRunDevKitPlatformTest extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            JoustHostService.confirmForTests = null;
        } finally {
            super.tearDown();
        }
    }

    public void testTheReadOnlyPanelSessionReadsTheDevKit() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "testTheReadOnlyPanelSessionReadsTheDevKit");
        String store = JoustTestTools.env("JOUST_DEVKIT_STORE", "testTheReadOnlyPanelSessionReadsTheDevKit");
        if (fixture == null || store == null) return;
        TrustedProjects.setProjectTrusted(getProject(), true);
        JoustHostService.confirmForTests = (project, dir, spec, version) -> true;
        RemoteNode node = new RemoteNode();
        node.setId("devkit-test");
        node.setName("DevKit");
        node.setNodeType(NodeType.LocalYaciDevKit);
        node.setApiEndpoint(store);
        node.setProtocolMagic("42");
        // I14 through bind and launch (M31B-CDX-27, -34): this test owns the project network it writes, whose start
        // command would leave a sentinel; a fixture that brings its own joust.config.json is refused, not skipped.
        Path config = Path.of(fixture, "joust.config.json");
        assertFalse("the fixture must not carry a joust.config.json of its own", java.nio.file.Files.exists(config));
        Path sentinel = java.nio.file.Files.createTempDirectory("joust-i14").resolve("started");
        var nodes = com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState.getInstance();
        JoustRunSession s = null;
        try {
            nodes.addRemoteNode(node);   // a stored configuration, as the panel binds one
            java.nio.file.Files.writeString(config, "{\"network\":\"x\",\"networks\":{\"x\":{\"kind\":\"devkit\","
                    + "\"url\":\"http://127.0.0.1:1/api/v1\",\"start\":\"touch " + sentinel + "\"}}}");
            JoustRunGuard guard = JoustRunGuard.capture(getProject());
            s = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() -> {
                JoustRunSession.Binding b = JoustRunSession.bind(node);
                JoustHostService.Running r = JoustHostService.getInstance(getProject()).launch(guard, Path.of(fixture),
                        JoustRunSession.initParams(b.identity()), b.key(), JoustRunSession.reads(b), e -> { });
                return new JoustRunSession(b, r);
            }), 120_000);
            JoustRunSession live = s;
            String contract = live.running().initialize().path("contracts").get(0).path("name").textValue();
            JsonNode calls = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() -> live.describe(contract)), 60_000);
            assertTrue(calls.toString(), calls.path("calls").isArray());
            JsonNode dep = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() -> live.deployment(contract)), 60_000);
            assertTrue(dep.path("addresses").path("contract").textValue().startsWith("addr_test1w"));
            JsonNode pre = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() -> live.prerequisites(contract)), 60_000);
            assertTrue(pre.isArray());
            JsonNode list = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() -> live.instances(contract, 10)), 60_000);
            assertTrue(list.has("items"));
            long n1 = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(live::now), 30_000);
            long n2 = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(live::now), 30_000);
            assertTrue("now never decreases (I28)", n2 >= n1);
            // Two panel sessions on one host share its clock: calls interleaved from both reach the host with a
            // non-decreasing now, or the host would answer bad/nowDecreased (M31B-CDX-18).
            JoustRunSession s2 = new JoustRunSession(live.binding(), live.running());
            assertSame(live.clockForTests(), s2.clockForTests());
            List<java.util.concurrent.Future<JsonNode>> calls2 = new java.util.ArrayList<>();
            for (int i = 0; i < 12; i++) {
                JoustRunSession w = i % 2 == 0 ? live : s2;
                calls2.add(ApplicationManager.getApplication().executeOnPooledThread(() -> w.deployment(contract)));
            }
            for (var f : calls2) assertTrue(PlatformTestUtil.waitForFuture(f, 120_000).has("addresses"));
            assertFalse("the project's start command never ran (I14)", java.nio.file.Files.exists(sentinel));
        } finally {
            // Every setup step is undone, whichever failed (M31B-CDX-34).
            java.nio.file.Files.deleteIfExists(config);
            nodes.removeRemoteNode(node);
            if (s != null) {
                JoustHostService.getInstance(getProject()).stop(Path.of(fixture));
                assertTrue(s.running().process().waitFor(20, TimeUnit.SECONDS));
            }
        }
    }

    public void testANonLoopbackOrPublicNodeIsNotBound() {
        RemoteNode node = new RemoteNode();
        node.setNodeType(NodeType.LocalYaciDevKit);
        node.setApiEndpoint("http://192.0.2.10:8080/api/v1");
        try {
            JoustRunSession.bind(node);
            fail("a non-loopback devnet was bound");
        } catch (Exception expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("loopback"));
        }
        RemoteNode bf = new RemoteNode();
        bf.setNodeType(NodeType.BLOCKFROST_PREPROD);
        try {
            JoustRunSession.bind(bf);
            fail("a public network was bound in M3.1b");
        } catch (Exception expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("later"));
        }
    }
}
