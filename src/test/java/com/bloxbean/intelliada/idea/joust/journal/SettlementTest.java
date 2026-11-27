package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.host.HostError;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Entry;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Inclusion;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.State;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.bloxbean.intelliada.idea.joust.journal.Bodies.*;
import static org.junit.Assert.*;

/**
 * ADR-005 D17, I30, I31, I32 (M3.3b1): the settlement driver against a fake chain and a host whose answers are scripted
 * from protocol 1's table (margin 60). The rows the real host must confirm are also run against it
 * ({@code SettlementRealHostTest}).
 */
public class SettlementTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    static final String CHAIN = "devnet-42-" + "ab".repeat(32);
    static final String MOD = JournalStore.key("/work/a");

    /** A chain whose tip and inclusions the test sets. */
    static final class FakeChain implements Settlement.ChainView {
        Tip tip = new Tip(0, 0, "00".repeat(32));
        final Map<String, Inclusion> in = new HashMap<>();
        int tipReads;

        void at(long slot) {
            tip = new Tip(slot, slot, String.format("%064x", slot));
        }

        void include(String txHash, long slot) {
            in.put(txHash, new Inclusion(slot, slot, String.format("%064x", slot + 1_000_000)));
        }

        @Override
        public Tip tip() {
            tipReads++;
            return tip;
        }

        @Override
        public Inclusion inclusion(String txHash) {
            return in.get(txHash);
        }
    }

    /** A host answering from a script, recording every call. */
    static final class ScriptHost implements Settlement.Host {
        final List<String> calls = new ArrayList<>();
        final Deque<Object> answers = new ArrayDeque<>();

        ScriptHost answer(Object... a) {
            for (Object x : a) answers.add(x);
            return this;
        }

        @Override
        public JsonNode call(String method, ObjectNode params) throws Exception {
            String line = method.equals("settled")
                    ? method + " " + params.path("status").asText("") + params.path("slot").asText("") + "/" + params.path("tip").asText("")
                    : method + (params.has("complete") ? " complete=" + params.get("complete") + " n=" + params.path("transactions").size() : "");
            calls.add(line.trim());
            Object a = answers.poll();
            if (a == null) throw new AssertionError("no answer scripted for " + line);
            if (a instanceof HostError e) throw e;
            if (a instanceof String s && method.equals("settled")) return HostJson.object().put("state", s);
            if (a instanceof List<?> ids) {
                ObjectNode r = HostJson.object();
                var arr = r.putArray("reservations");
                for (Object id : ids) arr.add((String) id);
                return r;
            }
            throw new AssertionError("a bad script entry for " + line);
        }
    }

    final FakeChain chain = new FakeChain();
    final ScriptHost host = new ScriptHost();
    final AtomicLong clock = new AtomicLong();
    final AtomicReference<String> fence = new AtomicReference<>();
    final AtomicLong lastNow = new AtomicLong();
    JournalStore store;
    ChainJournal journal;

    private Settlement settlement(HostTable t) {
        store = store == null ? new JournalStore(tmp.getRoot().toPath().resolve("j")) : store;
        journal = new ChainJournal(store, CHAIN, MOD, 60);
        return new Settlement(journal, t, chain, host, lastNow::get, clock::get, fence::set);
    }

    private static String ref(int n) {
        return txid(n) + "#0";
    }

    private static HostError err(String kindCode) {
        String[] p = kindCode.split("/");
        return new HostError(p[0], p[1], "scripted", List.of(), null);
    }

    /** A fresh host restored from journaled, submitted bodies at the chain's tip. */
    private Settlement restored(List<Entry> entries, List<String> ids) throws Exception {
        JournalStore s = new JournalStore(tmp.getRoot().toPath().resolve("j"));
        ChainJournal seed = new ChainJournal(s, CHAIN, MOD, 60);
        for (Entry e : entries) seed.admit(e, 0, 0);
        store = s;
        ChainJournal.Opening o = seed.open(chain.tip.slot());
        HostTable t = new HostTable(60, o.restoring());
        Settlement st = settlement(t);
        host.answer(ids);
        st.restore(o);
        return st;
    }

    @Test
    public void inclusionsAreReportedFirstEachAtItsOwnSlot() throws Exception {
        // Bank-publish-1 (validTo 1886) and Bank-init (1890), restored together, land at 1871 and 1875; the poll tip is
        // 1951. Reporting either inclusion at 1951 would expire the other body first (a conflict on the real host).
        String a = tx(List.of(ref(1)), 1886), b = tx(List.of(ref(2)), 1890);
        chain.at(1866);
        Settlement s = restored(List.of(submitted("1", a), submitted("2", b)), List.of("7", "8"));
        assertEquals(List.of("restore complete=true n=2"), host.calls);
        chain.at(1951);
        chain.include(hash(a), 1871);
        chain.include(hash(b), 1875);
        host.answer("shallow", "shallow", "confirmed");
        s.poll();
        assertNull(fence.get());
        // The first body's final confirmation at 1951 also passes the second's margin (1951 - 1875 > 60): the host
        // finalizes it with that tip, as its table applies every tip to every reservation.
        assertEquals(List.of("restore complete=true n=2", "settled confirmed1871/1871", "settled confirmed1875/1875",
                "settled confirmed1871/1951"), host.calls);
        assertEquals(List.of(State.confirmed, State.confirmed), journal.entries().stream().map(Entry::state).toList());
    }

    @Test
    public void aHostAnsweringConfirmedOneSlotAfterInclusionIsFencedAndTheReservationKept() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        chain.at(2006);
        chain.include(hash(a), 2005);
        host.answer("confirmed");
        s.poll();
        assertNotNull(fence.get());
        assertTrue(fence.get(), fence.get().contains("answered confirmed"));
        Entry e = journal.entries().get(0);
        assertEquals("the reservation is kept as the chain shows it", State.prepared, e.state());
        assertEquals(1, journal.entries().size());
    }

    @Test
    public void aReadWhoseTipWouldExpireALandedBodyAnswersFailedAndTheInclusionIsReportedBeforeTheNextCall() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        chain.include(hash(a), 2005);
        s.beginCall();
        ObjectNode answer = HostJson.object();
        answer.putObject("tip").put("slot", 2100).put("height", 2100).put("hash", String.format("%064x", 2100));
        answer.putArray("value");
        ObjectNode got = s.guard((id, p) -> answer.deepCopy()).read("2.1", HostJson.object().put("kind", "utxos").put("address", "x"));
        assertTrue(got.toString(), got.has("failed"));
        assertEquals(2100, got.path("tip").path("slot").intValue());
        s.endCall("prepare", null, "internal/readFailed");
        assertTrue(s.pollNeeded());
        chain.at(2100);
        host.answer("shallow", "confirmed");
        s.poll();
        assertEquals(List.of("restore complete=true n=1", "settled confirmed2005/2005", "settled confirmed2005/2100"), host.calls);
        assertNull(fence.get());
    }

    @Test
    public void whichReadTipsAdvanceTheTable() throws Exception {
        HostTable t = new HostTable(60, false);
        Settlement s = settlement(t);
        t.committed("1", "aa".repeat(32), 0L, 2025);
        assertTrue(t.reply(t.expect("1", "aa".repeat(32), "confirmed", 2005L, 2005), HostTable.St.shallow, null));
        s.beginCall();
        read(s, "datum", "value", 2100, false);
        s.endCall("prepare", null, null);
        assertEquals("a successful datum answer does not advance it", HostTable.St.shallow, t.lower("1").st());
        s.beginCall();
        read(s, "stakeRegistration", "unsupported", 2100, false);
        s.endCall("prepare", null, null);
        assertEquals("an unsupported stakeRegistration answer advances it", HostTable.St.confirmed, t.lower("1").st());
    }

    @Test
    public void uncertainCallsAdvanceOnlyTheUpperCopy() throws Exception {
        for (String ending : List.of("bad/readLimit", "internal/chainMoved", "transport")) {
            HostTable t = new HostTable(60, false);
            Settlement s = settlement(t);
            t.committed("1", "aa".repeat(32), 0L, 2025);
            assertTrue(t.reply(t.expect("1", "aa".repeat(32), "confirmed", 2005L, 2005), HostTable.St.shallow, null));
            s.beginCall();
            read(s, "utxos", "value", 2100, ending.equals("bad/readLimit"));   // a first chunk with a continuation
            s.endCall("prepare", null, ending);
            assertEquals(ending, HostTable.St.shallow, t.lower("1").st());
            assertEquals(ending, HostTable.St.confirmed, t.upper("1").st());
        }
        // A listing whose final chunk came, in a clean call, counts once, certainly.
        HostTable t = new HostTable(60, false);
        Settlement s = settlement(t);
        t.committed("1", "aa".repeat(32), 0L, 2025);
        assertTrue(t.reply(t.expect("1", "aa".repeat(32), "confirmed", 2005L, 2005), HostTable.St.shallow, null));
        s.beginCall();
        read(s, "utxos", "value", 2100, true);
        read(s, "utxos", "value", 2100, false);
        s.endCall("view", null, null);
        assertEquals(HostTable.St.confirmed, t.lower("1").st());
    }

    private static void read(Settlement s, String kind, String outcome, long tip, boolean more) throws Exception {
        ObjectNode a = HostJson.object();
        a.putObject("tip").put("slot", tip).put("height", tip).put("hash", String.format("%064x", tip));
        if (outcome.equals("value")) a.putArray("value");
        else a.put(outcome, true);
        if (more) a.put("more", "c1");
        s.guard((id, p) -> a).read("1.1", HostJson.object().put("kind", kind));
    }

    @Test
    public void aRefusedOrTimedOutSubmissionStaysReservedUntilExpiry() throws Exception {
        // I30: nothing but chain evidence or expiry releases a handed-out body.
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        chain.at(2085);
        s.poll();
        assertEquals("nothing settles inside the margin", List.of("restore complete=true n=1"), host.calls);
        chain.at(2086);
        host.answer("expired");
        s.poll();
        assertEquals("settled expired/2086", host.calls.get(1));
        assertEquals(State.expired, journal.entries().get(0).state());
    }

    @Test
    public void aShallowRollbackReturnsTheBodyToPreparedAndARestartRestoresItPrepared() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        chain.include(hash(a), 2005);
        chain.at(2006);
        host.answer("shallow");
        s.poll();
        chain.in.clear();
        chain.at(2010);
        host.answer("prepared");
        s.poll();
        assertEquals("settled rolledBack/2010", host.calls.get(2));
        Entry e = journal.entries().get(0);
        assertEquals(State.prepared, e.state());
        assertNull(e.inclusion());
        // A restart: the body is restored prepared (its inputs held), its validTo being above the tip.
        ChainJournal.Opening o = new ChainJournal(new JournalStore(store.root()), CHAIN, MOD, 60).open(2010);
        ScriptHost fresh = new ScriptHost().answer(List.of("1"));
        Settlement s2 = new Settlement(new ChainJournal(store, CHAIN, MOD, 60), new HostTable(60, o.restoring()), chain, fresh,
                lastNow::get, clock::get, fence::set);
        s2.restore(o);
        assertEquals(List.of("restore complete=true n=1"), fresh.calls);
    }

    @Test
    public void aLaggingTipIsTransientAndAPersistentLowerTipThatTouchesNothingIsTheNewBaseline() throws Exception {
        String a = tx(List.of(ref(1)), 3000);
        chain.at(2000);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        chain.at(2100);
        s.poll();
        chain.at(2050);   // a lagging provider
        s.poll();
        s.poll();
        assertNull("transient: no rewind", s.rewind());
        clock.addAndGet(11_000_000_000L);
        s.poll();
        assertNull("persistent, touching nothing: the journal and the mark are kept", s.rewind());
        assertNull(fence.get());
        assertEquals(1, journal.entries().size());
    }

    @Test
    public void aPersistentLowerTipBelowAnExpiredBodysValidToIsARewind() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        chain.at(2086);
        host.answer("expired");
        s.poll();
        chain.at(2000);
        s.poll();
        s.poll();
        clock.addAndGet(11_000_000_000L);
        s.poll();
        assertNotNull(s.rewind());
        assertNotNull("persisted before any restart", journal.record().rewind());
        assertNotNull(fence.get());
    }

    @Test
    public void aTipFarBelowTheLastNowIsARewind() throws Exception {
        String a = tx(List.of(ref(1)), 9000);
        chain.at(5000);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        lastNow.set(5000);
        chain.at(4000);
        s.poll();
        s.poll();
        clock.addAndGet(11_000_000_000L);
        s.poll();
        assertNotNull(s.rewind());
    }

    @Test
    public void aReorgBelowTheDepthMarginLeavesDeeperInclusionsUntouched() throws Exception {
        String a = tx(List.of(ref(1)), 1100);
        chain.at(990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        chain.include(hash(a), 1000);
        chain.at(1001);
        host.answer("shallow");
        s.poll();
        chain.at(1100);
        host.answer("confirmed");
        s.poll();
        assertEquals("final and still retained", State.confirmed, journal.entries().get(0).state());
        chain.at(1070);   // a reorg of 30 slots, above the final inclusion's margin (1000 + 60)
        s.poll();
        s.poll();
        clock.addAndGet(11_000_000_000L);
        s.poll();
        assertNull(s.rewind());
    }

    @Test
    public void aForkAtARecordedInclusionIsARewind() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        chain.include(hash(a), 2005);
        chain.at(2006);
        host.answer("shallow");
        s.poll();
        chain.include(hash(a), 2007);   // the same body, in another block
        chain.at(2010);
        s.poll();
        assertNotNull(s.rewind());
        assertEquals("nothing more was told to the fenced host", 2, host.calls.size());
    }

    @Test
    public void aRestoreRebuildsEachEntryFromTheChainAsItIsNow() throws Exception {
        // Shallow inclusions (one final before the rewind, one moved) come back shallow at their slots; a removed one
        // comes back prepared while its validTo is above the tip; one awaiting approval is invalidated.
        String kept = tx(List.of(ref(1)), 2025), moved = tx(List.of(ref(2)), 2025), removed = tx(List.of(ref(3)), 2025),
                pending = tx(List.of(ref(4)), 2025);
        JournalStore s = new JournalStore(tmp.getRoot().toPath().resolve("j"));
        ChainJournal seed = new ChainJournal(s, CHAIN, MOD, 60);
        seed.admit(submitted("1", kept), 0, 0);
        seed.admit(submitted("2", moved), 0, 0);
        seed.admit(submitted("3", removed), 0, 0);
        seed.admit(entry("4", pending, JournalStore.Handout.approvalPending), 0, 0);
        seed.update("1", e -> e.withState(State.confirmed, 1950L).withInclusion(new Inclusion(1950, 1950, "11".repeat(32))));
        store = s;
        chain.at(1990);
        chain.include(hash(kept), 1950);
        chain.include(hash(moved), 1960);
        ChainJournal.Opening o = seed.open(1990);
        Settlement st = settlement(new HostTable(60, o.restoring()));
        host.answer(List.of("1", "2", "3"));
        st.restore(o);
        List<Entry> after = journal.entries();
        assertEquals(3, after.size());
        assertEquals(State.shallow, after.get(0).state());
        assertEquals(1950L, (long) after.get(0).slot());
        assertEquals(1960L, (long) after.get(1).slot());
        assertEquals(State.prepared, after.get(2).state());
    }

    @Test
    public void aLostHistoryRestoreCompletesOnlyAboveTheBound() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        JournalStore s = new JournalStore(tmp.getRoot().toPath().resolve("j"));
        new ChainJournal(s, CHAIN, MOD, 60).admit(submitted("1", a), 1990, 1990);
        java.nio.file.Files.delete(s.recordFile(JournalStore.key(CHAIN)));
        store = s;
        chain.at(1995);
        ChainJournal.Opening o = new ChainJournal(s, CHAIN, MOD, 60).open(1995);
        long bound = o.completeAbove();
        Settlement st = settlement(new HostTable(60, true));
        host.answer(List.of("1"));
        st.restore(o);
        assertEquals("restore stays open", List.of("restore complete=false n=1"), host.calls);
        assertTrue(st.restoring());
        chain.at(bound);
        st.poll();
        assertTrue("not at the bound itself", st.restoring());
        chain.at(bound + 1);
        host.answer(List.of());
        st.poll();
        assertFalse(st.restoring());
        assertEquals("restore complete=true n=0", host.calls.get(host.calls.size() - 1));
    }
}
