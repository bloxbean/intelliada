package com.bloxbean.intelliada.idea.joust.run;

import com.bloxbean.intelliada.idea.joust.host.HostSession;
import com.bloxbean.intelliada.idea.joust.host.chain.NetworkIdentity;
import com.bloxbean.intelliada.idea.joust.journal.ChainJournal;
import com.bloxbean.intelliada.idea.joust.journal.HostTable;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore;
import com.bloxbean.intelliada.idea.joust.journal.ProviderChain;
import com.bloxbean.intelliada.idea.joust.journal.Settlement;
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
 * a timer. Nothing here admits or signs a body (that is M3.3b2's, behind the signing gate).
 */
public final class JoustRunJournal implements AutoCloseable {

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

    private JoustRunJournal(ChainJournal journal, ChainJournal.Opening opening, JournalStore.Held moduleLock, JoustRunSession.Binding b,
                            long settlementSlots) {
        this.journal = journal;
        this.opening = opening;
        this.moduleLock = moduleLock;
        this.settlementSlots = settlementSlots;
        this.slotMs = b.identity().slotLengthMs();
        this.table = new HostTable(settlementSlots, opening.restoring());
        Duration read = Duration.ofSeconds(10);
        this.settlement = new Settlement(journal, table, new ProviderChain(b.api().copy(), read), (method, params) -> {
            HostSession s = session.get();
            if (s == null) throw new IllegalStateException("the host is not started");
            return s.call(method, params, JoustRunSession.CALL_TIMEOUT, () -> false);
        }, lastNow::get, System::nanoTime, why -> {
            fenced.set(why);
            HostSession s = session.get();
            if (s != null) s.close();   // the host is stopped; its reservations stay journaled for a restored one
        });
    }

    /**
     * Before the host starts: takes the module's host lock on the chain and classifies the chain's history at a stable
     * tip read now (D17). The store is the IDE system directory's unless {@code store} is given (tests).
     */
    public static JoustRunJournal open(JoustRunSession.Binding b, Path moduleDir, @Nullable JournalStore store) throws Exception {
        JournalStore s = store != null ? store : JournalStore.inSystemDirectory();
        String chain = b.identity().chainKey();
        String module = JournalStore.key(moduleDir.toRealPath().toString());
        long settlement = b.identity().networkClass() == NetworkIdentity.NetworkClass.DEVNET ? 60 : 2160;
        JournalStore.Held lock = s.ownModule(JournalStore.key(chain), module);
        try {
            ChainJournal j = new ChainJournal(s, chain, module, settlement);
            long tip = stableTip(new ProviderChain(b.api().copy(), Duration.ofSeconds(10)));
            ChainJournal.Opening o = j.open(tip);
            return new JoustRunJournal(j, o, lock, b, settlement);
        } catch (Exception | Error e) {
            lock.close();
            throw e;
        }
    }

    private static long stableTip(ProviderChain c) throws Exception {
        for (int i = 0; i < 5; i++) {
            Settlement.ChainView.Tip a = c.tip(), b = c.tip();
            if (a.equals(b)) return a.slot();
        }
        throw new ChainJournal.Fenced("the chain kept moving while the session started; start again");
    }

    public boolean restoring() {
        return opening.restoring();
    }

    /** The reverse-read answerer for the host, through the settlement guard. */
    public HostSession.Reads reads(HostSession.Reads delegate) {
        return settlement.guard(delegate);
    }

    /**
     * After {@code initialize}: restores the host from the journal (D17 "Restart") and starts the settlement timer, which
     * takes {@code callLock} (the session's call lock, held by the caller now) for each pass.
     */
    void started(HostSession s, Object callLock) throws Exception {
        session.set(s);
        s.onEnded(why -> close());
        settlement.restore(opening);
        long every = tickMillis();
        timer.scheduleWithFixedDelay(() -> {
            synchronized (callLock) {
                try {
                    if (session.get() != null && session.get().ended() == null) tick();
                } catch (Exception e) {
                    LOG.info("JOUST settlement pass: " + e.getClass().getSimpleName());
                }
            }
        }, every, every, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private static final com.intellij.openapi.diagnostic.Logger LOG = com.intellij.openapi.diagnostic.Logger.getInstance(JoustRunJournal.class);
    private final java.util.concurrent.ScheduledExecutorService timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "JOUST settlement");
        t.setDaemon(true);
        return t;
    });

    /** Why building on this session is blocked, or null (views, listings and decoding work either way). */
    public @Nullable String buildingBlocked() {
        if (opening.permanent() != null) return opening.permanent();
        if (fenced.get() != null) return "the dev host was stopped: " + fenced.get();
        if (settlement.rewind() != null) return settlement.rewind();
        if (settlement.restoring()) {
            return opening.completeAbove() == null ? "the dev host is being restored"
                    : "IntelliAda's journal for this chain was incomplete; building waits until the chain passes slot " + opening.completeAbove();
        }
        if (table.deepRollback()) return "a final transaction was rolled back; restart the dev host";
        return null;
    }

    /** Before a forward call (call lock held): settlement first when anything is live or an inclusion is unreported. */
    void beforeCall(long now) throws Exception {
        lastNow.accumulateAndGet(now, Math::max);
        if (settlement.pollNeeded() || !journal.entries().isEmpty()) settlement.poll();
        settlement.beginCall();
    }

    /** After a forward call (call lock held). */
    void afterCall(String method, @Nullable JsonNode result, @Nullable String error) {
        settlement.endCall(method, result, error);
    }

    /** One timed settlement pass (call lock held by the caller); nothing to do without entries or an open restore. */
    void tick() throws Exception {
        if (settlement.restoring() || !journal.entries().isEmpty()) settlement.poll();
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
        moduleLock.close();
    }

    /** {@code initialize}'s parameters with this session's {@code restoring}. */
    public static ObjectNode initParams(NetworkIdentity.Identity id, boolean restoring) {
        ObjectNode n = JoustRunSession.initParams(id);
        n.put("restoring", restoring);
        return n;
    }
}
