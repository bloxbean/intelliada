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
    static final int OBSERVE_ATTEMPTS = 5;
    /** Restore batches stay well inside the protocol's 4 MiB frame. */
    static final int BATCH_BYTES = 1024 * 1024;

    /** The prefix of a final entry's journal id once its host is gone (hosts issue other ids). */
    static final String FINAL = "~";

    static final Set<String> BASE_KINDS = Set.of("utxos", "output", "tip", "protocolParams", "script", "stakeRegistration");
    /** Host error codes whose call's read tips are uncertain (D17, ADR5-CDX-23). */
    static final Set<String> READ_ERRORS = Set.of("bad/readLimit", "bad/nowImplausible", "internal/chainMoved",
            "internal/inconsistentRead", "internal/readFailed", "internal/cancelled", "internal/deadline", "internal/transport",
            "internal/need");

    private final ChainJournal journal;
    private final HostTable table;
    private final ChainView chain;
    private final Host host;
    private final long margin;
    private final LongSupplier lastNow;
    private final LongSupplier nanos;
    private final Consumer<String> onFence;

    private volatile @Nullable String fenced;
    private volatile @Nullable String rewind;
    private ChainView.Tip highest;
    private ChainView.Tip lowerFirst;
    private long lowerSince;
    private int lowerCount;
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
                      Consumer<String> onFence) {
        this.journal = journal;
        this.table = table;
        this.chain = chain;
        this.host = host;
        this.margin = journal.settlementSlots();
        this.lastNow = lastNow;
        this.nanos = nanos;
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
        if (rs != null && rs.isArray()) for (JsonNode x : rs) ids.add(x.isTextual() ? x.textValue() : null);
        if (rs == null || !table.restored(model, ids, complete)) {
            fence("its restore did not give one consistent reservation per transaction");
            throw new ChainJournal.Fenced("the dev host's restore was not consistent; the journal is kept");
        }
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) out.add(batch.get(i).withReservation(ids.get(i)));
        return out;
    }

    private void completed(long t) throws Exception {
        restoreOpen = false;
        journal.endRewind();
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

    /** Every listed body's inclusion, read between two equal chain points; null when the chain kept moving. */
    private @Nullable Observation observe(List<Entry> entries) throws Exception {
        for (int attempt = 0; attempt < OBSERVE_ATTEMPTS; attempt++) {
            ChainView.Tip a = chain.tip();
            Map<String, Inclusion> seen = new HashMap<>();
            for (Entry e : entries) {
                if (seen.containsKey(e.txHash())) continue;
                Inclusion in = chain.inclusion(e.txHash());
                seen.put(e.txHash(), in);
            }
            ChainView.Tip b = chain.tip();
            if (a.equals(b)) return new Observation(a, seen);
        }
        return null;
    }

    // ---- polling ------------------------------------------------------------------------------------------------------

    /**
     * One settlement pass (D17): before any forward call while entries are live or an inclusion is unreported, and at
     * least every {@code settlementSlots/2} slots. A fenced host or one with a rewind pending does nothing.
     */
    public synchronized void poll() throws Exception {
        if (fenced != null || rewind != null) return;
        List<Entry> entries = journal.entries();
        if (entries.isEmpty() && !restoreOpen) {
            needPoll = false;
            return;
        }
        Observation o = observe(entries);
        if (o == null) return;   // the chain kept moving: next pass
        String trigger = rewindTrigger(o, entries);
        if (trigger == TRANSIENT) return;
        if (trigger != null) {
            // Persisted before anything else (D17 "Rewind"): the hosts are restarted restored from the re-read chain.
            journal.beginRewind(o.tip.slot(), trigger);
            rewind = trigger;
            fence(trigger + "; the dev host is restarted and restored");
            return;
        }
        if (highest == null || o.tip.slot() >= highest.slot()) highest = o.tip;
        long t = o.tip.slot();
        // 1. Newly seen inclusions first, in slot order, each with its own slot as tip (ADR5-CDX-25).
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
            if (fenced != null) return;
        }
        // 2. A shallow inclusion the chain no longer shows: rolled back (its inputs stay reserved).
        for (Entry e : journal.entries()) {
            HostTable.Res r = table.lower(e.reservation());
            if (r != null && r.st() == HostTable.St.shallow && o.inclusions.containsKey(e.txHash()) && o.inclusions.get(e.txHash()) == null) {
                journal.update(e.reservation(), x -> x.withInclusion(null).withEvent("rolledBack@" + t));
                settle(e, "rolledBack", null, t);
                if (fenced != null) return;
            }
        }
        // 3. The thresholds this tip reaches (none while restore is open: the host applies none, and refuses expiry).
        for (Entry e : table.reconciling() ? List.<Entry>of() : journal.entries()) {
            HostTable.Res r = table.lower(e.reservation());
            if (r == null) continue;
            if (r.st() == HostTable.St.shallow && t - r.slot() > margin) settle(e, "confirmed", r.slot(), t);
            else if (r.st() == HostTable.St.prepared && o.inclusions.get(e.txHash()) == null && t - r.to() > margin) settle(e, "expired", null, t);
            if (fenced != null) return;
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
    }

    private static final String TRANSIENT = new String("transient");

    /**
     * D14: whether this observation is a rewind that touches the journal. A lower tip is first transient; persistent
     * after {@link #PERSIST_READS} consecutive readings over {@link #PERSIST_NANOS}. A fork at a recorded inclusion (the
     * body at another slot or block, or a final inclusion gone) triggers at once.
     */
    private @Nullable String rewindTrigger(Observation o, List<Entry> entries) {
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
        boolean lower = highest != null && (t.slot() < highest.slot() || t.height() < highest.height()
                || (t.slot() == highest.slot() && !t.hash().equals(highest.hash())));
        if (!lower) {
            lowerFirst = null;
            lowerCount = 0;
            return null;
        }
        if (lowerFirst == null) {
            lowerFirst = t;
            lowerSince = nanos.getAsLong();
            lowerCount = 1;
            return TRANSIENT;
        }
        lowerCount++;
        if (lowerCount < PERSIST_READS || nanos.getAsLong() - lowerSince < PERSIST_NANOS) return TRANSIENT;
        lowerFirst = null;
        lowerCount = 0;
        long tip = t.slot();
        for (Entry e : entries) {
            if (e.inclusion() != null && (e.inclusion().slot() > tip || tip - e.inclusion().slot() <= margin)) {
                return "the chain went back to slot " + tip + ", across a recorded inclusion";
            }
            if (e.state() == State.expired && e.validTo() > tip) return "the chain went back to slot " + tip + ", where an expired body can land again";
        }
        if (lastNow.getAsLong() - tip > ChainJournal.MAX_NOW_AHEAD) return "the chain went back to slot " + tip + ", far below the session's now";
        highest = t;   // a lower tip that touches nothing: the new baseline (I31: the journal and the mark are kept)
        return null;
    }

    /** Sends one {@code settled} event and checks the reply against the model (I32); a disagreement fences the host. */
    private void settle(Entry e, String status, @Nullable Long slot, long tip) throws Exception {
        HostTable.Expected x = table.expect(e.reservation(), e.txHash(), status, slot, tip);
        ObjectNode p = HostJson.object().put("reservation", e.reservation()).put("txHash", e.txHash()).put("status", status);
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
            fence("it answered " + got + " to " + status + " of " + e.txHash().substring(0, 16) + "…, where the chain shows "
                    + describe(x.lower()) + (x.certain() ? "" : " or " + describe(x.upper())));
            return;
        }
        String note = status + (slot == null ? "" : "@" + slot) + " tip " + tip;
        // The event's tip applied to every reservation of the host: the journal takes each changed state.
        for (Entry j : journal.entries()) {
            HostTable.Res r = table.lower(j.reservation());
            if (r == null) continue;
            State js = State.valueOf(r.st().name());
            boolean self = j.reservation().equals(e.reservation());
            if (self || js != j.state() || !java.util.Objects.equals(r.slot(), j.slot())) {
                journal.update(j.reservation(), y -> y.withState(js, r.slot()).withEvent((self ? note : "tip " + tip) + " -> " + js));
            }
        }
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
        boolean clean = error == null || !(READ_ERRORS.contains(error) || error.startsWith("unsupported/read") || error.equals("transport"));
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
                JsonNode from = p.get("validFrom");
                table.committed(p.path("reservation").textValue(), p.path("txHash").textValue(),
                        from == null || from.isNull() ? null : from.longValue(), p.path("validTo").longValue());
            }
        }
    }
}
