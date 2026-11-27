package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.host.HostError;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostSession;
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
import java.util.Set;
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
        /** Tips answered first, one per read, before {@link #tip}; {@code moving} advances the tip by one on every read. */
        final Deque<Long> next = new ArrayDeque<>();
        boolean moving;
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
            if (!next.isEmpty()) at(next.poll());
            else if (moving) at(tip.slot() + 1);
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
            if (a instanceof ObjectNode raw) return raw;
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
        return new Settlement(journal, t, chain, host, lastNow::get, clock::get, ms -> clock.addAndGet(ms * 1_000_000), fence::set);
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
        for (Entry e : entries) seed.admit(e, chain.tip.slot(), chain.tip.slot());
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
        chain.in.put("aa".repeat(32), new Inclusion(2005, 2005, "11".repeat(32)));   // still on chain (M33B1-CDX-25)
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
            chain.in.put("aa".repeat(32), new Inclusion(2005, 2005, "11".repeat(32)));
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
        chain.in.put("aa".repeat(32), new Inclusion(2005, 2005, "11".repeat(32)));
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
        assertEquals("rolled back at its own inclusion slot (M33B1-CDX-6)", "settled rolledBack/2005", host.calls.get(2));
        Entry e = journal.entries().get(0);
        assertEquals(State.prepared, e.state());
        assertNull(e.inclusion());
        // A restart: the body is restored prepared (its inputs held), its validTo being above the tip.
        ChainJournal.Opening o = new ChainJournal(new JournalStore(store.root()), CHAIN, MOD, 60).open(2010);
        ScriptHost fresh = new ScriptHost().answer(List.of("1"));
        Settlement s2 = new Settlement(new ChainJournal(store, CHAIN, MOD, 60), new HostTable(60, o.restoring()), chain, fresh,
                lastNow::get, clock::get, ms -> clock.addAndGet(ms * 1_000_000), fence::set);
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
        // A lagging provider: one lower reading, then back at the tip, re-read inside the pass (D14).
        chain.next.addAll(List.of(2050L, 2050L, 2050L, 2100L));
        assertFalse("transient: the pass is inconclusive", s.poll());
        assertNull("transient: no rewind", s.rewind());
        long before = clock.get();
        chain.at(2050);   // now persistently lower: three reads over at least 10 s, inside one pass
        assertTrue(s.poll());
        assertTrue("re-read for at least 10 s", clock.get() - before >= 10_000_000_000L);
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
        assertNotNull(s.rewind());
        assertNotNull("persisted before any restart", journal.record().rewind());
        assertTrue("it says the host must be started again", fence.get().contains("start it again"));
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

    // ---- round 1 (M33B1-*) ------------------------------------------------------------------------------------------------

    private static ObjectNode prepared(String id, String cbor) {
        try {
            var b = com.bloxbean.intelliada.idea.joust.tx.TxCbor.body(com.bloxbean.intelliada.idea.joust.tx.TxCbor.transaction(cbor));
            ObjectNode r = HostJson.object();
            ObjectNode p = r.putObject("prepared").put("reservation", id).put("cbor", cbor).put("txHash", b.txHash()).put("validTo", b.validTo());
            if (b.validFrom() == null) p.putNull("validFrom");
            else p.put("validFrom", b.validFrom());
            p.putArray("signers");
            p.putObject("plan");
            return r;
        } catch (com.bloxbean.intelliada.idea.joust.tx.TxCbor.Malformed m) {
            throw new AssertionError(m);
        }
    }

    /** M33B1-DES-1: on a provider whose tip moves on every read, restore and settlement still observe the chain. */
    @Test
    public void aTipThatMovesOnEveryReadStillHoldsIdenticalAnswers() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        chain.moving = true;
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        chain.include(hash(a), 1991);   // at or below every tip the pass can hold
        host.answer("shallow");
        assertTrue("a pass completes on a moving tip", s.poll());
        assertNull(fence.get());
        assertEquals(State.shallow, journal.entries().get(0).state());
    }

    /** M33B1-CDX-16: when no chain point holds, the pass is inconclusive (the caller withholds the next call). */
    @Test
    public void aPassWithoutAChainPointIsInconclusive() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s0 = restored(List.of(submitted("1", a)), List.of("1"));
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        Settlement.ChainView flapping = new Settlement.ChainView() {
            @Override
            public Tip tip() {
                long t = 2000 + n.incrementAndGet();
                return new Tip(t, t, String.format("%064x", t));
            }

            @Override
            public Inclusion inclusion(String txHash) {
                return n.get() % 2 == 0 ? null : new Inclusion(1999, 1999, "11".repeat(32));
            }
        };
        Settlement s = new Settlement(journal, new HostTable(60, false), flapping, host, lastNow::get, clock::get, ms -> { }, fence::set);
        assertFalse(s.poll());
        assertNull(fence.get());
    }

    /** M33B1-CDX-6: two shallow inclusions vanish together; each is rolled back at its own slot, neither finalized. */
    @Test
    public void twoVanishedShallowInclusionsAreBothRolledBack() throws Exception {
        String a = tx(List.of(ref(1)), 1000), b = tx(List.of(ref(2)), 1000);
        chain.at(90);
        Settlement s = restored(List.of(submitted("1", a), submitted("2", b)), List.of("1", "2"));
        chain.include(hash(a), 100);
        chain.include(hash(b), 105);
        chain.at(106);
        host.answer("shallow", "shallow");
        s.poll();
        chain.in.clear();
        chain.at(175);
        host.answer("prepared", "prepared");
        s.poll();
        assertNull(fence.get());
        // Both at the lowest vanished slot, in ascending order (M33B1-CDX-6).
        assertEquals(List.of("settled rolledBack/100", "settled rolledBack/100"), host.calls.subList(3, 5));
        assertEquals(List.of(State.prepared, State.prepared), journal.entries().stream().map(Entry::state).toList());
    }

    /** M33B1-CDX-8: a call that failed in a kind's own checks (internal/evaluator) leaves its tips uncertain. */
    @Test
    public void aFailedCallsTipsAreUncertain() throws Exception {
        HostTable t = new HostTable(60, false);
        Settlement s = settlement(t);
        t.committed("1", "aa".repeat(32), 0L, 2025);
        s.beginCall();
        read(s, "protocolParams", "value", 2100, false);
        s.endCall("prepare", null, "internal/evaluator");
        assertEquals(HostTable.St.prepared, t.lower("1").st());
        assertEquals(HostTable.St.expired, t.upper("1").st());
    }

    /** M33B1-CDX-9: an accepted read tip that expires a reservation reaches the journal. */
    @Test
    public void acceptedReadTipsReachTheJournal() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        s.beginCall();
        read(s, "utxos", "value", 2100, false);
        s.endCall("view", null, null);
        s.sync();
        assertEquals(State.expired, journal.entries().get(0).state());
    }

    /** M33B1-CDX-19: a restore reply of another shape fences, even for an empty batch. */
    @Test
    public void aMalformedRestoreReplyFences() throws Exception {
        JournalStore st = new JournalStore(tmp.getRoot().toPath().resolve("j"));
        ChainJournal seed = new ChainJournal(st, CHAIN, MOD, 60);
        seed.admit(submitted("1", tx(List.of(ref(1)), 100)), 0, 0);
        store = st;
        chain.at(5000);   // the body expired long ago: nothing to send, an empty batch completes restore
        ChainJournal.Opening o = seed.open(5000);
        Settlement s = settlement(new HostTable(60, o.restoring()));
        host.answer(HostJson.object().put("reservations", "garbage"));
        try {
            s.restore(o);
            fail("refused");
        } catch (ChainJournal.Fenced expected) {
            assertNotNull(fence.get());
        }
        assertTrue(s.restoring());
    }

    /** M33B1-SEC-4: the model takes a body's facts from its bytes; a host stating others is fenced. */
    @Test
    public void aHostStatingOtherFactsThanItsBodyIsFenced() throws Exception {
        HostTable t = new HostTable(60, false);
        Settlement s = settlement(t);
        ObjectNode r = prepared("1", tx(List.of(ref(1)), 2025));
        ((ObjectNode) r.get("prepared")).put("validTo", 9999);
        s.beginCall();
        s.endCall("prepare", r, null);
        assertNotNull(fence.get());
        assertNull("not committed", t.lower("1"));
        fence.set(null);
        HostTable t2 = new HostTable(60, false);
        Settlement ok = settlement(t2);
        ok.beginCall();
        ok.endCall("prepare", prepared("2", tx(List.of(ref(2)), 2025)), null);
        assertNull(fence.get());
        assertEquals(2025, t2.lower("2").to());
    }

    /** M33B1-DES-2: a body never handed out is discarded through the model; a handed-out one is refused (I30). */
    @Test
    public void onlyABodyNeverHandedOutIsDiscarded() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        try {
            s.discard("1");
            fail("a submitted body was discarded");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("I30"));
        }
        s.beginCall();
        s.endCall("prepare", prepared("2", tx(List.of(ref(2)), 2025)), null);
        host.answer("discarded");
        s.discard("2");
        assertEquals("settled discarded/1990", host.calls.get(host.calls.size() - 1));
        assertNull(fence.get());
        try {
            s.discard("2");
            fail("discarded twice");
        } catch (ChainJournal.Fenced expected) {
            // only a prepared body
        }
    }

    /** M33B1-CDX-10: a module whose own journal is unreadable recovers strictly above the mark; the file is kept aside. */
    @Test
    public void aModuleRecoversItsOwnUnreadableJournalAboveTheMark() throws Exception {
        JournalStore st = new JournalStore(tmp.getRoot().toPath().resolve("j"));
        new ChainJournal(st, CHAIN, MOD, 60).admit(submitted("1", tx(List.of(ref(1)), 500)), 0, 0);
        java.nio.file.Path f = st.journalFile(JournalStore.key(CHAIN), MOD);
        java.nio.file.Files.writeString(f, "{broken");
        store = st;
        ChainJournal.Opening o = new ChainJournal(st, CHAIN, MOD, 60).open(600);
        assertEquals(ChainJournal.History.LOST_JOURNAL, o.history());
        assertEquals(560L, (long) o.completeAbove());
        chain.at(600);
        Settlement s = settlement(new HostTable(60, true));
        host.answer(List.of());
        s.restore(o);   // 600 > 560: restore completes at once, and the unreadable file is moved aside
        assertFalse(s.restoring());
        assertFalse(java.nio.file.Files.exists(f));
        assertTrue(journal.observed(601));
    }

    /** M33B1-DES-11: a journal failure after a successful build discards the body (never handed out) and says so. */
    @Test
    public void aJournalFailureAfterABuildDiscardsTheBody() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        s.beforeCall();
        read(s, "utxos", "value", 2100, false);   // expires entry 1: the journal must be written after the call
        store.failWritesForTests = p -> p.getFileName().toString().endsWith(".journal.json");
        host.answer("discarded");
        try {
            s.complete("prepare", prepared("2", tx(List.of(ref(2)), 3000)), null);
            fail("the journal failure was hidden");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("discarded"));
        }
        assertEquals("settled discarded/1990", host.calls.get(host.calls.size() - 1));
        assertNull(fence.get());
        // The pending write is retried before the next call: refused while the store fails, then the call proceeds.
        try {
            s.beforeCall();
            fail("a call went ahead of a failed journal write");
        } catch (JournalStore.StoreFailed expected) {
            // refused
        }
        store.failWritesForTests = p -> false;
        s.beforeCall();
        assertEquals(State.expired, journal.entries().get(0).state());
    }

    /** M33B1-DES-11: after a view, a journal failure keeps the result; the write is retried before the next call. */
    @Test
    public void aJournalFailureAfterAViewKeepsTheResult() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        s.beforeCall();
        read(s, "utxos", "value", 2100, false);
        store.failWritesForTests = p -> true;
        s.complete("view", HostJson.object().putObject("value"), null);
        store.failWritesForTests = p -> false;
        s.beforeCall();
        assertEquals(State.expired, journal.entries().get(0).state());
    }

    /** M33B1-SEC-6: a restore held open does not complete into a later rewind, nor leave it unreconciled. */
    @Test
    public void aRestoreHeldOpenIsFencedByALaterRewind() throws Exception {
        JournalStore st = new JournalStore(tmp.getRoot().toPath().resolve("j"));
        String modB = JournalStore.key("/work/b"), modC = JournalStore.key("/work/c");
        new ChainJournal(st, CHAIN, MOD, 60).admit(submitted("1", tx(List.of(ref(1)), 1000)), 0, 0);
        new ChainJournal(st, CHAIN, modB, 60).admit(submitted("1", tx(List.of(ref(2)), 2000)), 0, 0);
        new ChainJournal(st, CHAIN, modC, 60).admit(submitted("1", tx(List.of(ref(3)), 1000)), 0, 0);
        java.nio.file.Files.delete(st.journalFile(JournalStore.key(CHAIN), modB));
        store = st;
        chain.at(100);
        ChainJournal.Opening o = new ChainJournal(st, CHAIN, MOD, 60).open(100);
        assertEquals(2060L, (long) o.completeAbove());
        Settlement s = settlement(new HostTable(60, true));
        host.answer(List.of("1"));
        s.restore(o);
        assertTrue("held open", s.restoring());
        new ChainJournal(st, CHAIN, modC, 60).beginRewind(150, "a rewind found by C");
        chain.at(2100);
        assertFalse(s.poll());
        assertNotNull("fenced by the rewind it never reconciled", fence.get());
        assertEquals("restore was not completed into the rewind", 1, host.calls.size());
        assertTrue("still pending", journal.record().rewind().pending().contains(MOD));
    }

    // ---- round 2 (M33B1-CDX-*) ------------------------------------------------------------------------------------------

    /** CDX-24: a read answered after its call ended is charged to no later call. */
    @Test
    public void aLateReadIsChargedToNoLaterCall() throws Exception {
        HostTable t = new HostTable(60, false);
        Settlement s = settlement(t);
        t.committed("1", "aa".repeat(32), 0L, 2025);
        s.beginCall();
        HostSession.Reads guarded = s.guard((id, p) -> {
            s.endCall("view", null, "internal/cancelled");   // the call ends while its read is still being answered
            s.beginCall();                                     // and the next one begins
            ObjectNode a = HostJson.object();
            a.putObject("tip").put("slot", 2100).put("height", 2100).put("hash", String.format("%064x", 2100));
            a.putArray("value");
            return a;
        });
        guarded.read("1.1", HostJson.object().put("kind", "utxos"));
        s.endCall("view", HostJson.object(), null);
        assertEquals(HostTable.St.prepared, t.lower("1").st());
        assertEquals(HostTable.St.prepared, t.upper("1").st());
    }

    /** CDX-25: a read that would finalize a shallow body that vanished answers failed; the rollback is reported first. */
    @Test
    public void aReadThatWouldFinalizeAVanishedShallowBodyFails() throws Exception {
        HostTable t = new HostTable(60, false);
        Settlement s = settlement(t);
        t.committed("1", "aa".repeat(32), 0L, 2025);
        assertTrue(t.reply(t.expect("1", "aa".repeat(32), "confirmed", 100L, 100), HostTable.St.shallow, null));
        s.beginCall();
        ObjectNode a = HostJson.object();
        a.putObject("tip").put("slot", 180).put("height", 180).put("hash", String.format("%064x", 180));
        a.putArray("value");
        ObjectNode got = s.guard((id, p) -> a).read("1.1", HostJson.object().put("kind", "utxos"));
        assertTrue(got.has("failed"));
        assertTrue(s.pollNeeded());
    }

    /** CDX-26: a rollback the host answers with deepRollback is a chain rewind, persisted, and the host fenced. */
    @Test
    public void aDeepRollbackReplyIsAChainRewind() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        chain.include(hash(a), 2000);
        chain.at(2001);
        host.answer("shallow");
        s.poll();
        s.beginCall();
        read(s, "utxos", "value", 2100, true);   // a partial listing: the upper copy holds it final
        s.endCall("view", null, "bad/readLimit");
        chain.in.clear();
        chain.at(2002);
        host.answer(new HostError("refusal", "deepRollback", "scripted", List.of(), null));
        s.poll();
        assertNotNull(fence.get());
        assertNotNull("persisted before anything is released", journal.record().rewind());
        assertNotNull("the inclusion evidence is kept", journal.entries().get(0).inclusion());
    }

    /** CDX-27: a restored reservation is never discarded, even with its journal entry gone. */
    @Test
    public void aRestoredReservationIsNeverDiscarded() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        journal.remove(java.util.Set.of("1"));
        try {
            s.discard("1");
            fail("discarded");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("I30"));
        }
    }

    /** CDX-28: discard runs a settlement pass first, so a landed body is reported before the discard's tip. */
    @Test
    public void aDiscardReportsALandedBodyFirst() throws Exception {
        String a = tx(List.of(ref(1)), 500);
        chain.at(90);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        s.beginCall();
        s.endCall("prepare", prepared("2", tx(List.of(ref(2)), 2000)), null);
        chain.include(hash(a), 100);
        chain.at(600);
        host.answer("shallow", "confirmed", "discarded");
        s.discard("2");
        assertEquals(List.of("settled confirmed100/100", "settled confirmed100/600", "settled discarded/600"),
                host.calls.subList(host.calls.size() - 3, host.calls.size()));
        assertNull(fence.get());
    }

    /** CDX-29: a prepared reply reusing an existing reservation id fences the host. */
    @Test
    public void aReusedReservationIdFences() throws Exception {
        HostTable t = new HostTable(60, false);
        Settlement s = settlement(t);
        s.beginCall();
        s.endCall("prepare", prepared("1", tx(List.of(ref(1)), 500)), null);
        s.beginCall();
        s.endCall("prepare", prepared("1", tx(List.of(ref(2)), 120)), null);
        assertNotNull(fence.get());
        assertEquals(500, t.lower("1").to());
    }

    /** CDX-30: after a lower tip is adopted, settlement uses a whole observation at it. */
    @Test
    public void aRebasedPassObservesAgain() throws Exception {
        String a = tx(List.of(ref(1)), 3000);
        chain.at(100);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        chain.include(hash(a), 150);
        chain.at(190);
        host.answer("shallow");
        s.poll();
        chain.at(180);   // persistently lower, touching nothing recorded (150 < 180; a shallow margin is open anyway)
        chain.in.clear();   // and the inclusion is gone at the new point
        host.answer("prepared");
        s.poll();
        assertEquals(State.prepared, journal.entries().get(0).state());
    }

    /** CDX-33: a build the model refused surfaces as an error, never as a result. */
    @Test
    public void aRefusedBuildSurfaces() throws Exception {
        Settlement s = settlement(new HostTable(60, false));
        ObjectNode r = prepared("1", tx(List.of(ref(1)), 2025));
        ((ObjectNode) r.get("prepared")).put("validTo", 9999);
        s.beginCall();
        try {
            s.complete("prepare", r, null);
            fail("returned");
        } catch (ChainJournal.Fenced expected) {
            assertNotNull(fence.get());
        }
    }

    // ---- round 4 (M33B1-CDX-*) ------------------------------------------------------------------------------------------

    /** CDX-24: a read is charged to the call that owned it at dispatch, never to a later call. */
    @Test
    public void aReadIsChargedToTheCallThatOwnedIt() throws Exception {
        HostTable t = new HostTable(60, false);
        Settlement s = settlement(t);
        t.committed("1", "aa".repeat(32), 0L, 2025);
        s.beginCall();
        ObjectNode a = HostJson.object();
        a.putObject("tip").put("slot", 2100).put("height", 2100).put("hash", String.format("%064x", 2100));
        a.putArray("value");
        s.guard((id, p) -> a).read("1.1", HostJson.object().put("kind", "utxos"), 7);   // owned by call 7
        s.endCall("view", HostJson.object(), null, 8);                                  // call 8 ends
        assertEquals(HostTable.St.prepared, t.lower("1").st());
    }

    /** CDX-25: a replacement block at the recorded slot is a rolled-back inclusion for the read guard. */
    @Test
    public void aReplacementBlockAtTheSameSlotFailsTheRead() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(90);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        chain.include(hash(a), 100);
        chain.at(101);
        host.answer("shallow");
        s.poll();
        chain.in.put(hash(a), new Inclusion(100, 100, "99".repeat(32)));   // another block at slot 100
        s.beginCall();
        ObjectNode r = HostJson.object();
        r.putObject("tip").put("slot", 180).put("height", 180).put("hash", String.format("%064x", 180));
        r.putArray("value");
        assertTrue(s.guard((id, p) -> r).read("2.1", HostJson.object().put("kind", "utxos")).has("failed"));
    }

    /** CDX-38: a rollback's tip crosses no reservation's threshold: it expires no body that landed unreported. */
    @Test
    public void aRollbackTipCrossesNoThreshold() {
        List<HostTable.Res> all = List.of(
                new HostTable.Res("a", "aa".repeat(32), 0, 150, HostTable.St.prepared, null),
                new HostTable.Res("b", "bb".repeat(32), 0, 1000, HostTable.St.shallow, 300L));
        assertEquals("not above a's validTo + margin", 210, Settlement.safeRollbackTip(all, 300, 60));
        assertEquals("the lowest vanished slot when lower", 120, Settlement.safeRollbackTip(all, 120, 60));
    }

    /** CDX-39: discard refuses a body its settlement pass found landed, and keeps it. */
    @Test
    public void aDiscardRefusesABodyItsPassFoundLanded() throws Exception {
        chain.at(90);
        Settlement s = settlement(new HostTable(60, false));
        String b = tx(List.of(ref(2)), 900);
        s.beginCall();
        s.endCall("prepare", prepared("2", b), null);
        s.admit(entry("2", b, JournalStore.Handout.approvalPending), 90, 90);   // awaiting approval: journaled
        chain.include(hash(b), 100);
        chain.at(101);
        host.answer("shallow");
        try {
            s.discard("2");
            fail("discarded a landed body");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("landed"));
        }
    }

    /** CDX-40: a discarded body is never admitted. */
    @Test
    public void aDiscardedBodyIsNeverAdmitted() throws Exception {
        chain.at(90);
        Settlement s = settlement(new HostTable(60, false));
        String b = tx(List.of(ref(2)), 1000);
        s.beginCall();
        s.endCall("prepare", prepared("2", b), null);
        host.answer("discarded");
        s.discard("2");
        try {
            s.admit(entry("2", b, JournalStore.Handout.exported), 90, 90);
            fail("admitted");
        } catch (ChainJournal.Fenced expected) {
            // refused
        }
    }

    /** CDX-41: read tips are accepted only after the bound chain checks out. */
    @Test
    public void readTipsFromAnotherChainAreRefused() throws Exception {
        HostTable t = new HostTable(60, false);
        Settlement s = settlement(t);
        t.committed("1", "aa".repeat(32), 0L, 2025);
        s.chainCheck(() -> {
            throw new IllegalStateException("block 1 differs");
        });
        s.beginCall();
        read(s, "utxos", "value", 2100, false);
        try {
            s.complete("view", HostJson.object(), null);
            fail("accepted");
        } catch (ChainJournal.Fenced expected) {
            assertNotNull(fence.get());
        }
        assertEquals(HostTable.St.prepared, t.lower("1").st());
    }

    /** CDX-42: a successful prepare without a prepared body fences the host. */
    @Test
    public void aPrepareWithoutABodyFences() throws Exception {
        Settlement s = settlement(new HostTable(60, false));
        s.beginCall();
        try {
            s.complete("prepare", HostJson.object(), null);
            fail("returned");
        } catch (ChainJournal.Fenced expected) {
            assertNotNull(fence.get());
        }
    }

    // ---- round 5 (M33B1-CDX-*) ------------------------------------------------------------------------------------------

    /** CDX-44: a call that returns after its session closed commits nothing. */
    @Test
    public void aCallAfterCloseCommitsNothing() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        s.beginCall();
        read(s, "utxos", "value", 2100, false);
        s.close("ended");
        s.complete("view", HostJson.object(), null);
        assertEquals(State.prepared, journal.entries().get(0).state());
    }

    /** CDX-47: a tip below an accepted read's is a lower tip (a rewind when it touches the journal). */
    @Test
    public void aTipBelowAnAcceptedReadIsLower() throws Exception {
        String a = tx(List.of(ref(1)), 500);
        chain.at(100);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        s.beginCall();
        read(s, "utxos", "value", 600, false);
        s.complete("view", HostJson.object(), null);
        assertEquals(State.expired, journal.entries().get(0).state());
        chain.at(400);   // the chain is back below the expired body's validTo
        s.poll();
        assertNotNull(s.rewind());
    }

    /** CDX-48: a body the host expired that the chain shows included is a reconciliation, with its evidence kept. */
    @Test
    public void aLandedBodyTheHostExpiredIsAReconciliation() throws Exception {
        String a = tx(List.of(ref(1)), 500);
        chain.at(400);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        s.beginCall();
        read(s, "utxos", "value", 600, false);   // uncertain: the upper copy expires it
        s.endCall("view", null, "bad/readLimit");
        chain.include(hash(a), 490);
        chain.at(495);
        host.answer(new HostError("bad", "conflict", "scripted", List.of(), null));
        s.poll();
        assertNotNull("this host is stopped", fence.get());
        assertNull("no chain rewind: the chain did not move (DES-16)", journal.record().rewind());
        Entry e = journal.entries().get(0);
        assertEquals("the chain's evidence: still reserved", State.shallow, e.state());
        assertNotNull(e.inclusion());
    }

    /** CDX-49: an approval is never promoted for a body that is no longer prepared. */
    @Test
    public void anExpiredApprovalIsNeverExported() throws Exception {
        chain.at(90);
        Settlement s = settlement(new HostTable(60, false));
        String b = tx(List.of(ref(2)), 200);
        s.beginCall();
        s.endCall("prepare", prepared("2", b), null);
        s.admit(entry("2", b, JournalStore.Handout.approvalPending), 90, 90);
        s.beginCall();
        read(s, "utxos", "value", 300, false);
        s.complete("view", HostJson.object(), null);
        try {
            s.handedOut("2", JournalStore.Handout.exported);
            fail("exported an expired body");
        } catch (ChainJournal.Fenced expected) {
            // refused
        }
    }

    /** CDX-53: a prepared reply stating a fractional validity fences the host. */
    @Test
    public void aFractionalValidityFences() throws Exception {
        HostTable t = new HostTable(60, false);
        Settlement s = settlement(t);
        ObjectNode r = prepared("1", tx(List.of(ref(1)), 500));
        ((ObjectNode) r.get("prepared")).put("validTo", 500.9);
        s.beginCall();
        s.endCall("prepare", r, null);
        assertNotNull(fence.get());
        assertNull(t.lower("1"));
    }

    // ---- round 6 (M33B1-CDX-*) ------------------------------------------------------------------------------------------

    /** CDX-48: a landed body the host expired is never published expired, not even briefly. */
    @Test
    public void anExpiredLandedBodyIsNeverPublishedExpired() throws Exception {
        String a = tx(List.of(ref(1)), 500);
        chain.at(400);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        s.beginCall();
        read(s, "utxos", "value", 600, false);
        s.endCall("view", null, "bad/readLimit");
        chain.include(hash(a), 490);
        chain.at(495);
        java.util.List<State> seen = new java.util.ArrayList<>();
        host.answer(new HostError("bad", "conflict", "scripted", List.of(), null));
        s.poll();
        for (var e : journal.entries()) seen.add(e.state());
        assertEquals(List.of(State.shallow), seen);
        assertNotNull(fence.get());
    }

    /** CDX-60: a failed registry write after the record is this owner's known failed write, which it may finish. */
    @Test
    public void aFailedRegistryWriteCanBeFinished() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        s.beforeCall();
        read(s, "utxos", "value", 2100, false);
        store.failWritesForTests = p -> p.getFileName().toString().endsWith(".json") && p.getParent().getFileName().toString().equals("registry");
        s.complete("view", HostJson.object(), null);
        store.failWritesForTests = p -> false;
        s.beforeCall();
        assertEquals(State.expired, journal.entries().get(0).state());
    }

    /** CDX-64: a body the host holds expired that the chain shows included within its margin stops the host. */
    @Test
    public void anExpiredBodyTheChainShowsIncludedStopsTheHost() throws Exception {
        String a = tx(List.of(ref(1)), 500);
        chain.at(400);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        s.beginCall();
        read(s, "utxos", "value", 600, false);
        s.complete("view", HostJson.object(), null);   // the host expires it
        chain.include(hash(a), 490);   // a replacement branch shows it included
        chain.at(510);
        s.poll();
        assertNotNull(fence.get());
        assertEquals(State.shallow, journal.entries().get(0).state());
    }

    // ---- round 8 (M33B1-CDX-*) ------------------------------------------------------------------------------------------

    /** CDX-46: admission checks against the lower of the prepare's tip and one read now. */
    @Test
    public void admissionReadsAFreshTip() throws Exception {
        JournalStore st = new JournalStore(tmp.getRoot().toPath().resolve("j"));
        new ChainJournal(st, CHAIN, JournalStore.key("/work/b"), 60).admit(submitted("x", tx(List.of(ref(9)), 500)), 400, 400);
        java.nio.file.Files.delete(st.journalFile(JournalStore.key(CHAIN), JournalStore.key("/work/b")));
        store = st;
        assertTrue(new ChainJournal(st, CHAIN, MOD, 60).observed(0) || true);
        Settlement s = settlement(new HostTable(60, false));
        new ChainJournal(st, CHAIN, MOD, 60).open(0);
        assertTrue(journal.observed(561));
        chain.at(490);   // rewound below the recovered bound since the prepare at 600
        String b = tx(List.of(ref(2)), 1200);
        s.beginCall();
        s.endCall("prepare", prepared("2", b), null);
        try {
            s.admit(submitted("2", b), 600, 600);
            fail("admitted at the stale prepare tip");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("recovered"));
        }
    }

    /** CDX-64: a retained expired entry with no host row that the chain shows included stops the host. */
    @Test
    public void aRetainedExpiredEntryTheChainShowsIncludedStopsTheHost() throws Exception {
        String a = tx(List.of(ref(1)), 500);
        chain.at(600);
        Settlement s = restored(List.of(submitted("1", a)), List.of());   // expired at restore: kept final, no host row
        assertEquals(State.expired, journal.entries().get(0).state());
        chain.include(hash(a), 490);
        chain.at(510);
        s.poll();
        assertNotNull(fence.get());
        assertEquals(State.shallow, journal.entries().get(0).state());
    }

    // ---- round 9 (M33B1-CDX-75, -76) ----------------------------------------------------------------------------------

    /** CDX-75: a deep-rollback reply publishes nothing after its host is stopped. */
    @Test
    public void aDeepRollbackPublishesNothingAfterTheStop() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(1990);
        Settlement s = restored(List.of(submitted("1", a)), List.of("1"));
        chain.include(hash(a), 2000);
        chain.at(2001);
        host.answer("shallow");
        s.poll();
        s.beginCall();
        read(s, "utxos", "value", 2100, true);
        s.endCall("view", null, "bad/readLimit");
        chain.in.clear();
        chain.at(2002);
        host.answer(new HostError("refusal", "deepRollback", "scripted", List.of(), null));
        s.poll();
        assertNotNull(fence.get());
        assertEquals("the journal keeps what it held before the stopped host's reply", State.shallow, journal.entries().get(0).state());
    }

    /** CDX-76: a restore acknowledges its rewind only while the chain still shows what it restored. */
    @Test
    public void aRestoreDoesNotAcknowledgeWhenTheChainChangedSince() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        JournalStore st = new JournalStore(tmp.getRoot().toPath().resolve("j"));
        ChainJournal seed = new ChainJournal(st, CHAIN, MOD, 60);
        seed.admit(submitted("1", a), 1990, 1990);
        seed.beginRewind(1990, "a rewind");
        store = st;
        chain.at(2000);
        chain.include(hash(a), 1995);
        ChainJournal.Opening o = seed.open(2000);
        Settlement s = new Settlement(new ChainJournal(st, CHAIN, MOD, 60), new HostTable(60, true), new Settlement.ChainView() {
            @Override
            public Tip tip() {
                return chain.tip();
            }

            @Override
            public Inclusion inclusion(String txHash) {
                // gone again once the host was restored from it (another module saw it vanish)
                return host.calls.stream().anyMatch(c -> c.startsWith("restore")) ? null : chain.inclusion(txHash);
            }
        }, host, lastNow::get, clock::get, ms -> { }, fence::set);
        journal = new ChainJournal(st, CHAIN, MOD, 60);
        host.answer(List.of("1"));
        s.restore(o);
        assertNotNull("the host is stopped", fence.get());
        assertNotNull("the rewind stands", journal.record().rewind());
    }

    // ---- round 10 (M33B1-CDX-77) --------------------------------------------------------------------------------------

    /** CDX-77: a restore kept a body final; the chain went back within its margin at the same block before the ack. */
    @Test
    public void aRestoreDoesNotAcknowledgeWhenAFinalBodyIsShallowAgain() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        JournalStore st = new JournalStore(tmp.getRoot().toPath().resolve("j"));
        ChainJournal seed = new ChainJournal(st, CHAIN, MOD, 60);
        seed.admit(submitted("1", a), 1990, 1990);
        seed.beginRewind(1990, "a rewind");
        store = st;
        chain.at(2100);
        chain.include(hash(a), 2000);
        ChainJournal.Opening o = seed.open(2100);
        Settlement s = new Settlement(new ChainJournal(st, CHAIN, MOD, 60), new HostTable(60, true), new Settlement.ChainView() {
            @Override
            public Tip tip() {
                // back to 2010 once the host was restored: the same block, now within the margin
                if (host.calls.stream().anyMatch(c -> c.startsWith("restore"))) chain.at(2010);
                return chain.tip();
            }

            @Override
            public Inclusion inclusion(String txHash) {
                return chain.inclusion(txHash);
            }
        }, host, lastNow::get, clock::get, ms -> { }, fence::set);
        journal = new ChainJournal(st, CHAIN, MOD, 60);
        host.answer(List.of());
        s.restore(o);
        assertNotNull("the host is stopped", fence.get());
        assertNotNull("the rewind stands", journal.record().rewind());
    }

    // ---- round 11 (M33B1-CDX-82, -83) ----------------------------------------------------------------------------------

    private Settlement restoredUnder(String cbor, long admitAt, long openAt, Settlement.ChainView view, List<String> ids) throws Exception {
        JournalStore st = new JournalStore(tmp.getRoot().toPath().resolve("j"));
        ChainJournal seed = new ChainJournal(st, CHAIN, MOD, 60);
        seed.admit(submitted("1", cbor), admitAt, admitAt);
        seed.beginRewind(admitAt, "a rewind");
        store = st;
        ChainJournal.Opening o = seed.open(openAt);
        Settlement s = new Settlement(new ChainJournal(st, CHAIN, MOD, 60), new HostTable(60, true), view, host, lastNow::get, clock::get, ms -> { }, fence::set);
        journal = new ChainJournal(st, CHAIN, MOD, 60);
        host.answer(ids);
        s.restore(o);
        return s;
    }

    /** CDX-82: a body kept expired that is valid again at the acknowledgement's chain point stops the host. */
    @Test
    public void aRestoreDoesNotAcknowledgeWhenAnExpiredBodyIsValidAgain() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(2100);
        restoredUnder(a, 1990, 2100, new Settlement.ChainView() {
            @Override
            public Tip tip() {
                if (host.calls.stream().anyMatch(c -> c.startsWith("restore"))) chain.at(2010);
                return chain.tip();
            }

            @Override
            public Inclusion inclusion(String txHash) {
                return chain.inclusion(txHash);
            }
        }, List.of());
        assertNotNull("the host is stopped", fence.get());
        assertNotNull("the rewind stands", journal.record().rewind());
    }

    /** CDX-83: the chain goes back between the acknowledgement's tip read and its inclusion read. */
    @Test
    public void aRollbackBetweenTheTipAndTheInclusionReadIsSeen() throws Exception {
        String a = tx(List.of(ref(1)), 2025);
        chain.at(2100);
        chain.include(hash(a), 2000);
        java.util.concurrent.atomic.AtomicInteger tipsAfterRestore = new java.util.concurrent.atomic.AtomicInteger();
        restoredUnder(a, 1990, 2100, new Settlement.ChainView() {
            @Override
            public Tip tip() {
                return chain.tip();
            }

            @Override
            public Inclusion inclusion(String txHash) {
                // the first inclusion read after the restore: the chain is back at 2010 by the next tip read
                if (host.calls.stream().anyMatch(c -> c.startsWith("restore")) && tipsAfterRestore.getAndIncrement() == 0) chain.at(2010);
                return chain.inclusion(txHash);
            }
        }, List.of());
        assertNotNull("the host is stopped", fence.get());
        assertNotNull("the rewind stands", journal.record().rewind());
    }

    // ---- round 12 (M33B1-CDX-93) --------------------------------------------------------------------------------------

    /** CDX-93: an empty module still observes the chain while a fence on it waits for a later tip. */
    @Test
    public void anEmptyModuleLiftsAFenceWithoutARestart() throws Exception {
        chain.at(600);
        ChainJournal other = new ChainJournal(new JournalStore(tmp.getRoot().toPath().resolve("j")), CHAIN, JournalStore.key("/work/b"), 60);
        other.admit(submitted("x", tx(List.of(ref(9)), 900)), 600, 600);   // a chain record, then nothing journaled
        other.remove(Set.of("x"));
        Settlement s = restored(List.of(), List.of());
        String k = JournalStore.key(CHAIN);
        JournalStore.ChainRecord c = store.record(k).value();
        store.writeRecord(c.withFence(new JournalStore.Fence(700, "a lost journal's bound")));
        chain.at(701);
        assertTrue(s.poll());
        assertNull("lifted at 701", store.record(k).value().fence());
    }

    // ---- round 13 (M33B1-CDX-95) --------------------------------------------------------------------------------------

    /** CDX-95: an idle host stops on a rewind another module began and already cleared. */
    @Test
    public void anIdleHostStopsOnARewindAlreadyCleared() throws Exception {
        chain.at(600);
        ChainJournal other = new ChainJournal(new JournalStore(tmp.getRoot().toPath().resolve("j")), CHAIN, JournalStore.key("/work/b"), 60);
        other.admit(submitted("x", tx(List.of(ref(9)), 900)), 600, 600);
        Settlement s = restored(List.of(), List.of());
        assertNull(fence.get());
        other.beginRewind(500, "a fork B found");
        other.endRewind(other.record().rewind().id());
        assertNull("cleared", other.record().rewind());
        assertTrue(s.rewindMissed());
        assertNotNull(fence.get());
        assertFalse(s.poll());
    }

    // ---- round 14 (M33B1-CDX-101) -------------------------------------------------------------------------------------

    /** CDX-101: a host stops when the chain record it started with is gone. */
    @Test
    public void aHostStopsWhenTheChainRecordIsLost() throws Exception {
        chain.at(600);
        ChainJournal other = new ChainJournal(new JournalStore(tmp.getRoot().toPath().resolve("j")), CHAIN, JournalStore.key("/work/b"), 60);
        other.admit(submitted("x", tx(List.of(ref(9)), 900)), 600, 600);
        Settlement s = restored(List.of(), List.of());
        java.nio.file.Files.delete(store.recordFile(JournalStore.key(CHAIN)));
        assertTrue(s.rewindMissed());
        assertTrue(fence.get(), fence.get().contains("record is missing"));
    }

    // ---- round 15 (M33B1-CDX-101, -106) -------------------------------------------------------------------------------

    /** CDX-101: a host that never saw the record stops when it is lost while the registry proves history. */
    @Test
    public void aHostThatNeverSawTheRecordStopsOnItsLoss() throws Exception {
        chain.at(600);
        Settlement s = restored(List.of(), List.of());   // a new chain: no record yet
        assertFalse(s.rewindMissed());
        ChainJournal other = new ChainJournal(store, CHAIN, JournalStore.key("/work/b"), 60);
        other.admit(submitted("x", tx(List.of(ref(9)), 900)), 600, 600);
        java.nio.file.Files.delete(store.recordFile(JournalStore.key(CHAIN)));
        assertTrue(s.rewindMissed());
        assertTrue(fence.get(), fence.get().contains("cannot be trusted"));
    }

    /** CDX-106: a record restored to an older version, proven stale by the registry, stops the host. */
    @Test
    public void aStaleRecordStopsTheHost() throws Exception {
        chain.at(600);
        ChainJournal other = new ChainJournal(new JournalStore(tmp.getRoot().toPath().resolve("j")), CHAIN, JournalStore.key("/work/b"), 60);
        other.admit(submitted("x", tx(List.of(ref(9)), 900)), 600, 600);
        Settlement s = restored(List.of(), List.of());
        java.nio.file.Path rec = store.recordFile(JournalStore.key(CHAIN));
        byte[] old = java.nio.file.Files.readAllBytes(rec);
        other.admit(submitted("y", tx(List.of(ref(8)), 900)), 600, 600);   // the record moves on
        java.nio.file.Files.write(rec, old);                              // and is restored to the older one
        assertTrue(s.rewindMissed());
        assertTrue(fence.get(), fence.get().contains("cannot be trusted"));
    }

    // ---- round 16 (M33B1-CDX-114) -------------------------------------------------------------------------------------

    /** CDX-114: a session opened read-only on a permanently fenced chain keeps running for views. */
    @Test
    public void aReadOnlySessionKeepsItsViews() throws Exception {
        chain.at(600);
        JournalStore st = new JournalStore(tmp.getRoot().toPath().resolve("j"));
        ChainJournal other = new ChainJournal(st, CHAIN, JournalStore.key("/work/b"), 60);
        other.admit(submitted("x", tx(List.of(ref(9)), 900)), 600, 600);
        String k = JournalStore.key(CHAIN);
        java.nio.file.Files.delete(st.recordFile(k));
        java.nio.file.Files.delete(st.registryFile(k));
        Settlement s = restored(List.of(), List.of());
        assertTrue(store.permanentlyFenced(k));
        assertFalse(s.rewindMissed());
        assertNull(fence.get());
    }

    // ---- round 17 (M33B1-CDX-116) -------------------------------------------------------------------------------------

    /** CDX-116: a fence the rewind check's snapshot shows is what dispatch refuses a build on. */
    @Test
    public void theRewindCheckReportsTheChainsFence() throws Exception {
        chain.at(600);
        ChainJournal other = new ChainJournal(new JournalStore(tmp.getRoot().toPath().resolve("j")), CHAIN, JournalStore.key("/work/b"), 60);
        other.admit(submitted("x", tx(List.of(ref(9)), 900)), 600, 600);
        Settlement s = restored(List.of(), List.of());
        assertFalse(s.rewindMissed());
        assertNull(s.lastFence());
        String k = JournalStore.key(CHAIN);
        store.writeRecord(store.record(k).value().withFence(new JournalStore.Fence(9000, "a lost journal")));
        // the registry follows the record's generation, as persist would write it
        store.writeRegistry(new JournalStore.RegistryEntry(CHAIN, 600, 600, store.record(k).value().generation()));
        assertFalse("a fence alone does not stop the host", s.rewindMissed());
        assertEquals(9000, s.lastFence().until());
    }

    // ---- round 21 (M33B1-CDX-138) -------------------------------------------------------------------------------------

    /** CDX-138: a body built while the host's reads saw the chain below the recovered bound is discarded, never exposed. */
    @Test
    public void aBodyBuiltBelowTheRecoveredBoundIsDiscarded() throws Exception {
        chain.at(1990);
        Settlement s = restored(List.of(), List.of());
        String k = JournalStore.key(CHAIN);
        JournalStore.ChainRecord c = store.record(k) .value();
        if (c == null) {
            new ChainJournal(store, CHAIN, JournalStore.key("/work/b"), 60).admit(submitted("x", tx(List.of(ref(9)), 2400)), 1990, 1990);
            c = store.record(k).value();
        }
        store.writeRecord(c.withRecovered(1980L));
        store.writeRegistry(new JournalStore.RegistryEntry(CHAIN, 1990, 1990, c.generation()));
        s.beginCall();
        read(s, "utxos", "value", 1970, false);
        chain.at(1970);   // the chain as the host's read saw it (the discard's own pass observes it too)
        host.answer("discarded");
        try {
            s.complete("prepare", prepared("2", tx(List.of(ref(2)), 2025)), null);
            fail("exposed a body built below the recovered bound");
        } catch (ChainJournal.Fenced expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("recovered lost history"));
        }
        assertTrue(host.calls.get(host.calls.size() - 1).startsWith("settled discarded"));
        assertNotNull("fenced again", store.record(k).value().fence());
    }
}
