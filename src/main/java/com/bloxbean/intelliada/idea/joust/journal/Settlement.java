package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.host.HostError;
import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.host.HostSession;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Entry;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Handout;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Inclusion;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.State;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * ADR-005 D17's settlement for one host session (M3.3b1): IntelliAda observes the bound node, reports every newly seen
 * inclusion first (in slot order, each with its inclusion slot as tip), then rollbacks, then the thresholds the current
 * tip reaches; it checks every reply against {@link HostTable} (I32) and fences the host on a disagreement. It also
 * guards reverse reads inside a call (a tip that would expire a body which in fact landed answers {@code failed}),
 * records each call's read tips for the table, detects a lower tip or a fork that touches the journal (D14: a rewind,
 * reconciled on a fresh host), and restores a fresh host from the journal and the re-read chain (D17 "Restart").
 * <p>
 * Every method that talks to the host must be called with the session's call lock held (calls are serial).
 */
public final class Settlement {

    /** IntelliAda's own reads of the bound node (D18), at stable chain points. */
    public interface ChainView {
        record Tip(long slot, long height, String hash) {
        }

        Tip tip() throws Exception;

        /** Where the chain holds {@code txHash}, or null when it does not. */
        @Nullable Inclusion inclusion(String txHash) throws Exception;
    }

    /** A forward call to the host (protocol errors arrive as {@link HostError}). */
    public interface Host {
        JsonNode call(String method, ObjectNode params) throws Exception;
    }

    /** D14: a lower tip counts as persistent after this many consecutive readings over at least {@link #PERSIST_NANOS}. */
    static final int PERSIST_READS = 3;
    static final long PERSIST_NANOS = 10_000_000_000L;
    static final int OBSERVE_ATTEMPTS = 8;
    /** Between re-reads of a lower tip inside one pass (D14: three reads over at least 10 s). */
    static final long PERSIST_GAP_MS = 2_000;
    static final int PERSIST_MAX_READS = 12;
    /** Restore batches stay well inside the protocol's 4 MiB frame. */
    static final int BATCH_BYTES = 1024 * 1024;

    /** The prefix of a final entry's journal id once its host is gone (hosts issue other ids). */
    static final String FINAL = "~";

    static final Set<String> BASE_KINDS = Set.of("utxos", "output", "tip", "protocolParams", "script", "stakeRegistration");

    private final ChainJournal journal;
    private final HostTable table;
    private final ChainView chain;
    private final Host host;
    private final long margin;
    private final LongSupplier lastNow;
    private final LongSupplier nanos;
    private final Sleeper sleeper;
    private final Consumer<String> onFence;

    /** Waits between the re-reads of a lower tip (tests advance a fake clock instead). */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private volatile @Nullable String fenced;
    private volatile @Nullable String rewind;
    private volatile ChainView.Tip highest;
    /** Inclusions recorded in the journal that the host has not acknowledged yet. */
    private final Set<String> unreported = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean needPoll;
    private @Nullable Long completeAbove;
    private boolean restoreOpen;

    /** One answered read of the call in flight: its kind, outcome, chain point, and whether a continuation followed. */
    record ReadTip(String kind, String outcome, long tip, boolean more) {
    }

    private final List<ReadTip> callTips = new ArrayList<>();

    /**
     * @param lastNow the greatest {@code now} sent to this host (D14: a tip more than {@code maxNowAheadSlots} below it
     *                is a rewind)
     * @param onFence stops the host with the reason (its reservations stay journaled; a new host is restored)
     */
    public Settlement(ChainJournal journal, HostTable table, ChainView chain, Host host, LongSupplier lastNow, LongSupplier nanos,
                      Sleeper sleeper, Consumer<String> onFence) {
        this.journal = journal;
        this.table = table;
        this.chain = chain;
        this.host = host;
        this.margin = journal.settlementSlots();
        this.lastNow = lastNow;
        this.nanos = nanos;
        this.sleeper = sleeper;
        this.onFence = onFence;
    }

