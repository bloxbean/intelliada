package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.host.HostError;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostLauncher;
import com.bloxbean.intelliada.idea.joust.host.HostSession;
import com.bloxbean.intelliada.idea.joust.host.HostSnapshot;
import com.bloxbean.intelliada.idea.joust.host.HostSpec;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Entry;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.State;
import com.bloxbean.intelliada.idea.joust.tx.TxCborTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * ADR-005 I32 (M3.3b1, tool-gated): IntelliAda's expected settlement states against JOUST's real JVM host. The kit's
 * bodies ({@code Bank-publish-1}, {@code Bank-init}, {@code Registry-remove}) are restored into a real host (the
 * {@code JOUST_HOST_FIXTURE} Counter's; {@code restore} reads only the bodies), the scenarios of §4.0's I32 row are
 * driven through {@link Settlement} with scripted reverse reads, and every reply must be the one IntelliAda expects.
 */
public class SettlementRealHostTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    static final String CHAIN = "devnet-42-" + "ab".repeat(32);
    static final String MOD = JournalStore.key("/work/a");

    /** A real host restored by a {@link Settlement}; its reverse reads come from {@code script} through the guard. */
    private final class Rig implements AutoCloseable {
        final Process p;
        final HostSession s;
        final HostSnapshot snap;
        final JsonNode init;
        final Deque<ObjectNode> script = new ArrayDeque<>();
        final SettlementTest.FakeChain chain = new SettlementTest.FakeChain();
        final AtomicReference<String> fence = new AtomicReference<>();
        final ChainJournal journal;
        HostTable table;
        Settlement settlement;
        private volatile HostSession.Reads reads;

        Rig(Path project, int readsPerCall) throws Exception {
            HostSpec spec = HostSpec.read(project);
            snap = HostSnapshot.create(spec, tmp.newFolder().toPath());
            p = HostLauncher.start(spec, snap, project, 256 * 1024).process();
            s = new HostSession(p.getInputStream(), p.getOutputStream(), () -> HostLauncher.stop(p), (id, q) -> reads.read(id, q), e -> { });
            ObjectNode i = HostJson.object();
            i.putArray("protocol").add(1);
            i.putObject("network").put("networkId", 0).put("protocolMagic", 42).put("mainnet", false);
            i.putObject("clock").put("zeroSlot", 0).put("zeroTimeMs", 1700000000000L).put("slotLengthMs", 1000);
            i.putObject("time").put("maxTtlSlots", 900).put("settlementSlots", 60).put("maxNowAheadSlots", 900);
            i.putObject("limits").put("callDeadlineMs", 60000).put("readsPerCall", readsPerCall).put("readBytesPerCall", 33554432);
            i.putObject("build").put("snapshot", snap.root().toString()).put("digest", snap.digest());
            i.put("restoring", true);
            init = s.initialize(i, snap.digest(), Duration.ofSeconds(120));
            journal = new ChainJournal(new JournalStore(tmp.newFolder().toPath()), CHAIN, MOD, 60);
        }

        /** Journals the bodies as handed out, then restores this host from them at {@code tip}. */
        void restore(long tip, String... cbors) throws Exception {
            int n = 1;
            for (String c : cbors) journal.admit(Bodies.submitted("old" + n++, c), 0, 0);
            chain.at(tip);
            ChainJournal.Opening o = journal.open(tip);
            table = new HostTable(60, o.restoring());
            settlement = new Settlement(journal, table, chain, (m, q) -> s.call(m, q, Duration.ofSeconds(60), () -> false),
                    () -> 0, new AtomicLong()::get, ms -> { }, fence::set);
            HostSession.Reads raw = (id, q) -> {
                ObjectNode a = script.poll();
                if (a == null) throw new IllegalStateException("no read scripted for " + q);
                return a;
            };
            reads = settlement.guard(raw);
            settlement.restore(o);
        }

        String contract() {
            return init.path("contracts").get(0).path("name").textValue();
        }

        /** A {@code deployment} call at {@code now} through the settlement's bookkeeping: the error kind/code, or null. */
        String deployment(long now) throws Exception {
            settlement.beginCall();
            try {
                JsonNode res = s.call("deployment", HostJson.object().put("now", now).put("contract", contract()), Duration.ofSeconds(60), () -> false);
                settlement.endCall("deployment", res, null);
                return null;
            } catch (HostError e) {
                settlement.endCall("deployment", null, e.kind() + "/" + e.code());
                return e.kind() + "/" + e.code();
            }
        }

        @Override
        public void close() {
            s.close();
            HostLauncher.stop(p);
            snap.delete();
        }
    }

    private static ObjectNode answer(long slot, String outcome, boolean more) {
        ObjectNode a = HostJson.object();
        a.putObject("tip").put("slot", slot).put("height", slot).put("hash", String.format("%064x", slot));
        if (outcome.equals("value")) a.putArray("value");
        else if (outcome.equals("failed")) a.putObject("failed").put("message", "scripted");
        else a.put(outcome, true);
        if (more) a.put("more", "c1");
        return a;
    }

    private static Map<String, String> vectors() throws Exception {
        List<JsonNode> vs = TxCborTest.vectors("SettlementRealHostTest");
        if (vs == null) return null;
        Map<String, String> out = new HashMap<>();
        for (JsonNode v : vs) {
            for (String want : List.of("Bank-publish-1", "Bank-init", "Registry-remove")) {
                if (v.path("$name").textValue().equals("verifier/" + want + ".json")) {
                    out.put(want, v.path("result").path("prepared").path("cbor").textValue());
                }
            }
        }
        return out;
    }

    private static Path fixture(String test) {
        String f = JoustTestTools.env("JOUST_HOST_FIXTURE", test);
        return f == null ? null : Path.of(f);
    }

    @Test
    public void twoBodiesRestoredTogetherAreBothReportedBeforeThePollTip() throws Exception {
        Path project = fixture("twoBodiesRestoredTogetherAreBothReportedBeforeThePollTip");
        Map<String, String> v = vectors();
        if (project == null || v == null) return;
        try (Rig r = new Rig(project, 256)) {
            r.restore(1866, v.get("Bank-publish-1"), v.get("Bank-init"));
            r.chain.at(1951);
            r.chain.include(Bodies.hash(v.get("Bank-publish-1")), 1871);
            r.chain.include(Bodies.hash(v.get("Bank-init")), 1875);
            r.settlement.poll();
            assertNull("the real host answered as expected: " + r.fence.get(), r.fence.get());
            assertEquals(List.of(State.confirmed, State.confirmed), r.journal.entries().stream().map(Entry::state).toList());
        }
    }

    /** The control: reporting an inclusion at the poll tip first makes the real host expire the other body. */
    @Test
    public void reportingAtThePollTipFirstConflictsOnTheRealHost() throws Exception {
        Path project = fixture("reportingAtThePollTipFirstConflictsOnTheRealHost");
        Map<String, String> v = vectors();
        if (project == null || v == null) return;
        try (Rig r = new Rig(project, 256)) {
            r.restore(1866, v.get("Bank-publish-1"), v.get("Bank-init"));
            List<Entry> es = r.journal.entries();
            JsonNode first = r.s.call("settled", HostJson.object().put("reservation", es.get(0).reservation()).put("txHash", es.get(0).txHash())
                    .put("status", "confirmed").put("slot", 1871).put("tip", 1951), Duration.ofSeconds(60), () -> false);
            assertEquals("confirmed", first.path("state").textValue());
            try {
                r.s.call("settled", HostJson.object().put("reservation", es.get(1).reservation()).put("txHash", es.get(1).txHash())
                        .put("status", "confirmed").put("slot", 1875).put("tip", 1951), Duration.ofSeconds(60), () -> false);
                fail("the second body was expired by the first event's tip");
            } catch (HostError e) {
                assertEquals("bad/conflict", e.kind() + "/" + e.code());
            }
            // IntelliAda's model agrees on the same sequence.
            HostTable t = new HostTable(60, false);
            t.committed("a", es.get(0).txHash(), null, 1886);
            t.committed("b", es.get(1).txHash(), 1870L, 1890);
            assertTrue(t.reply(t.expect("a", es.get(0).txHash(), "confirmed", 1871L, 1951), HostTable.St.confirmed, null));
            assertEquals("bad/conflict", t.expect("b", es.get(1).txHash(), "confirmed", 1875L, 1951).lower().error());
        }
    }

    @Test
    public void aReadAt2070ThenConfirmedAt2005IsExpectedAsTheRealHostAnswers() throws Exception {
        Path project = fixture("aReadAt2070ThenConfirmedAt2005IsExpectedAsTheRealHostAnswers");
        Map<String, String> v = vectors();
        if (project == null || v == null) return;
        try (Rig r = new Rig(project, 256)) {
            r.restore(1990, v.get("Registry-remove"));
            for (int i = 0; i < 16; i++) r.script.add(answer(2070, "value", false));
            assertNull("the deployment call at 2070", r.deployment(2070));
            assertEquals("valid through 2025, within its margin at 2070", HostTable.St.prepared, r.table.lower(r.journal.entries().get(0).reservation()).st());
            r.chain.at(2070);
            r.chain.include(Bodies.hash(v.get("Registry-remove")), 2005);
            r.settlement.poll();   // confirmed at slot and tip 2005, then final at 2070
            assertNull("the real host answered as expected: " + r.fence.get(), r.fence.get());
            assertEquals(State.confirmed, r.journal.entries().get(0).state());
        }
    }

    @Test
    public void uncertainTipsNeverFenceTheRealHost() throws Exception {
        Path project = fixture("uncertainTipsNeverFenceTheRealHost");
        Map<String, String> v = vectors();
        if (project == null || v == null) return;
        // (1) a failed read at 2100 (never applied); (2) a first utxos chunk at 2100 with a continuation, the call ending
        // bad/readLimit (one read per call; applied to the upper copy only). Either way the body is shallow for a
        // repeat confirmation at 2006, as the real host answers.
        for (String mode : List.of("failed", "chunk")) {
            try (Rig r = new Rig(project, mode.equals("chunk") ? 1 : 256)) {
                r.restore(1990, v.get("Registry-remove"));
                r.chain.at(2005);
                r.chain.include(Bodies.hash(v.get("Registry-remove")), 2005);
                r.settlement.poll();
                Entry e = r.journal.entries().get(0);
                assertEquals(State.shallow, e.state());
                r.script.add(mode.equals("failed") ? answer(2100, "failed", false) : answer(2100, "value", true));
                String ended = r.deployment(2006);
                assertNotNull(mode + ": the call ended in an error", ended);
                if (mode.equals("chunk")) assertEquals("bad/readLimit", ended);
                HostTable.Expected x = r.table.expect(e.reservation(), e.txHash(), "confirmed", 2005L, 2006);
                JsonNode got = r.s.call("settled", HostJson.object().put("reservation", e.reservation()).put("txHash", e.txHash())
                        .put("status", "confirmed").put("slot", 2005).put("tip", 2006), Duration.ofSeconds(60), () -> false);
                assertEquals(mode, "shallow", got.path("state").textValue());
                assertTrue(mode + ": " + x, r.table.reply(x, HostTable.St.shallow, null));
                assertNull(r.fence.get());
            }
        }
    }
}
