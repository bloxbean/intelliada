package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NodeType;
import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostError;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.JoustHostService;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.bloxbean.intelliada.idea.joust.tx.TxCbor;
import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * ADR-005 D17 on a real host and Yaci DevKit (M3.3b1 exit, §12 E10; tool-gated on {@code JOUST_HOST_FIXTURE} and
 * {@code JOUST_DEVKIT_STORE}): an unsigned publication is prepared through the journaled session and journaled as
 * handed out; the host is stopped; a fresh host starts restoring and is restored from the journal (new ids), holds the
 * body's reservation ({@code refusal/pending} for the same publication), and releases it on {@code discarded}. Nothing
 * is signed or submitted.
 */
public class JoustRunJournalDevKitPlatformTest extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            JoustHostService.confirmForTests = null;
        } finally {
            super.tearDown();
        }
    }

    private static <T> T off(Callable<T> c) throws Exception {
        return PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(c), 180_000);
    }

    public void testAJournaledBodyIsRestoredIntoAFreshHostWhichHoldsItsReservation() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "testAJournaledBodyIsRestoredIntoAFreshHostWhichHoldsItsReservation");
        String store = JoustTestTools.env("JOUST_DEVKIT_STORE", "testAJournaledBodyIsRestoredIntoAFreshHostWhichHoldsItsReservation");
        if (fixture == null || store == null) return;
        TrustedProjects.setProjectTrusted(getProject(), true);
        JoustHostService.confirmForTests = (project, dir, spec, version) -> true;
        RemoteNode node = new RemoteNode();
        node.setId("devkit-journal-test");
        node.setName("DevKit");
        node.setNodeType(NodeType.LocalYaciDevKit);
        node.setApiEndpoint(store);
        node.setProtocolMagic("42");
        var nodes = com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState.getInstance();
        Path module = Path.of(fixture);
        JournalStore journalStore = new JournalStore(Files.createTempDirectory("joust-journal"));
        JoustHostService service = JoustHostService.getInstance(getProject());
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        // A DevKit genesis account pays the publication (never signed: nothing is submitted).
        String payer = new Account(Networks.testnet(), "test test test test test test test test test test test test test test test test test"
                + " test test test test test test sauce", DerivationPath.createExternalAddressDerivationPathForAccount(7)).baseAddress();
        try {
            nodes.addRemoteNode(node);
            JoustRunSession.Launcher launcher = (init, key, reads) -> service.launch(guard, module, init, key, reads, e -> { });
            JoustRunSession s1 = off(() -> JoustRunSession.start(service, module, JoustRunSession.bind(node), journalStore, launcher, r -> { }));
            assertNotNull("a journaled session", s1.journalForTests());
            String contract = s1.running().initialize().path("contracts").get(0).path("name").textValue();
            JsonNode pre = off(() -> s1.prerequisites(contract));
            assertTrue("the Counter's reference script is outstanding on this DevKit: " + pre, pre.isArray() && pre.size() > 0);
            String id = pre.get(0).path("id").textValue();
            var publish = HostJson.object().put("ttlSlots", 120).put("contract", contract).put("prerequisite", id).put("address", payer);
            // An unsigned body never handed out is discarded through the model; the publication can then be prepared again
            // (D17, M33B1-DES-2).
            JsonNode declined = off(() -> s1.build("publish", publish.deepCopy())).path("prepared");
            off(() -> {
                s1.discard(declined.path("reservation").textValue());
                return null;
            });
            JsonNode prepared = off(() -> s1.build("publish", publish.deepCopy())).path("prepared");
            String cbor = prepared.path("cbor").textValue().toLowerCase(java.util.Locale.ROOT);
            TxCbor.Body body = TxCbor.body(TxCbor.transaction(cbor));
            assertEquals("IntelliAda's reader agrees with the host's hash", prepared.path("txHash").textValue(), body.txHash());
            assertEquals(prepared.path("validTo").longValue(), (long) body.validTo());
            assertTrue("the body publishes its prerequisite", body.publishes().contains(id.startsWith("verifier:")
                    ? "referenceScript:" + id.substring(9) : id));
            // Journaled as handed out (an export in M3.5's terms), by the test: nothing in production admits in M3.3b1.
            var entry = new JournalStore.Entry(prepared.path("reservation").textValue(), body.txHash(), cbor, body.validFrom(), body.validTo(),
                    JournalStore.Handout.exported, node.getId(), JournalStore.State.prepared, null, null, List.of(), "publish " + id);
            long now = off(s1::now);
            off(() -> {
                s1.journalForTests().admit(entry, now, now);
                return null;
            });
            service.stop(module);
            assertTrue(s1.running().process().waitFor(20, TimeUnit.SECONDS));

            // A fresh host: restoring, restored from the journal at the chain as it is now.
            // The service clears the stopped host's slot after the process exits (M33B1-DES-23): retried briefly.
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
            assertTrue("initialized restoring", s2.running().requested().path("restoring").booleanValue());
            List<JournalStore.Entry> restored = off(() -> s2.journalForTests().journal().entries());
            assertEquals(1, restored.size());
            assertEquals(body.txHash(), restored.get(0).txHash());
            assertEquals(JournalStore.State.prepared, restored.get(0).state());
            assertNull("restore completed: building is not blocked by the journal", s2.journalBlocks());
            // The fresh host holds the reservation: the same publication is pending.
            try {
                off(() -> s2.build("publish", publish.deepCopy()));
                fail("the restored reservation was not held");
            } catch (Throwable expected) {
                HostError he = cause(expected);
                assertNotNull(String.valueOf(expected), he);
                assertEquals("refusal/pending", he.kind() + "/" + he.code());
            }
            // A handed-out body is never discarded (I30): only chain evidence or expiry releases it.
            try {
                off(() -> {
                    s2.discard(restored.get(0).reservation());
                    return null;
                });
                fail("a handed-out body was discarded");
            } catch (Throwable expected) {
                assertTrue(String.valueOf(expected), String.valueOf(expected).contains("I30"));
            }
            // I33: the same module through another configuration (an alias) of this chain is refused while it runs.
            RemoteNode alias = new RemoteNode();
            alias.setId("devkit-journal-alias");
            alias.setName("DevKit alias");
            alias.setNodeType(NodeType.LocalYaciDevKit);
            alias.setApiEndpoint(store);
            alias.setProtocolMagic("42");
            nodes.addRemoteNode(alias);
            try {
                off(() -> JoustRunSession.start(service, module, JoustRunSession.bind(alias), journalStore, launcher, r -> { }));
                fail("a second host of the module on one chain");
            } catch (Throwable expected) {
                assertTrue(String.valueOf(expected), s2.running().process().isAlive());
            } finally {
                nodes.removeRemoteNode(alias);
            }
            service.stop(module);
            assertTrue(s2.running().process().waitFor(20, TimeUnit.SECONDS));
        } finally {
            nodes.removeRemoteNode(node);
            service.stop(module);
        }
    }

    /** M33B1-FAB-1: a journal store IntelliAda cannot use (here a file where its folder must go) starts the host read-only. */
    public void testAnUnusableStoreStartsTheHostReadOnly() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "testAnUnusableStoreStartsTheHostReadOnly");
        String store = JoustTestTools.env("JOUST_DEVKIT_STORE", "testAnUnusableStoreStartsTheHostReadOnly");
        if (fixture == null || store == null) return;
        TrustedProjects.setProjectTrusted(getProject(), true);
        JoustHostService.confirmForTests = (project, dir, spec, version) -> true;
        RemoteNode node = new RemoteNode();
        node.setId("devkit-journal-unusable");
        node.setName("DevKit");
        node.setNodeType(NodeType.LocalYaciDevKit);
        node.setApiEndpoint(store);
        node.setProtocolMagic("42");
        var nodes = com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState.getInstance();
        Path module = Path.of(fixture);
        Path blocker = Files.createTempFile("joust-journal-blocker", "");
        JournalStore unusable = new JournalStore(blocker.resolve("joust-journal"));
        JoustHostService service = JoustHostService.getInstance(getProject());
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        try {
            nodes.addRemoteNode(node);
            JoustRunSession.Launcher launcher = (init, key, reads) -> service.launch(guard, module, init, key, reads, e -> { });
            JoustRunSession s = off(() -> JoustRunSession.start(service, module, JoustRunSession.bind(node), unusable, launcher, r -> { }));
            assertNull("no journal", s.journalForTests());
            assertTrue(s.running().process().isAlive());
            String why = s.journalBlocks();
            assertNotNull(why);
            assertTrue(why, why.contains("cannot be used"));
            String contract = s.running().initialize().path("contracts").get(0).path("name").textValue();
            assertNotNull("views work without the journal", off(() -> s.prerequisites(contract)));
        } finally {
            nodes.removeRemoteNode(node);
            service.stop(module);
            Files.deleteIfExists(blocker);
        }
    }

    private static HostError cause(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) if (c instanceof HostError he) return he;
        return null;
    }
}
