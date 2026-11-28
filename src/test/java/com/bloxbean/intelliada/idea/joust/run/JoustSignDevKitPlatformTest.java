package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.JoustHostService;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * ADR-005 M3.3b2's exit (tool-gated, DevKit): a Counter deployed (its reference script published and settled, then
 * {@code init}), an {@code increment} call signed and submitted, and the host restarted between that submission and its
 * settlement: the fresh host is restored from the journal and the call lands.
 */
public class JoustSignDevKitPlatformTest extends BasePlatformTestCase {

    private static <T> T off(Callable<T> c) throws Exception {
        // The EDT is pumped while the pooled work runs (the host start reaches it).
        return com.intellij.testFramework.PlatformTestUtil.waitForFuture(
                com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(c), 600_000);
    }

    public void testACounterIsDeployedCalledAndSettledAcrossARestart() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "testACounterIsDeployedCalledAndSettledAcrossARestart");
        String store = JoustTestTools.env("JOUST_DEVKIT_STORE", "testACounterIsDeployedCalledAndSettledAcrossARestart");
        if (fixture == null || store == null) return;
        String bundle = System.getProperty("joust.verifier.dir", ""), java = System.getProperty("joust.lint.java", "");
        if (bundle.isEmpty() || java.isEmpty() || !Files.isDirectory(Path.of(bundle, "lib"))) {
            if (JoustTestTools.required()) throw new AssertionError("this test needs the bundled checker and a JDK 25");
            System.out.println("JOUST-SKIPPED testACounterIsDeployedCalledAndSettledAcrossARestart: no bundled checker or JDK 25");
            return;
        }
        JoustRunSession.checkerForTests = com.bloxbean.intelliada.idea.joust.verifier.JoustChecker.forTests(Path.of(bundle), Path.of(java));
        TrustedProjects.setProjectTrusted(getProject(), true);
        JoustHostService.confirmForTests = (project, dir, spec, version) -> true;
        RemoteNode node = new RemoteNode();
        node.setId("devkit-sign-test");
        node.setName("DevKit");
        node.setNodeType(NodeType.LocalYaciDevKit);
        node.setApiEndpoint(store);
        node.setProtocolMagic("42");
        var nodes = com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState.getInstance();
        Path module = Path.of(fixture);
        JournalStore journalStore = new JournalStore(Files.createTempDirectory("joust-sign-journal"));
        JoustHostService service = JoustHostService.getInstance(getProject());
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        String caller = "genesis-5";
        try {
            nodes.addRemoteNode(node);
            JoustRunSession.Launcher launcher = (init, key, reads) -> service.launch(guard, module, init, key, reads, e -> { });
            JoustRunSession s1 = off(() -> JoustRunSession.start(service, module, JoustRunSession.bind(node), journalStore, launcher, r -> { }));
            assertNull("signing is possible: " + s1.signingDisabled(), s1.signingDisabled());
            String contract = s1.running().initialize().path("contracts").get(0).path("name").textValue();

            // Deploy: prerequisites published and settled, then init.
            List<JoustRunSession.Submitted> deployed = off(() -> s1.deploy(contract, caller,
                    HostJson.object().put("method", "init").set("args", HostJson.object()), 300, Duration.ofMinutes(3)));
            assertFalse(deployed.isEmpty());
            JoustRunSession.Submitted init = deployed.get(deployed.size() - 1);
            assertNotNull("init creates an instance", init.instance());
            assertTrue("init lands", off(() -> s1.awaitInclusion(init.txHash(), Duration.ofMinutes(3))));

            // A call, submitted; the host stopped before IntelliAda saw it settle.
            String address = com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts.getInstance()
                    .forSession(s1.binding().identity()).stream().filter(e -> e.id.equals(caller)).findFirst().orElseThrow().address;
            ObjectNode call = HostJson.object().put("contract", contract).put("kind", "action").put("method", "increment")
                    .put("address", address).put("instance", init.instance());
            call.putObject("args").put("by", "1");
            JoustRunSession.Submitted inc = off(() -> s1.submit("prepare", HostJson.object().put("ttlSlots", 300).set("call", call),
                    caller, List.of()));
            assertNotNull(inc);
            service.stop(module);
            assertTrue(s1.running().process().waitFor(20, TimeUnit.SECONDS));

            // A fresh host: restored from the journal; the call settles from the chain.
            JoustRunSession s2 = off(() -> {
                for (int tries = 0; ; tries++) {
                    try {
                        return JoustRunSession.start(service, module, JoustRunSession.bind(node), journalStore, launcher, r -> { });
                    } catch (Exception e) {
                        if (tries >= 50 || e.getMessage() == null || !e.getMessage().contains("starting or stopping")) throw e;
                        Thread.sleep(200);
                    }
                }
            });
            assertTrue("the restored host holds the call", s2.running().requested().path("restoring").booleanValue());
            assertTrue("the call lands after the restart", off(() -> s2.awaitInclusion(inc.txHash(), Duration.ofMinutes(3))));
        } finally {
            JoustRunSession.checkerForTests = null;
            nodes.removeRemoteNode(node);
            service.stop(module);
        }
    }

    /**
     * #42 (FAB-17): a submission whose every POST failed to reach the node is journaled signed and sent again by the
     * settlement timer, so it lands; one left unsent when the host stops is sent again by the restored host.
     */
    public void testASubmissionThatNeverReachedTheNodeIsSentAgain() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "testASubmissionThatNeverReachedTheNodeIsSentAgain");
        String store = JoustTestTools.env("JOUST_DEVKIT_STORE", "testASubmissionThatNeverReachedTheNodeIsSentAgain");
        if (fixture == null || store == null) return;
        String bundle = System.getProperty("joust.verifier.dir", ""), java = System.getProperty("joust.lint.java", "");
        if (bundle.isEmpty() || java.isEmpty() || !Files.isDirectory(Path.of(bundle, "lib"))) {
            if (JoustTestTools.required()) throw new AssertionError("this test needs the bundled checker and a JDK 25");
            System.out.println("JOUST-SKIPPED testASubmissionThatNeverReachedTheNodeIsSentAgain: no bundled checker or JDK 25");
            return;
        }
        JoustRunSession.checkerForTests = com.bloxbean.intelliada.idea.joust.verifier.JoustChecker.forTests(Path.of(bundle), Path.of(java));
        TrustedProjects.setProjectTrusted(getProject(), true);
        JoustHostService.confirmForTests = (project, dir, spec, version) -> true;
        RemoteNode node = new RemoteNode();
        node.setId("devkit-resend-test");
        node.setName("DevKit");
        node.setNodeType(NodeType.LocalYaciDevKit);
        node.setApiEndpoint(store);
        node.setProtocolMagic("42");
        var nodes = com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState.getInstance();
        Path module = Path.of(fixture);
        JournalStore journalStore = new JournalStore(Files.createTempDirectory("joust-resend-journal"));
        JoustHostService service = JoustHostService.getInstance(getProject());
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        String caller = "genesis-6";
        try {
            nodes.addRemoteNode(node);
            JoustRunSession.Launcher launcher = (init, key, reads) -> service.launch(guard, module, init, key, reads, e -> { });
            JoustRunSession s1 = off(() -> JoustRunSession.start(service, module, JoustRunSession.bind(node), journalStore, launcher, r -> { }));
            String contract = s1.running().initialize().path("contracts").get(0).path("name").textValue();
            // The prerequisites, published if this DevKit does not hold them yet (and one instance).
            off(() -> s1.deploy(contract, caller, HostJson.object().put("method", "init").set("args", HostJson.object()), 300,
                    Duration.ofMinutes(3)));
            String address = com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts.getInstance()
                    .forSession(s1.binding().identity()).stream().filter(e -> e.id.equals(caller)).findFirst().orElseThrow().address;
            ObjectNode init = HostJson.object().put("ttlSlots", 300).set("call", HostJson.object().put("contract", contract)
                    .put("kind", "init").put("method", "init").put("address", address).set("args", HostJson.object()));

            // 1. Every POST fails to reach the node: an unknown outcome, journaled signed; the timer sends it again.
            String first = unsent(s1, init);
            assertTrue("sent again by the settlement timer, it lands", off(() -> s1.awaitInclusion(first, Duration.ofMinutes(3))));

            // 2. The same, and the host stopped at once: the restored host sends it again.
            String second = unsent(s1, init);
            service.stop(module);
            assertTrue(s1.running().process().waitFor(20, TimeUnit.SECONDS));
            JoustRunSession s2 = off(() -> {
                for (int tries = 0; ; tries++) {
                    try {
                        return JoustRunSession.start(service, module, JoustRunSession.bind(node), journalStore, launcher, r -> { });
                    } catch (Exception e) {
                        if (tries >= 50 || e.getMessage() == null || !e.getMessage().contains("starting or stopping")) throw e;
                        Thread.sleep(200);
                    }
                }
            });
            assertTrue("sent again by the restored host, it lands", off(() -> s2.awaitInclusion(second, Duration.ofMinutes(3))));
        } finally {
            JoustRunSession.unreachableForTests.set(0);
            JoustRunSession.checkerForTests = null;
            nodes.removeRemoteNode(node);
            service.stop(module);
        }
    }

    /** Submits {@code init} with every POST failing to reach the node; returns the journaled transaction's id. */
    private static String unsent(JoustRunSession s, ObjectNode init) throws Exception {
        var before = new java.util.HashSet<String>();
        for (var e : s.journalForTests().journal().entries()) before.add(e.txHash());
        JoustRunSession.unreachableForTests.set(JoustRunSession.SUBMIT_ATTEMPTS);
        try {
            off(() -> s.submit("prepare", init.deepCopy(), "genesis-6", List.of()));
            fail("a submission that never reached the node succeeded");
        } catch (Throwable expected) {
            String m = String.valueOf(expected.getMessage());
            assertTrue(m, m.contains("outcome is unknown") && m.contains("stay reserved"));
        }
        assertEquals(0, JoustRunSession.unreachableForTests.get());
        var fresh = s.journalForTests().journal().entries().stream().filter(e -> !before.contains(e.txHash())).toList();
        assertEquals(1, fresh.size());
        var e = fresh.get(0);
        assertEquals(JournalStore.Handout.submitted, e.handout());
        assertTrue("journaled signed", com.bloxbean.intelliada.idea.joust.tx.TxCbor.witnessEntries(
                com.bloxbean.intelliada.idea.joust.tx.TxCbor.transaction(e.cbor())).stream().anyMatch(w -> w.key() == 0));
        return e.txHash();
    }
}
