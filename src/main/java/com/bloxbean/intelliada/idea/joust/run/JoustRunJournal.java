package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.joust.host.HostSession;
import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity;
import com.bloxbean.intelliada.idea.joust.journal.ChainJournal;
import com.bloxbean.intelliada.idea.joust.journal.HostTable;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore;
import com.bloxbean.intelliada.idea.joust.journal.ProviderChain;
import com.bloxbean.intelliada.idea.joust.journal.Settlement;
import com.bloxbean.intelliada.idea.joust.journal.TimePolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ADR-005 D17 for one host session (M3.3b1): the chain's history is classified before the host starts (so
 * {@code initialize} carries {@code restoring}), the module's host lock is held for the session (I33), reverse reads go
 * through the settlement guard, the host is restored once initialized, and settlement runs before forward calls and on
 * a timer. It also carries the operations M3.3b2's signer uses (admission, handout, discard); in M3.3b1 nothing in
 * production calls admission (§12 E10's architecture test).
 */
public final class JoustRunJournal implements AutoCloseable {

    private static final com.intellij.openapi.diagnostic.Logger LOG = com.intellij.openapi.diagnostic.Logger.getInstance(JoustRunJournal.class);

    /** Checks the bound chain before a timed pass (the block-1 identity, as forward calls do; M33B1-CDX-12). */
    @FunctionalInterface
    interface Check {
        void run() throws Exception;
    }

    private final ChainJournal journal;
    private final ChainJournal.Opening opening;
    private final HostTable table;
    private final Settlement settlement;
    private final JournalStore.Held moduleLock;
    private final AtomicReference<HostSession> session = new AtomicReference<>();
    private final AtomicLong lastNow = new AtomicLong();
    private final AtomicReference<String> fenced = new AtomicReference<>();
    private final long slotMs;
    private final long settlementSlots;
    private final java.util.concurrent.ScheduledExecutorService timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "JOUST settlement");
        t.setDaemon(true);
        return t;
    });

    private JoustRunJournal(ChainJournal journal, ChainJournal.Opening opening, JournalStore.Held moduleLock, JoustRunSession.Binding b,
                            long settlementSlots) {
        this.journal = journal;
        this.opening = opening;
        this.moduleLock = moduleLock;
        this.settlementSlots = settlementSlots;
        this.slotMs = b.identity().slotLengthMs();
        this.table = new HostTable(settlementSlots, opening.restoring());
        this.settlement = new Settlement(journal, table, new ProviderChain(b.api().copy(), Duration.ofSeconds(10)), (method, params) -> {
            HostSession s = session.get();
            if (s == null) throw new IllegalStateException("the host is not started");
            return s.call(method, params, JoustRunSession.CALL_TIMEOUT, () -> false);
        }, lastNow::get, System::nanoTime, Thread::sleep, why -> {
            fenced.set(why);
            HostSession s = session.get();
            if (s != null) s.close();   // the host is stopped; its reservations stay journaled for a restored one
        });
    }

    /**
     * Before the host starts: takes the module's host lock on the chain and classifies the chain's history at a tip read
     * now (D17; one read: the classification needs only a base, and a later tip only raises it; M33B1-DES-1). The store
     * is the IDE system directory's unless {@code store} is given (tests).
     */
    public static JoustRunJournal open(JoustRunSession.Binding b, Path moduleDir, @Nullable JournalStore store) throws Exception {
        JournalStore s = store != null ? store : JournalStore.inSystemDirectory();
        String chain = b.identity().chainKey();
        String module = JournalStore.key(moduleDir.toRealPath().toString());
        long settlement = TimePolicy.settlementSlots(b.identity().networkClass());
        JournalStore.Held lock = s.ownModule(JournalStore.key(chain), module);
        try {
            ChainJournal j = new ChainJournal(s, chain, module, settlement);
            ProviderChain provider = new ProviderChain(b.api().copy(), Duration.ofSeconds(10));
            ChainJournal.Opening o = j.open(() -> provider.tip().slot(), provider::inclusion);
            return new JoustRunJournal(j, o, lock, b, settlement);
        } catch (Exception | Error e) {
            lock.close();
            throw e;
        }
    }

    public boolean restoring() {
        return opening.restoring();
    }

    public ChainJournal.History history() {
        return opening.history();
    }

    /** The margin this journal settles with: it must be the {@code settlementSlots} the host was initialized with. */
    long settlementSlots() {
        return settlementSlots;
    }

    /** The reverse-read answerer for the host, through the settlement guard. */
    public HostSession.Reads reads(HostSession.Reads delegate) {
        return settlement.guard(delegate);
    }

    /**
     * After {@code initialize} and the catalog check: restores the host from the journal (D17 "Restart") and starts the
     * settlement timer. Each timed pass takes {@code callLock} (the session's call lock, held by the caller now) and
     * checks the bound chain first.
     */
    void started(HostSession s, Object callLock, Check check) throws Exception {
        session.set(s);
        s.onEnded(why -> close());
        settlement.chainCheck(check::run);   // around the restore's observation and every pass (M33B1-CDX-12)
        settlement.restore(opening);
        long every = tickMillis();
        timer.scheduleWithFixedDelay(() -> {
            synchronized (callLock) {
                try {
                    if (session.get() == null || session.get().ended() != null) return;
                    if (!settlement.restoring() && journal.entries().isEmpty()) return;
                    settlement.poll();   // the chain check runs inside, after the observation
                } catch (Exception e) {
                    LOG.info("JOUST settlement pass: " + e.getClass().getSimpleName());
                }
            }
        }, every, every, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Why building on this session is blocked, or null (views, listings and decoding work either way). */
    public @Nullable String buildingBlocked() {
        if (opening.permanent() != null) return opening.permanent();
        if (fenced.get() != null) return "the dev host was stopped: " + fenced.get();
        if (settlement.rewind() != null) return settlement.rewind();
        if (settlement.restoring()) {
            return opening.completeAbove() == null ? "the dev host is being restored"
                    : "IntelliAda's journal for this chain was incomplete; building waits until the chain passes slot "
                    + opening.completeAbove() + waitFor(opening.completeAbove());
        }
        if (table.deepRollback()) return "a final transaction was rolled back; stop the dev host and start it again";
        // The chain's record: a fence or a rewind another host of this chain found (M33B1-DES-3).
        try {
            JournalStore.ChainRecord c = journal.record();
            if (c != null && c.rewind() != null && !c.rewind().pending().isEmpty() && c.rewind().pending().contains(journal.module())) {
                return "a rewind on this chain is being reconciled (" + c.rewind().reason() + ")";
            }
            if (c != null && c.rewind() != null) return "another module on this chain is reconciling a rewind";
            if (c != null && c.fence() != null) {
                return "building waits until the chain passes slot " + c.fence().until() + waitFor(c.fence().until());
            }
        } catch (Exception e) {
            return "IntelliAda's journal for this chain cannot be read (" + e.getClass().getSimpleName() + ")";
        }
        return null;
    }

    /** A rough wall time until {@code slot} at the session's slot length (M33B1-DES-4). */
    private String waitFor(long slot) {
        long left = slot - settlement.highestSlot();
        if (left <= 0) return "";
        long minutes = Math.max(1, left * slotMs / 60_000);
        return minutes < 120 ? " (about " + minutes + " min)" : " (about " + (minutes / 60) + " h)";
    }

    /**
     * Before a forward call (call lock held): settlement first when anything is live or an inclusion is unreported; an
     * inconclusive pass withholds the call while anything is live (M33B1-CDX-16).
     */
    void beforeCall(long now) throws Exception {
        lastNow.accumulateAndGet(now, Math::max);
        if (mustSettleFirst(journal.entries(), settlement.pollNeeded())) {
            if (!settlement.poll()) {
                String why = settlement.fenced() != null ? "the dev host was stopped: " + settlement.fenced()
                        : "the chain kept moving while IntelliAda settled its transactions; the call was not sent. Try again";
                throw new ChainJournal.Fenced(why);
            }
        }
        settlement.beforeCall();
    }

    /**
     * Whether a forward call waits for a settlement pass (M33B1-DES-10): only while a reservation can still move (a live
     * entry) or an inclusion is unreported. Final entries kept for their retention never hold a view back; the timer
     * pass looks after them.
     */
    static boolean mustSettleFirst(java.util.List<JournalStore.Entry> entries, boolean pollNeeded) {
        return pollNeeded || entries.stream().anyMatch(JournalStore.Entry::live);
    }

    /** After a forward call (call lock held), once, with its outcome (M33B1-DES-11). */
    void afterCall(String method, @Nullable JsonNode result, @Nullable String error) throws Exception {
        // The call this thread dispatched, carried with its own outcome (M33B1-CDX-24, -DES-15).
        settlement.complete(method, result, error, HostSession.callOfThisThread());
    }

    /** D14: a chain far below the session's now is a rewind of the chain, recorded for every module before the stop. */
    void clockRewound(long tip, String why) throws Exception {
        journal.beginRewind(tip, why);
    }

    /**
     * Discards a body the host prepared that was never handed out (D17; call lock held by the caller). A handed-out one
     * is refused (I30).
     */
    void discard(String reservation) throws Exception {
        settlement.discard(reservation);
    }

    /** Journals a body being handed out (I25; M3.3b2, behind the signing gate). */
    void admit(JournalStore.Entry e, long tip, long now) throws Exception {
        String blocked = buildingBlocked();   // a fenced or blocked session admits nothing (M33B1-CDX-33)
        if (blocked != null) throw new ChainJournal.Fenced(blocked);
        settlement.admit(e, tip, now);
    }

    /** Records how a journaled body was handed out (forward only; serialized with discard). */
    void handedOut(String reservation, JournalStore.Handout h) throws Exception {
        settlement.handedOut(reservation, h);
    }

    /** D17: at least every {@code settlementSlots/2} slots while anything is live. */
    long tickMillis() {
        return Math.max(5_000, settlementSlots / 2 * slotMs);
    }

    ChainJournal journal() {
        return journal;
    }

    Settlement settlement() {
        return settlement;
    }

    @Override
    public void close() {
        timer.shutdownNow();
        // After any pass or restore in progress, and with nothing more committed, the module's lock is released
        // (M33B1-CDX-44): a successor never races this session's last write.
        settlement.close("the session ended");
        synchronized (settlement) {
            moduleLock.close();
        }
    }

    /** {@code initialize}'s parameters with this session's {@code restoring}. */
    public static ObjectNode initParams(NetworkIdentity.Identity id, boolean restoring) {
        ObjectNode n = JoustRunSession.initParams(id);
        n.put("restoring", restoring);
        return n;
    }
}
