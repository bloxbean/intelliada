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
 * ADR-005 D17's settlement for one host session (M3.3b1, simplified by §12 E12 and L44): IntelliAda observes the bound
 * node, reports every newly seen inclusion first (in slot order, each with its inclusion slot as tip), then rollbacks,
 * then the thresholds the current tip reaches; it checks every reply against {@link HostTable} (I32) and stops the host
 * on a disagreement. It also guards reverse reads inside a call (a tip that would expire a body which in fact landed
 * answers {@code failed}), records each call's read tips for the table, and restores a fresh host from the journal and
 * the chain as it is now (D17 "Restart").
 * <p>
 * A rollback the host's reservations cannot follow (a final inclusion rolled back, a body in another block, the chain
 * far behind) stops the host only while it holds live reservations; a restart restores them from the chain. Nothing
 * here blocks another module, or the chain.
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

    /** D14 on public networks: a lower tip counts as persistent after this many readings over at least this long. */
    static final int PERSIST_READS = 3;
    static final long PERSIST_NANOS = 10_000_000_000L;
    static final int OBSERVE_ATTEMPTS = 8;
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
    private volatile ChainView.Tip highest;
    /** Inclusions recorded in the journal that the host has not acknowledged yet. */
    private final Set<String> unreported = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean needPoll;
    private boolean restoreOpen;

    /** One answered read of the call in flight: its kind, outcome, chain point, and whether a continuation followed. */
    record ReadTip(String kind, String outcome, long tip, boolean more, long call) {
        ReadTip(String kind, String outcome, long tip, boolean more) {
            this(kind, outcome, tip, more, -1);
        }
    }

    private final List<ReadTip> callTips = new ArrayList<>();

    /**
     * @param lastNow the greatest {@code now} sent to this host (D14: a tip more than {@code maxNowAheadSlots} below it
     *                is a chain the host cannot follow)
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

    /** Whether the host holds a reservation that can still move (prepared, or included but not final). */
    private boolean holdsLive() {
        return table.lowerAll().stream().anyMatch(r -> r.st() == HostTable.St.prepared || r.st() == HostTable.St.shallow);
    }

    /** Checks the bound chain's identity (D14's block 1) around observations before they change the journal. */
    @FunctionalInterface
    public interface ChainCheck {
        void run() throws Exception;
    }

    private ChainCheck chainCheck = () -> { };

    /** The session's identity check, run after each observation and before anything is settled (M33B1-CDX-12). */
    public void chainCheck(ChainCheck c) {
        this.chainCheck = c;
    }

    // ---- restore ------------------------------------------------------------------------------------------------------

    /**
     * Restores a fresh host ({@code initialize} sent with {@code restoring:true}) from the journal's survivors and the
     * chain as it is now (D17, D14): each handed-out body still on chain within its margin as {@code shallow} at its slot;
     * one not on chain while its {@code validTo} is above the tip as {@code prepared}; bodies awaiting approval or never
     * handed out are removed; the rest are kept as history for their retention. The last batch completes restore.
     */
    public synchronized void restore(ChainJournal.Opening opening) throws Exception {
        if (!opening.restoring()) return;
        try (var m = journal.enter()) {
            restoreOpen = true;
            Observation o = observe(opening.survivors());
            if (o == null) throw new ChainJournal.Fenced("the chain kept moving while the journal was read; start again");
            chainCheck.run();   // the observation is the bound chain's, before it decides anything (M33B1-CDX-12)
            long t = o.tip.slot();
            highest = o.tip;
            List<Entry> kept = new ArrayList<>();
            List<Entry> send = new ArrayList<>();
            Set<String> hashes = new HashSet<>();
            for (Entry e : opening.survivors()) {
                if (e.handout() == Handout.none || e.handout() == Handout.approvalPending) continue;   // never handed out: dropped
                if (!hashes.add(e.txHash())) continue;
                Inclusion in = o.inclusions.get(e.txHash());
                switch (restoreClass(e, in, t)) {
                    case shallow -> send.add(e.withState(State.shallow, in.slot()).withInclusion(in).withEvent("restored shallow@" + in.slot()));
                    // Kept as history under an id no host issues, so a new host's ids never collide with it.
                    case confirmed -> kept.add(e.withReservation(FINAL + e.txHash()).withState(State.confirmed, in.slot()).withInclusion(in).withEvent("final@" + in.slot()));
                    case prepared -> send.add(e.withState(State.prepared, null).withInclusion(null).withEvent("restored prepared"));
                    default -> kept.add(e.withReservation(FINAL + e.txHash()).withState(State.expired, null).withInclusion(null).withEvent("expired@" + t));
                }
            }
            List<List<Entry>> batches = batches(send);
            List<Entry> restored = new ArrayList<>();
            for (int i = 0; i < batches.size(); i++) restored.addAll(restoreBatch(batches.get(i), i == batches.size() - 1));
            if (batches.isEmpty()) restoreBatch(List.of(), true);
            List<Entry> all = new ArrayList<>(restored);
            all.addAll(kept);
            if (fenced != null) throw new ChainJournal.Fenced("the session ended during its restore");
            journal.replace(all);
            restoreOpen = false;
        }
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
        for (int i = 0; i < batch.size(); i++) {
            Entry e = batch.get(i).withReservation(ids.get(i));
            if (e.inclusion() != null) recorded.put(e.reservation(), e.inclusion());
            out.add(e);
        }
        return out;
    }

    /** How a restore classifies a journaled body at chain point {@code t} with inclusion {@code in} (D17 "Restart"). */
    private State restoreClass(Entry e, @Nullable Inclusion in, long t) {
        boolean within = in != null && in.slot() >= (e.validFrom() == null ? 0 : e.validFrom()) && in.slot() < e.validTo() && in.slot() <= t;
        if (within && t - in.slot() <= margin) return State.shallow;
        if (within) return State.confirmed;
        return e.validTo() > t ? State.prepared : State.expired;
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
     * hold at that tip. Null when no point held.
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
     * least every {@code settlementSlots/2} slots.
     *
     * @return whether the pass completed against a chain point; false when no point held or a lower tip is still being
     * resolved (the caller withholds the next forward call while anything is live; M33B1-CDX-16)
     */
    public synchronized boolean poll() throws Exception {
        if (fenced != null) return false;
        List<Entry> entries = journal.entries();
        if (entries.isEmpty() && !restoreOpen) {
            needPoll = false;
            return true;
        }
        try (var m = journal.enter()) {
            return pass(journal.entries());
        }
    }

    private boolean pass(List<Entry> entries) throws Exception {
        Observation o = observe(entries);
        if (o == null) return false;   // no chain point held: the next pass reads again
        chainCheck.run();
        String stop = followable(o, entries);
        if (stop == TRANSIENT) return false;   // a lower tip still being re-read
        if (stop != null) {
            fence(stop + "; the dev host was stopped: start it again to restore its transactions from the chain");
            return false;
        }
        if (highest == null || o.tip.slot() >= highest.slot()) highest = o.tip;
        long t = o.tip.slot();
        // 1. A shallow inclusion the chain no longer shows: rolled back (the body is prepared again). Reported at a tip
        //    that crosses no other reservation's threshold (M33B1-CDX-6, -38), in ascending slot order.
        List<Entry> vanished = new ArrayList<>();
        for (Entry e : journal.entries()) {
            HostTable.Res r = table.lower(e.reservation());
            if (r != null && r.st() == HostTable.St.shallow && o.inclusions.containsKey(e.txHash()) && o.inclusions.get(e.txHash()) == null) vanished.add(e);
        }
        vanished.sort(Comparator.comparingLong(e -> table.lower(e.reservation()).slot()));
        long safe = vanished.isEmpty() ? 0 : safeRollbackTip(table.lowerAll(), table.lower(vanished.get(0).reservation()).slot(), margin);
        for (Entry e : vanished) {
            settle(e, "rolledBack", null, safe);
            if (fenced != null) return false;
            HostTable.Res after = table.lower(e.reservation());
            if (after != null && after.st() == HostTable.St.prepared) {
                journal.update(e.reservation(), x -> x.withInclusion(null).withEvent("rolledBack@" + t));
                recorded.remove(e.reservation());
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
        // A body the host holds expired that the chain shows included within its margin: the host's table diverged
        // (M33B1-CDX-64). The journal keeps the chain's evidence, and the host is stopped so a restart restores it.
        for (Entry e : entries) {
            HostTable.Res r = table.lower(e.reservation());
            Inclusion in = o.inclusions.get(e.txHash());
            long from = e.validFrom() == null ? 0 : e.validFrom();
            if (r != null && r.st() == HostTable.St.expired && in != null && in.slot() >= from && in.slot() < e.validTo() && in.slot() <= t
                    && t - in.slot() <= margin) {
                journal.update(e.reservation(), x -> x.withState(State.shallow, in.slot()).withInclusion(in)
                        .withEvent("the chain shows it at " + in.slot() + " though the host expired it"));
                fence("it holds " + e.label() + " expired, which the chain shows included");
                return false;
            }
        }
        for (Entry e : landed) {
            journal.update(e.reservation(), x -> x.withInclusion(e.inclusion()).withEvent("included@" + e.inclusion().slot()));
            recorded.put(e.reservation(), e.inclusion());
            unreported.add(e.reservation());
            checkReply(e.reservation(), e.txHash(), "confirmed", e.inclusion().slot(), e.inclusion().slot());
            unreported.remove(e.reservation());
            if (fenced != null) return false;
            HostTable.Res after = table.lower(e.reservation());
            if (after != null && after.st() != HostTable.St.shallow && after.st() != HostTable.St.confirmed) {
                long slot = e.inclusion().slot();
                journal.update(e.reservation(), x -> x.withState(State.shallow, slot).withInclusion(e.inclusion())
                        .withEvent("the host expired it; the chain shows it at " + slot));
                fence("it expired " + e.label() + ", which the chain shows included");
                return false;
            }
            publish(e.reservation(), "confirmed@" + e.inclusion().slot() + " tip " + e.inclusion().slot());
        }
        // 3. The thresholds this tip reaches (none while restore is open: the host applies none, and refuses expiry).
        for (Entry e : table.reconciling() ? List.<Entry>of() : journal.entries()) {
            HostTable.Res r = table.lower(e.reservation());
            if (r == null) continue;
            if (r.st() == HostTable.St.shallow && t - r.slot() > margin) settle(e, "confirmed", r.slot(), t);
            else if (r.st() == HostTable.St.prepared && o.inclusions.get(e.txHash()) == null && t - r.to() > margin) settle(e, "expired", null, t);
            if (fenced != null) return false;
        }
        // 4. Final entries past their retention leave the journal (history only; they hold no inputs).
        Set<String> drop = new HashSet<>();
        for (Entry e : journal.entries()) if (journal.retentionOver(e, t)) drop.add(e.reservation());
        if (!drop.isEmpty()) journal.remove(drop);
        needPoll = false;
        return true;
    }

    private static final String TRANSIENT = new String("transient");

    /**
     * Whether the host can follow this observation, or why not. A body the chain shows in another block than recorded,
     * a final inclusion gone, or a lower tip that crosses the host's reservations, is a rewind the host cannot follow: it
     * is stopped, but only while it holds live reservations (a restart restores them from the chain); otherwise the
     * lower tip is the new baseline. On a devnet a lower tip is acted on at once (a single node has no lagging replica);
     * on a public network it must persist for {@link #PERSIST_READS} readings over {@link #PERSIST_NANOS} (D14).
     */
    private @Nullable String followable(Observation o, List<Entry> entries) throws Exception {
        for (Entry e : entries) {
            Inclusion was = e.inclusion();
            if (was == null || !o.inclusions.containsKey(e.txHash()) || table.lower(e.reservation()) == null) continue;
            Inclusion now = o.inclusions.get(e.txHash());
            HostTable.Res r = table.lower(e.reservation());
            if (now != null && !now.equals(was) && holdsLive()) return "the chain shows " + e.label() + " in another block than recorded";
            if (now == null && r.st() == HostTable.St.confirmed && holdsLive()) return "a final inclusion (" + e.label() + ") is no longer on the chain";
        }
        ChainView.Tip t = o.tip;
        if (!lower(t)) return null;
        if (!journal.devnet()) {
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
        }
        long tip = t.slot();
        if (lastNow.getAsLong() - tip > TimePolicy.MAX_NOW_AHEAD) return "the chain went back to slot " + tip + ", far below the session's now";
        boolean crosses = table.lowerAll().stream().anyMatch(r -> (r.slot() != null && r.slot() > tip)
                || (r.st() == HostTable.St.confirmed && r.slot() != null && tip - r.slot() <= margin)
                || (r.st() == HostTable.St.expired && r.to() > tip));
        if (crosses && holdsLive()) return "the chain went back to slot " + tip + ", across this host's transactions";
        highest = t;   // the new baseline
        acceptedHigh = Math.min(acceptedHigh, t.slot());
        return null;
    }

    /**
     * A rollback tip that crosses no reservation's threshold (M33B1-CDX-38): at most the lowest vanished slot, a prepared
     * body's {@code validTo + margin}, and a shallow body's {@code slot + margin}.
     */
    static long safeRollbackTip(List<HostTable.Res> all, long lowestVanished, long margin) {
        long safe = lowestVanished;
        for (HostTable.Res r : all) {
            if (r.st() == HostTable.St.prepared) safe = Math.min(safe, r.to() + margin);
            else if (r.st() == HostTable.St.shallow) safe = Math.min(safe, r.slot() + margin);
        }
        return Math.max(0, safe);
    }

    private volatile long acceptedHigh = -1;

    private boolean lower(ChainView.Tip t) {
        if (t.slot() < acceptedHigh) return true;
        return highest != null && (t.slot() < highest.slot() || t.height() < highest.height()
                || (t.slot() == highest.slot() && !t.hash().equals(highest.hash())));
    }

    /** Sends one {@code settled} event and checks the reply against the model (I32); a disagreement stops the host. */
    private boolean settle(Entry e, String status, @Nullable Long slot, long tip) throws Exception {
        return settle(e.reservation(), e.txHash(), status, slot, tip);
    }

    private boolean settle(String reservation, String txHash, String status, @Nullable Long slot, long tip) throws Exception {
        if (!checkReply(reservation, txHash, status, slot, tip)) return false;
        if (!deferJournal && fenced == null) publish(reservation, status + (slot == null ? "" : "@" + slot) + " tip " + tip);
        return true;
    }

    /** The journal takes the host's states after an event (its tip applied to every reservation). */
    private void publish(String reservation, String note) throws Exception {
        if (closed) return;   // the session ended: nothing more is written (M33B1-CDX-75)
        for (Entry j : journal.entries()) {
            HostTable.Res r = table.lower(j.reservation());
            if (r == null) continue;
            State js = State.valueOf(r.st().name());
            boolean self = j.reservation().equals(reservation);
            if (self || js != j.state() || !java.util.Objects.equals(r.slot(), j.slot())) {
                journal.update(j.reservation(), y -> y.withState(js, r.slot()).withEvent((self ? note : "event tip") + " -> " + js));
            }
        }
    }

    /** Sends one {@code settled} event and checks the reply against the model (I32); the caller publishes (M33B1-DES-17). */
    private boolean checkReply(String reservation, String txHash, String status, @Nullable Long slot, long tip) throws Exception {
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
        if (status.equals("rolledBack") && table.deepRollback()) {
            // A final inclusion rolled back: the host cannot follow it; a restart restores its transactions from the chain.
            fence("a final inclusion was rolled back; the dev host was stopped: start it again to restore its transactions from the chain");
        }
        if (deferJournal) syncPending = true;   // the model holds the states; the journal takes them at the next successful write
        return true;
    }

    /**
     * D17: a body that was never handed out (declined before handout, an approval invalidated, or one whose admission
     * failed) settles {@code discarded}, checked against the model; its journal entry, if any, is removed. A handed-out
     * body ({@code submitted} or {@code exported}) is refused: only chain evidence or expiry releases it (I30).
     */
    public synchronized void discard(String reservation) throws Exception {
        if (fenced != null) throw new ChainJournal.Fenced("the dev host was stopped: " + fenced);
        HostTable.Res lo = table.lower(reservation), up = table.upper(reservation);
        if (lo == null) throw new ChainJournal.Fenced("the dev host holds no such reservation");
        Entry e = entry(reservation);
        if (e != null && e.handout() != Handout.none && e.handout() != Handout.approvalPending) {
            throw new ChainJournal.Fenced("a body that was handed out is released only by chain evidence or expiry (I30)");
        }
        if (lo.st() != HostTable.St.prepared || up == null || up.st() != HostTable.St.prepared) {
            throw new ChainJournal.Fenced("only a prepared body can be discarded");
        }
        // Only a body this host built in this session (a restored one was handed out; M33B1-CDX-27), and after a pass,
        // so the event's tip expires no body that landed (M33B1-CDX-28).
        if (!builtHere.contains(reservation)) throw new ChainJournal.Fenced("only a body prepared in this session is discarded (I30)");
        if (!deferJournal && !poll()) throw new ChainJournal.Fenced("the chain kept moving; the body was not discarded. Try again");
        e = entry(reservation);
        if (e != null && e.handout() != Handout.none && e.handout() != Handout.approvalPending) {
            throw new ChainJournal.Fenced("a body that was handed out is released only by chain evidence or expiry (I30)");
        }
        lo = table.lower(reservation);
        up = table.upper(reservation);
        if (lo == null || up == null || lo.st() != HostTable.St.prepared || up.st() != HostTable.St.prepared) {
            throw new ChainJournal.Fenced("the body is no longer only prepared (it may have landed); it was not discarded");
        }
        long tip = highest != null ? highest.slot() : chain.tip().slot();
        if (!settle(reservation, lo.txHash(), "discarded", null, tip) || table.lower(reservation).st() != HostTable.St.discarded) {
            fence("it did not discard the body as expected");
            throw new ChainJournal.Fenced("the dev host did not discard the body as expected; it was stopped");
        }
        builtHere.remove(reservation);
        if (e != null && !deferJournal) journal.remove(Set.of(reservation));
    }

    private @Nullable Entry entry(String reservation) throws Exception {
        for (Entry x : journal.entries()) if (x.reservation().equals(reservation)) return x;
        return null;
    }

    /**
     * Journals a body being handed out (I25), serialized with discard on this monitor (a body is never discarded while
     * it is being handed out). A body admitted as submitted or exported is no longer discardable.
     */
    @org.jetbrains.annotations.ApiStatus.Internal   // unreachable from production in M3.3b1; reviewed in M3.3b2 (ADR-005 L43, #38)
    public synchronized void admit(Entry e, long tip, long now) throws Exception {
        if (fenced != null) throw new ChainJournal.Fenced("the dev host was stopped: " + fenced);
        // Only a body this host prepared in this session and still holds prepared, with the same bytes (M33B1-CDX-40).
        HostTable.Res lo = table.lower(e.reservation()), up = table.upper(e.reservation());
        if (!builtHere.contains(e.reservation()) || lo == null || up == null || lo.st() != HostTable.St.prepared
                || up.st() != HostTable.St.prepared || !lo.txHash().equals(e.txHash())) {
            throw new ChainJournal.Fenced("only a body this dev host prepared in this session, and still holds prepared, is handed out");
        }
        journal.admit(e, tip, now);
        if (e.handout() != Handout.approvalPending) builtHere.remove(e.reservation());
    }

    /** Records how a journaled body was handed out. Handouts only move forward: approval pending to submitted or exported. */
    @org.jetbrains.annotations.ApiStatus.Internal   // unreachable from production in M3.3b1; reviewed in M3.3b2 (ADR-005 L43, #38)
    public synchronized void handedOut(String reservation, Handout h) throws Exception {
        if (h == Handout.none || h == Handout.approvalPending) throw new IllegalArgumentException("a handout only moves to submitted or exported");
        if (fenced != null) throw new ChainJournal.Fenced("the dev host was stopped");
        if (!builtHere.contains(reservation)) throw new ChainJournal.Fenced("only a body this dev host prepared in this session is handed out");
        HostTable.Res lo = table.lower(reservation), up = table.upper(reservation);
        if (lo == null || up == null || lo.st() != HostTable.St.prepared || up.st() != HostTable.St.prepared) {
            throw new ChainJournal.Fenced("the body is no longer prepared; it is not handed out");   // M33B1-CDX-49
        }
        Entry cur = entry(reservation);
        if (cur == null || cur.state() != State.prepared) throw new ChainJournal.Fenced("the journaled body is not prepared");
        journal.handout(reservation, h);
        builtHere.remove(reservation);
        if (h == Handout.submitted) submittedHere.add(reservation);
    }

    /**
     * M3.3b2: the node refused a submission this session sent (a 4xx: it never took the transaction), so the body is
     * released though it was recorded as submitted. Only a body submitted from this session, still prepared in the host and
     * in the journal after a pass (not on chain), settles {@code discarded}; anything else stays held for chain evidence.
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public synchronized void nodeRefused(String reservation) throws Exception {
        if (fenced != null) throw new ChainJournal.Fenced("the dev host was stopped: " + fenced);
        if (!submittedHere.contains(reservation)) throw new ChainJournal.Fenced("only a body submitted from this session is released on the node's refusal");
        if (!deferJournal && !poll()) throw new ChainJournal.Fenced("the chain kept moving; the body is kept until it settles");
        Entry e = entry(reservation);
        HostTable.Res lo = table.lower(reservation), up = table.upper(reservation);
        if (e == null || e.state() != State.prepared || e.handout() != Handout.submitted
                || lo == null || up == null || lo.st() != HostTable.St.prepared || up.st() != HostTable.St.prepared) {
            throw new ChainJournal.Fenced("the body is no longer only prepared; it is kept until it settles");
        }
        long tip = highest != null ? highest.slot() : chain.tip().slot();
        if (!settle(reservation, lo.txHash(), "discarded", null, tip) || table.lower(reservation).st() != HostTable.St.discarded) {
            fence("it did not discard the body as expected");
            throw new ChainJournal.Fenced("the dev host did not discard the body as expected; it was stopped");
        }
        submittedHere.remove(reservation);
        if (!deferJournal) journal.remove(Set.of(reservation));
    }

    /**
     * #42 (FAB-17): journaled bodies handed out as submitted that the host still holds prepared, the chain does not show,
     * and that are still valid at the highest tip seen: a submission that may never have reached the node, to send again.
     * Sending the same signed transaction again is harmless (the ledger takes it once).
     */
    public synchronized List<Entry> unlanded() throws Exception {
        if (fenced != null || highest == null) return List.of();
        List<Entry> out = new ArrayList<>();
        for (Entry e : journal.entries()) {
            HostTable.Res r = table.lower(e.reservation());
            if (e.handout() == Handout.submitted && e.state() == State.prepared && e.inclusion() == null
                    && r != null && r.st() == HostTable.St.prepared && e.validTo() > highest.slot()) out.add(e);
        }
        return out;
    }

    /** The session ended: nothing more is committed by this settlement (M33B1-CDX-44). Waits for a pass in progress. */
    public synchronized void close(String why) {
        if (fenced == null) fenced = why;
        closed = true;
    }

    private volatile boolean closed;

    /** Reservations this host prepared in this session (never restored): the only ones discard may release. */
    private final Set<String> builtHere = new HashSet<>();
    /** Bodies this session recorded as submitted, before their submission was sent (M3.3b2). */
    private final Set<String> submittedHere = new HashSet<>();

    private static String describe(HostTable.Outcome o) {
        return o.state() != null ? o.state().name() : o.error();
    }

    // ---- inside a call ------------------------------------------------------------------------------------------------

    private final java.util.concurrent.atomic.AtomicLong callEpoch = new java.util.concurrent.atomic.AtomicLong();
    /** The inclusion IntelliAda recorded for each reservation the host holds shallow. */
    private final Map<String, Inclusion> recorded = new java.util.concurrent.ConcurrentHashMap<>();

    /** Starts a forward call's bookkeeping. */
    public synchronized void beginCall() {
        callEpoch.incrementAndGet();
        callTips.clear();
    }

    /**
     * Wraps the session's reverse-read answerer: records each answer's chain point, and answers {@code failed} when its
     * tip would let the host expire a reservation whose body in fact landed (or whose inclusion is recorded but not
     * reported yet) (ADR5-CDX-25, the playground's read handler).
     */
    public HostSession.Reads guard(HostSession.Reads delegate) {
        return new HostSession.Reads() {
            @Override
            public ObjectNode read(String id, JsonNode params) throws Exception {
                return read(id, params, -1);
            }

            @Override
            public ObjectNode read(String id, JsonNode params, long call) throws Exception {
                return guarded(delegate, id, params, call);
            }
        };
    }

    /**
     * One guarded read. {@code call} is the forward call that owned it at dispatch (M33B1-CDX-24); its tip is charged to
     * that call only, and an answer that returns after its call ended (another epoch) to none.
     */
    private ObjectNode guarded(HostSession.Reads delegate, String id, JsonNode params, long call) throws Exception {
        long epoch = callEpoch.get();
        ObjectNode a = delegate.read(id, params);
        if (callEpoch.get() != epoch) return a;
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
                    if (callEpoch.get() == epoch) callTips.add(new ReadTip(kind, "failed", t, false, call));
                }
                return f;
            }
        }
        synchronized (this) {
            if (callEpoch.get() == epoch) callTips.add(new ReadTip(kind, outcome, t, a.has("more"), call));
        }
        return a;
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
            // A shallow body this tip would finalize must still be where IntelliAda recorded it (M33B1-CDX-25): a rollback
            // is reported first.
            if (r.st() == HostTable.St.shallow && t - r.slot() > margin) {
                Inclusion in = chain.inclusion(r.txHash());
                Inclusion was = recorded.get(r.id());
                boolean same = in != null && (was != null ? in.equals(was) : in.slot() == r.slot());
                if (!same) return r.txHash().substring(0, 16) + "… (rolled back)";
                continue;
            }
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
        endCall(method, result, error, -1);
    }

    /** {@code call}: the forward call's JSON-RPC id; only reads it owned count (−1: every read recorded since beginCall). */
    public synchronized void endCall(String method, @Nullable JsonNode result, @Nullable String error, long call) {
        if (call >= 0) callTips.removeIf(r -> r.call() != call);
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
            if (clean) {
                table.accepted(r.tip());
                acceptedHigh = Math.max(acceptedHigh, r.tip());   // a later lower tip is measured against it (M33B1-CDX-47)
            } else {
                table.uncertain(r.tip());
            }
        }
        callTips.clear();
        callEpoch.incrementAndGet();   // late answers of this call are charged to no other
        if (error == null && result != null && (method.equals("prepare") || method.equals("publish"))) {
            JsonNode p = result.path("prepared");
            // The protocol's shapes (M33B1-CDX-42): prepare always holds a prepared object; publish one, or is null.
            if (!p.isObject() && (method.equals("prepare") || !result.isNull())) fence("it answered " + method + " without a prepared body");
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
                JsonNode to = p.get("validTo"), from = p.get("validFrom");
                if (why == null && (to == null || !to.isIntegralNumber() || !to.canConvertToLong() || to.longValue() < 0
                        || (from != null && !from.isNull() && (!from.isIntegralNumber() || !from.canConvertToLong() || from.longValue() < 0)))) {
                    why = "it stated its prepared body's validity as other than slot integers";
                }
                if (why == null) {
                    Long stated = from == null || from.isNull() ? null : from.longValue();
                    if (!b.txHash().equals(p.path("txHash").textValue()) || b.validTo() == null || b.validTo() != p.path("validTo").longValue()
                            || !java.util.Objects.equals(b.validFrom(), stated)) {
                        why = "its prepared transaction's hash or validity differs from what it stated";
                    }
                }
                if (why == null && table.lower(id) != null) why = "it reused reservation id " + id;   // M33B1-CDX-29
                if (why != null) fence(why);
                else {
                    table.committed(id, b.txHash(), b.validFrom(), b.validTo());
                    builtHere.add(id);
                }
            }
        }
    }

    private boolean syncPending;

    /**
     * After a forward call (M33B1-DES-11): its tips to the model ({@link #endCall}), then the changed states to the journal.
     * If that write fails after a successful {@code prepare} or {@code publish}, the body (never handed out) is discarded,
     * or the host stopped when even that fails; either way the caller learns why. After any other call the result stands
     * and the write is retried before the next call ({@link #beforeCall}).
     */
    public synchronized void complete(String method, @Nullable JsonNode result, @Nullable String error) throws Exception {
        complete(method, result, error, -1);
    }

    public synchronized void complete(String method, @Nullable JsonNode result, @Nullable String error, long call) throws Exception {
        if (closed) {
            // A call that returns after its session closed commits nothing (M33B1-CDX-44).
            callTips.clear();
            if (error == null && (method.equals("prepare") || method.equals("publish"))) throw new ChainJournal.Fenced("the session ended");
            return;
        }
        // Advancing evidence is accepted only from the bound chain (M33B1-CDX-41).
        boolean advancingTips = callTips.stream().anyMatch(r -> (call < 0 || r.call() == call) && advancing(r.kind(), r.outcome()));
        if (advancingTips) {
            try {
                chainCheck.run();
            } catch (Exception changed) {
                callTips.clear();
                fence("the bound chain changed (" + changed.getMessage() + ")");
                endCall(method, null, "transport", call);
                throw new ChainJournal.Fenced("the bound chain changed; the dev host was stopped");
            }
        }
        endCall(method, result, error, call);
        boolean build = error == null && result != null && (method.equals("prepare") || method.equals("publish"));
        // A body the model refused (another hash, validity or a reused id) is never exposed (M33B1-CDX-33).
        if (build && fenced != null) throw new ChainJournal.Fenced("the dev host was stopped: " + fenced);
        try {
            sync();
            syncPending = false;
        } catch (Exception journalFailed) {
            syncPending = true;
            String id = build ? result.path("prepared").path("reservation").textValue() : null;
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
            } finally {
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
        if (closed) return;   // a closed session writes nothing after its module lock is released (M33B1-SEC-8)
        if (fenced != null) return;   // nor does a stopped host: its model is no longer trusted (M33B1-CDX-48)
        for (Entry j : journal.entries()) {
            HostTable.Res r = table.lower(j.reservation());
            if (r == null) continue;
            if (!r.txHash().equals(j.txHash())) {
                fence("its reservation " + j.reservation() + " holds another body than IntelliAda journaled");
                throw new ChainJournal.Fenced("the dev host's reservation " + j.reservation() + " holds another body; it was stopped");
            }
            State js = State.valueOf(r.st().name());
            if (js != j.state() || !java.util.Objects.equals(r.slot(), j.slot())) {
                journal.update(j.reservation(), y -> y.withState(js, r.slot()).withEvent("read tip -> " + js));
            }
        }
    }
}