    /** Why this host builds nothing more (it is stopped), or null. */
    public @Nullable String fenced() {
        return fenced;
    }

    /** A rewind found on this chain: the outstanding reconciliation is persisted; hosts must restart restored. */
    public @Nullable String rewind() {
        return rewind;
    }

    /** The highest tip this session observed (0 before any). */
    public long highestSlot() {
        ChainView.Tip h = highest;
        return h == null ? 0 : h.slot();
    }

    /** Whether restore is still open on the host (building refused until it completes). */
    public synchronized boolean restoring() {
        return restoreOpen || table.reconciling();
    }

    /** Whether a poll must run before the next forward call (an inclusion seen, or a read answered {@code failed}). */
    public boolean pollNeeded() {
        return needPoll || !unreported.isEmpty();
    }

    private void fence(String why) {
        if (fenced != null) return;
        fenced = why;
        onFence.accept(why);
    }

    // ---- restore ------------------------------------------------------------------------------------------------------

    /**
     * Restores a fresh host ({@code initialize} sent with {@code restoring:true}) from the journal's survivors and the
     * chain as it is now (D17, D14): each handed-out body still on chain within its margin as {@code shallow} at its
     * slot (even one that was final); one not on chain while its {@code validTo} is above the tip as {@code prepared};
     * bodies awaiting approval or never handed out are removed; the rest are kept as final for their retention. The
     * last batch completes restore unless {@code completeAbove} holds it open until a tip strictly passes it.
     */
    public synchronized void restore(ChainJournal.Opening opening) throws Exception {
        JournalStore.ChainRecord atStart = journal.record();
        reconciling = atStart == null ? null : atStart.rewind();
        if (!opening.restoring()) return;
        completeAbove = opening.completeAbove();
        restoreOpen = true;
        Observation o = observe(opening.survivors());
        if (o == null) throw new ChainJournal.Fenced("the chain kept moving while the journal was read; start again");
        long t = o.tip.slot();
        highest = o.tip;
        List<Entry> kept = new ArrayList<>();
        List<Entry> send = new ArrayList<>();
        Set<String> hashes = new HashSet<>();
        for (Entry e : opening.survivors()) {
            if (e.handout() == Handout.none || e.handout() == Handout.approvalPending) continue;   // invalidated, never restored
            if (!hashes.add(e.txHash())) continue;
            Inclusion in = o.inclusions.get(e.txHash());
            boolean within = in != null && in.slot() >= (e.validFrom() == null ? 0 : e.validFrom()) && in.slot() < e.validTo() && in.slot() <= t;
            if (within && t - in.slot() <= margin) {
                send.add(e.withState(State.shallow, in.slot()).withInclusion(in).withEvent("restored shallow@" + in.slot()));
            } else if (within) {
                // Kept for its retention under an id no host issues, so a new host's ids never collide with it.
                kept.add(e.withReservation(FINAL + e.txHash()).withState(State.confirmed, in.slot()).withInclusion(in).withEvent("final@" + in.slot()));
            } else if (e.validTo() > t) {
                send.add(e.withState(State.prepared, null).withInclusion(null).withEvent("restored prepared"));
            } else {
                kept.add(e.withReservation(FINAL + e.txHash()).withState(State.expired, null).withInclusion(null).withEvent("expired@" + t));
            }
        }
        List<List<Entry>> batches = batches(send);
        boolean mayComplete = completeAbove == null || t > completeAbove;
        List<Entry> restored = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            boolean last = i == batches.size() - 1;
            restored.addAll(restoreBatch(batches.get(i), last && mayComplete));
        }
        if (batches.isEmpty() && mayComplete) restoreBatch(List.of(), true);
        List<Entry> all = new ArrayList<>(restored);
        all.addAll(kept);
        journal.replace(all);
        if (mayComplete) completed(t);
    }

    private List<Entry> restoreBatch(List<Entry> batch, boolean complete) throws Exception {
        ObjectNode p = HostJson.object();
        ArrayNode txs = p.putArray("transactions");
        List<HostTable.Restored> model = new ArrayList<>();
        for (Entry e : batch) {
            ObjectNode t = txs.addObject().put("cbor", e.cbor()).put("state", e.state().name());
            if (e.state() == State.shallow) t.put("slot", e.slot());
            model.add(new HostTable.Restored(e.txHash(), e.validFrom(), e.validTo(), HostTable.St.valueOf(e.state().name()), e.slot()));
        }
        p.put("complete", complete);
        JsonNode r = host.call("restore", p);
        List<String> ids = new ArrayList<>();
        JsonNode rs = r == null ? null : r.get("reservations");
        // The protocol's shape exactly (M33B1-CDX-19): one object holding an array of strings, one per transaction.
        boolean shaped = r != null && r.isObject() && r.size() == 1 && rs != null && rs.isArray() && rs.size() == batch.size();
        if (shaped) for (JsonNode x : rs) ids.add(x.isTextual() ? x.textValue() : null);
        if (!shaped || !table.restored(model, ids, complete)) {
            fence("its restore did not give one consistent reservation per transaction");
            throw new ChainJournal.Fenced("the dev host's restore was not consistent; the journal is kept");
        }
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) out.add(batch.get(i).withReservation(ids.get(i)));
        return out;
    }

    /** The rewind outstanding when this host was restored: it reconciled that one (and keeps running while others do). */
    private @Nullable JournalStore.Rewind reconciling;

    private static boolean same(@Nullable JournalStore.Rewind a, @Nullable JournalStore.Rewind b) {
        return a != null && b != null && a.tip() == b.tip() && a.reason().equals(b.reason());
    }

    private void completed(long t) throws Exception {
        restoreOpen = false;
        // This host leaves only the rewind it was restored after (M33B1-SEC-6).
        JournalStore.ChainRecord rec = journal.record();
        if (rec != null && same(rec.rewind(), reconciling)) journal.endRewind();
        journal.observed(t);
    }

    private static List<List<Entry>> batches(List<Entry> send) {
        List<List<Entry>> out = new ArrayList<>();
        List<Entry> cur = new ArrayList<>();
        long size = 0;
        for (Entry e : send) {
            long n = e.cbor().length() + 128L;
            if (!cur.isEmpty() && size + n > BATCH_BYTES) {
                out.add(cur);
                cur = new ArrayList<>();
                size = 0;
            }
            cur.add(e);
            size += n;
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }

    // ---- observation --------------------------------------------------------------------------------------------------

    record Observation(ChainView.Tip tip, Map<String, Inclusion> inclusions) {
    }

    /**
     * Every listed body's inclusion at one chain point (ADR-005 §12 E1, M33B1-DES-1): tip, inclusions, tip; equal tips
     * hold the answers; otherwise the inclusions are read again, and answers identical before and after the later tip
     * hold at that tip (a busy provider rarely answers two equal tips in a row). Null when no point held.
     */
    private @Nullable Observation observe(List<Entry> entries) throws Exception {
        ChainView.Tip a = chain.tip();
        Map<String, Inclusion> seen = inclusions(entries);
        for (int attempt = 0; attempt < OBSERVE_ATTEMPTS; attempt++) {
            ChainView.Tip b = chain.tip();
            if (a.equals(b)) return new Observation(b, seen);
            Map<String, Inclusion> again = inclusions(entries);
            if (again.equals(seen)) return new Observation(b, again);
            a = b;
            seen = again;
        }
        return null;
    }

    private Map<String, Inclusion> inclusions(List<Entry> entries) throws Exception {
        Map<String, Inclusion> seen = new HashMap<>();
        for (Entry e : entries) {
            if (!seen.containsKey(e.txHash())) seen.put(e.txHash(), chain.inclusion(e.txHash()));
        }
        return seen;
    }

    // ---- polling ------------------------------------------------------------------------------------------------------

    /**
     * One settlement pass (D17): before any forward call while entries are live or an inclusion is unreported, and at
     * least every {@code settlementSlots/2} slots. A fenced host or one with a rewind pending does nothing.
     */
    /**
     * @return whether the pass completed against a chain point; false when no point held or a lower tip is still being
     * resolved (the caller withholds the next forward call while anything is live; M33B1-CDX-16)
     */
    public synchronized boolean poll() throws Exception {
        if (fenced != null || rewind != null) return false;
        // A rewind another host of this chain found stops this one too, unless this host was restored after it (D14:
        // every live host on the chain; M33B1-DES-3, -CDX-5).
        JournalStore.ChainRecord rec = journal.record();
        // Checked while a restore is held open too (M33B1-SEC-6): a rewind persisted after this host's restore began was
        // never reconciled by it.
        if (rec != null && rec.rewind() != null && !same(rec.rewind(), reconciling)) {
            rewind = rec.rewind().reason();
            fence("a rewind on this chain is being reconciled (" + rec.rewind().reason() + ")");
            return false;
        }
        List<Entry> entries = journal.entries();
        if (entries.isEmpty() && !restoreOpen) {
            needPoll = false;
            return true;
        }
        Observation o = observe(entries);
        if (o == null) return false;   // no chain point held: the next pass reads again
        String trigger = rewindTrigger(o, entries);
        if (trigger == TRANSIENT) return false;   // the lower tip recovered while it was re-read: read again
        if (trigger != null) {
            // Persisted before anything else (D17 "Rewind"); every host on the chain stops, and a new one is restored
            // from the re-read chain when the user starts it again (M33B1-DES-4).
            journal.beginRewind(o.tip.slot(), trigger);
            rewind = trigger;
            fence(trigger + "; the dev host was stopped: start it again to restore it from IntelliAda's journal");
            return false;
        }
        if (highest == null || o.tip.slot() >= highest.slot()) highest = o.tip;
        long t = o.tip.slot();
        // 1. A shallow inclusion the chain no longer shows: rolled back (its inputs stay reserved). Reported at its own
        //    inclusion slot, a tip the host already holds, so the event finalizes no other body (M33B1-CDX-6).
        for (Entry e : journal.entries()) {
            HostTable.Res r = table.lower(e.reservation());
            if (r != null && r.st() == HostTable.St.shallow && o.inclusions.containsKey(e.txHash()) && o.inclusions.get(e.txHash()) == null) {
                journal.update(e.reservation(), x -> x.withInclusion(null).withEvent("rolledBack@" + t));
                settle(e, "rolledBack", null, r.slot());
                if (fenced != null) return false;
            }
        }
        // 2. Newly seen inclusions, in slot order, each with its own slot as tip (ADR5-CDX-25).
        List<Entry> landed = new ArrayList<>();
        for (Entry e : entries) {
            HostTable.Res r = table.lower(e.reservation());
            Inclusion in = o.inclusions.get(e.txHash());
            if (r != null && r.st() == HostTable.St.prepared && in != null && in.slot() >= r.from() && in.slot() < r.to() && in.slot() <= t) {
                landed.add(e.withInclusion(in));
            }
        }
        landed.sort(Comparator.comparingLong(e -> e.inclusion().slot()));
        for (Entry e : landed) {
            journal.update(e.reservation(), x -> x.withInclusion(e.inclusion()).withEvent("included@" + e.inclusion().slot()));
            unreported.add(e.reservation());
            settle(e, "confirmed", e.inclusion().slot(), e.inclusion().slot());
            unreported.remove(e.reservation());
            if (fenced != null) return false;
        }
        // 3. The thresholds this tip reaches (none while restore is open: the host applies none, and refuses expiry).
        for (Entry e : table.reconciling() ? List.<Entry>of() : journal.entries()) {
            HostTable.Res r = table.lower(e.reservation());
            if (r == null) continue;
            if (r.st() == HostTable.St.shallow && t - r.slot() > margin) settle(e, "confirmed", r.slot(), t);
            else if (r.st() == HostTable.St.prepared && o.inclusions.get(e.txHash()) == null && t - r.to() > margin) settle(e, "expired", null, t);
            if (fenced != null) return false;
        }
        // 4. Final entries past their retention leave the journal.
        Set<String> drop = new HashSet<>();
        for (Entry e : journal.entries()) if (journal.retentionOver(e, t)) drop.add(e.reservation());
        if (!drop.isEmpty()) journal.remove(drop);
        // 5. Fences that this tip passes, and a restore held open by one.
        if (restoreOpen && (completeAbove == null || t > completeAbove)) {
            restoreBatch(List.of(), true);
            completed(t);
        } else {
            journal.observed(t);
        }
        needPoll = false;
        return true;
    }

    private static final String TRANSIENT = new String("transient");

    /**
     * D14: whether this observation is a rewind that touches the journal. A lower tip is first transient; persistent
     * after {@link #PERSIST_READS} consecutive readings over {@link #PERSIST_NANOS}. A fork at a recorded inclusion (the
     * body at another slot or block, or a final inclusion gone) triggers at once.
     */
    private @Nullable String rewindTrigger(Observation o, List<Entry> entries) throws Exception {
        for (Entry e : entries) {
            Inclusion was = e.inclusion();
            if (was == null || !o.inclusions.containsKey(e.txHash())) continue;
            Inclusion now = o.inclusions.get(e.txHash());
            HostTable.Res r = table.lower(e.reservation());
            boolean finalNow = r != null && r.st() == HostTable.St.confirmed || e.state() == State.confirmed;
            if (now != null && !now.equals(was)) return "the chain shows " + e.label() + " in another block than recorded";
            if (now == null && finalNow) return "a final inclusion (" + e.label() + ") is no longer on the chain";
        }
        ChainView.Tip t = o.tip;
        if (!lower(t)) return null;
        // D14: first a transient read. Re-read inside this pass (forward calls wait) until three consecutive lower reads
        // span at least 10 s; a reading back at or above the highest tip resolves it as transient (M33B1-DES-3).
        long since = nanos.getAsLong();
        int reads = 1;
        while (reads < PERSIST_READS || nanos.getAsLong() - since < PERSIST_NANOS) {
            if (reads >= PERSIST_MAX_READS) return TRANSIENT;
            sleeper.sleep(PERSIST_GAP_MS);
            ChainView.Tip again = chain.tip();
            reads++;
            if (!lower(again)) return TRANSIENT;
            t = again;
        }
        long tip = t.slot();
        for (Entry e : entries) {
            if (e.inclusion() != null && (e.inclusion().slot() > tip || tip - e.inclusion().slot() <= margin)) {
                return "the chain went back to slot " + tip + ", across a recorded inclusion";
            }
            if (e.state() == State.expired && e.validTo() > tip) return "the chain went back to slot " + tip + ", where an expired body can land again";
        }
        if (lastNow.getAsLong() - tip > TimePolicy.MAX_NOW_AHEAD) return "the chain went back to slot " + tip + ", far below the session's now";
        highest = t;   // a lower tip that touches nothing: the new baseline (I31: the journal and the mark are kept)
        return null;
    }

    private boolean lower(ChainView.Tip t) {
        return highest != null && (t.slot() < highest.slot() || t.height() < highest.height()
                || (t.slot() == highest.slot() && !t.hash().equals(highest.hash())));
    }

    /** Sends one {@code settled} event and checks the reply against the model (I32); a disagreement fences the host. */
    private boolean settle(Entry e, String status, @Nullable Long slot, long tip) throws Exception {
        return settle(e.reservation(), e.txHash(), status, slot, tip);
    }

    private boolean settle(String reservation, String txHash, String status, @Nullable Long slot, long tip) throws Exception {
        HostTable.Expected x = table.expect(reservation, txHash, status, slot, tip);
        ObjectNode p = HostJson.object().put("reservation", reservation).put("txHash", txHash).put("status", status);
        if (slot != null) p.put("slot", slot);
        p.put("tip", tip);
        boolean ok;
        String got;
        try {
            JsonNode r = host.call("settled", p);
            String s = r == null ? null : r.path("state").textValue();
            HostTable.St st;
            try {
                st = s == null ? null : HostTable.St.valueOf(s);
            } catch (IllegalArgumentException bad) {
                st = null;
            }
            got = String.valueOf(s);
            ok = st != null && r.size() == 1 && table.reply(x, st, null);
        } catch (HostError he) {
            got = he.kind() + "/" + he.code();
            ok = table.reply(x, null, got);
        }
        if (!ok) {
            fence("it answered " + got + " to " + status + " of " + txHash.substring(0, 16) + "…, where the chain shows "
                    + describe(x.lower()) + (x.certain() ? "" : " or " + describe(x.upper())));
            return false;
        }
        if (deferJournal) {
            syncPending = true;   // the model holds the states; the journal takes them at the next successful write
            return true;
        }
        String note = status + (slot == null ? "" : "@" + slot) + " tip " + tip;
        // The event's tip applied to every reservation of the host: the journal takes each changed state.
        for (Entry j : journal.entries()) {
            HostTable.Res r = table.lower(j.reservation());
            if (r == null) continue;
            State js = State.valueOf(r.st().name());
            boolean self = j.reservation().equals(reservation);
            if (self || js != j.state() || !java.util.Objects.equals(r.slot(), j.slot())) {
                journal.update(j.reservation(), y -> y.withState(js, r.slot()).withEvent((self ? note : "tip " + tip) + " -> " + js));
            }
        }
        return true;
    }

    /**
     * D17: a body that was never handed out (declined before handout, an approval invalidated, or one whose admission
     * failed) settles {@code discarded}, checked against the model; its journal entry, if any, is removed. A handed-out
     * body ({@code submitted} or {@code exported}) is refused: only chain evidence or expiry releases it (I30). The tip
     * sent is one IntelliAda observed, so applying it to the host's other reservations is evidence, never a guess.
     */
    public synchronized void discard(String reservation) throws Exception {
        if (fenced != null) throw new ChainJournal.Fenced("the dev host was stopped: " + fenced);
        HostTable.Res lo = table.lower(reservation), up = table.upper(reservation);
        if (lo == null) throw new ChainJournal.Fenced("the dev host holds no such reservation");
        Entry e = null;
        for (Entry x : journal.entries()) if (x.reservation().equals(reservation)) e = x;
        if (e != null && e.handout() != Handout.none && e.handout() != Handout.approvalPending) {
            throw new ChainJournal.Fenced("a body that was handed out is released only by chain evidence or expiry (I30)");
        }
        if (lo.st() != HostTable.St.prepared || up == null || up.st() != HostTable.St.prepared) {
            throw new ChainJournal.Fenced("only a prepared body can be discarded");
        }
        long tip = highest != null ? highest.slot() : chain.tip().slot();
        if (!settle(reservation, lo.txHash(), "discarded", null, tip)) {
            throw new ChainJournal.Fenced("the dev host did not discard the body as expected; it was stopped");
        }
        if (e != null && !deferJournal) journal.remove(Set.of(reservation));
    }

    private static String describe(HostTable.Outcome o) {
        return o.state() != null ? o.state().name() : o.error();
    }

    // ---- inside a call ------------------------------------------------------------------------------------------------

    /** Starts a forward call's bookkeeping. */
    public synchronized void beginCall() {
        callTips.clear();
    }

    /**
     * Wraps the session's reverse-read answerer: records each answer's chain point, and answers {@code failed} when its
     * tip would let the host expire a reservation whose body in fact landed (or whose inclusion is recorded but not
     * reported yet) (ADR5-CDX-25, the playground's read handler).
     */
    public HostSession.Reads guard(HostSession.Reads delegate) {
        return (id, params) -> {
            ObjectNode a = delegate.read(id, params);
            String kind = params.path("kind").textValue();
            JsonNode tip = a.path("tip").path("slot");
            if (kind == null || !tip.isIntegralNumber()) return a;
            long t = tip.longValue();
            String outcome = a.has("value") ? "value" : a.has("absent") ? "absent" : a.has("unsupported") ? "unsupported" : "failed";
            if (advancing(kind, outcome)) {
                String landed = landedUnreported(t);
                if (landed != null) {
                    needPoll = true;
                    ObjectNode f = HostJson.object();
                    f.set("tip", a.get("tip"));
                    f.putObject("failed").put("message", "a transaction landed and its settlement is not reported yet (" + landed
                            + "); try again in a moment");
                    synchronized (this) {
                        callTips.add(new ReadTip(kind, "failed", t, false));
                    }
                    return f;
                }
            }
            synchronized (this) {
                callTips.add(new ReadTip(kind, outcome, t, a.has("more")));
            }
            return a;
        };
    }

    /** Whether the host applies this answer's tip to its table once its kind's checks pass (JOUST {@code Reads}). */
    static boolean advancing(String kind, String outcome) {
        if (!BASE_KINDS.contains(kind)) return false;
        return outcome.equals("value") || outcome.equals("absent") || (outcome.equals("unsupported") && kind.equals("stakeRegistration"));
    }

    private @Nullable String landedUnreported(long t) throws Exception {
        for (HostTable.Res r : table.lowerAll()) {
            boolean told = !unreported.contains(r.id());
            if (!told && t - r.to() > margin) return r.txHash().substring(0, 16) + "…";
            if (r.st() != HostTable.St.prepared || t - r.to() <= margin) continue;
            Inclusion in = chain.inclusion(r.txHash());
            if (in != null && in.slot() >= r.from() && in.slot() < r.to() && in.slot() <= t) return r.txHash().substring(0, 16) + "…";
        }
        return null;
    }

    /**
     * Ends a forward call's bookkeeping: its read tips go to the table, certain when the call completed without a
     * read-related error (D17, ADR5-SEC-21), else to the upper copy only; a partial listing is uncertain; then a
     * successful {@code prepare} or {@code publish} commits its body.
     *
     * @param error the host's {@code kind/code}, or {@code "transport"} for a session that ended or a call abandoned
     */
    public synchronized void endCall(String method, @Nullable JsonNode result, @Nullable String error) {
        // Only a successful call's read tips are certain (M33B1-CDX-8): an error can come from a kind's own checks after its
        // answer (internal/evaluator for parameters without a Plutus V3 cost model), before the host accepts the tip.
        boolean clean = error == null;
        for (int i = 0; i < callTips.size(); i++) {
            ReadTip r = callTips.get(i);
            if (!advancing(r.kind(), r.outcome())) continue;
            if (r.more()) {
                // A nonfinal chunk counts once, with its final chunk; a listing that never finished is uncertain.
                boolean finished = false;
                for (int j = i + 1; j < callTips.size(); j++) {
                    ReadTip n = callTips.get(j);
                    if (n.kind().equals(r.kind()) && n.tip() == r.tip() && !n.more() && advancing(n.kind(), n.outcome())) finished = true;
                }
                if (!finished) table.uncertain(r.tip());
                continue;
            }
            if (clean) table.accepted(r.tip());
            else table.uncertain(r.tip());
        }
        callTips.clear();
        if (error == null && result != null && (method.equals("prepare") || method.equals("publish"))) {
            JsonNode p = result.path("prepared");
            if (p.isObject()) {
                // The model's facts come from the body itself; the host's stated ones must agree (M33B1-SEC-4).
                String why = null;
                com.bloxbean.intelliada.idea.joust.tx.TxCbor.Body b = null;
                try {
                    b = com.bloxbean.intelliada.idea.joust.tx.TxCbor.body(com.bloxbean.intelliada.idea.joust.tx.TxCbor.transaction(
                            p.path("cbor").asText("").toLowerCase(java.util.Locale.ROOT)));
                } catch (com.bloxbean.intelliada.idea.joust.tx.TxCbor.Malformed m) {
                    why = "its prepared transaction cannot be read (" + m.getMessage() + ")";
                }
                String id = p.path("reservation").textValue();
                if (why == null && (id == null || !JournalStore.RESERVATION.matcher(id).matches())) why = "it gave a malformed reservation id";
                if (why == null) {
                    JsonNode from = p.get("validFrom");
                    Long stated = from == null || from.isNull() ? null : from.longValue();
                    if (!b.txHash().equals(p.path("txHash").textValue()) || b.validTo() == null || b.validTo() != p.path("validTo").longValue()
                            || !java.util.Objects.equals(b.validFrom(), stated)) {
                        why = "its prepared transaction's hash or validity differs from what it stated";
                    }
                }
                if (why != null) fence(why);
                else table.committed(id, b.txHash(), b.validFrom(), b.validTo());
            }
        }
    }

    private boolean syncPending;

    /**
     * After a forward call (M33B1-DES-11): its tips to the model ({@link #endCall}), then the changed states to the journal.
     * If that write fails after a successful {@code prepare} or {@code publish}, the body (never handed out) is discarded,
     * or the host fenced when even that fails; either way the caller learns why. After any other call the result stands
     * and the write is retried before the next call ({@link #beforeCall}).
     */
    public synchronized void complete(String method, @Nullable JsonNode result, @Nullable String error) throws Exception {
        endCall(method, result, error);
        try {
            sync();
            syncPending = false;
        } catch (Exception journalFailed) {
            syncPending = true;
            String id = error == null && result != null && (method.equals("prepare") || method.equals("publish"))
                    ? result.path("prepared").path("reservation").textValue() : null;
            if (id == null || table.lower(id) == null) return;
            String cause = journalFailed.getMessage();
            try {
                // The host's discard is checked against the model; the journal (failing now) takes the states later.
                deferJournal = true;
                discard(id);
            } catch (Exception notDiscarded) {
                fence("IntelliAda's journal could not be written after it prepared a body, which could not be discarded");
                throw new ChainJournal.Fenced("IntelliAda's journal could not be written (" + cause + "), and the prepared body could not be "
                        + "discarded; the dev host was stopped");
            }
            finally {
                deferJournal = false;
            }
            throw new ChainJournal.Fenced("IntelliAda's journal could not be written (" + cause + "); the prepared body was discarded");
        }
    }

    private boolean deferJournal;

    /** Before a forward call: a journal write that failed after the last call is retried; failing again refuses the call. */
    public synchronized void beforeCall() throws Exception {
        if (syncPending) {
            sync();
            syncPending = false;
        }
        beginCall();
    }

    /** Writes the table's changed states to the journal (M33B1-CDX-9): accepted read tips advance reservations too. */
    public synchronized void sync() throws Exception {
        for (Entry j : journal.entries()) {
            HostTable.Res r = table.lower(j.reservation());
            if (r == null) continue;
            State js = State.valueOf(r.st().name());
            if (js != j.state() || !java.util.Objects.equals(r.slot(), j.slot())) {
                journal.update(j.reservation(), y -> y.withState(js, r.slot()).withEvent("read tip -> " + js));
            }
        }
    }
}
