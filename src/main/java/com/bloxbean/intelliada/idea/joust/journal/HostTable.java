package com.bloxbean.intelliada.idea.joust.journal;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ADR-005 D17 "The reply is checked", I32 (M3.3b1): IntelliAda's model of one host session's reservation table, the rules
 * of protocol 1's table as JOUST's JVM host applies them ({@code Reservations} at {@code 01bad1f7}):
 * <ul>
 *   <li>an accepted tip advances every reservation: {@code prepared} expires when {@code tip - validTo > margin},
 *   {@code shallow} becomes {@code confirmed} when {@code tip - slot > margin}; nothing advances while restore is open;</li>
 *   <li>a {@code settled} event is applied, then the thresholds at that event's tip, then that tip to every other
 *   reservation;</li>
 *   <li>{@code confirmed} needs the slot inside {@code [validFrom, validTo)} and at most the tip, and equal to a recorded
 *   inclusion; {@code rolledBack} of a final inclusion is {@code refusal/deepRollback}; {@code expired} needs the margin
 *   passed and restore closed ({@code bad/notExpired}); a repeated final status is accepted, competing evidence is
 *   {@code bad/conflict}.</li>
 * </ul>
 * Tips whose acceptance by the host is uncertain (D17: a call that ended in a read-related error, was cancelled or timed
 * out, or a partial listing) are applied to an <b>upper</b> copy only; the host's reply may match either copy, and the
 * lower (less advanced) one is what the journal keeps until a reply settles which one the host holds.
 */
public final class HostTable {

    public enum St { prepared, shallow, confirmed, expired, discarded }

    /** One reservation as the host holds it. {@code from} is 0 for an absent lower bound (the host's reading). */
    public record Res(String id, String txHash, long from, long to, St st, @Nullable Long slot) {
        boolean live() {
            return st == St.prepared || st == St.shallow;
        }

        Res as(St s, @Nullable Long at) {
            return new Res(id, txHash, from, to, s, at);
        }
    }

    /** What the host answers to an event: a state, or an error {@code kind/code}. */
    public record Outcome(@Nullable St state, @Nullable String error) {
        static Outcome of(St s) {
            return new Outcome(s, null);
        }

        static Outcome err(String e) {
            return new Outcome(null, e);
        }
    }

    /** The expected answers to one event, per copy. */
    public record Expected(String id, Outcome lower, Outcome upper, String status, @Nullable Long slot, long tip) {
        public boolean certain() {
            return lower.equals(upper);
        }
    }

    private final long margin;
    private Map<String, Res> lower = new LinkedHashMap<>();
    private Map<String, Res> upper = new LinkedHashMap<>();
    private boolean reconcile;
    private boolean deep;

    public HostTable(long settlementSlots, boolean restoring) {
        this.margin = settlementSlots;
        this.reconcile = restoring;
    }

    public synchronized boolean reconciling() {
        return reconcile;
    }

    public synchronized boolean deepRollback() {
        return deep;
    }

    public synchronized @Nullable Res lower(String id) {
        return lower.get(id);
    }

    public synchronized @Nullable Res upper(String id) {
        return upper.get(id);
    }

    public synchronized List<Res> lowerAll() {
        return List.copyOf(lower.values());
    }

    /** A body the host committed (a successful {@code prepare} or {@code publish}): {@code prepared} in both copies. */
    public synchronized void committed(String id, String txHash, @Nullable Long validFrom, long validTo) {
        Res r = new Res(id, txHash, validFrom == null ? 0 : validFrom, validTo, St.prepared, null);
        lower.put(id, r);
        upper.put(id, r);
    }

    /** A tip the host accepted for certain: both copies advance. */
    public synchronized void accepted(long tip) {
        advanceAll(lower, tip);
        advanceAll(upper, tip);
    }

    /** A tip the host may or may not have accepted: the upper copy only. */
    public synchronized void uncertain(long tip) {
        advanceAll(upper, tip);
    }

    private void advanceAll(Map<String, Res> m, long tip) {
        if (reconcile) return;
        m.replaceAll((k, r) -> advance(r, tip));
    }

    private Res advance(Res e, long tip) {
        if (e.st == St.prepared && tip - e.to > margin) return e.as(St.expired, null);
        if (e.st == St.shallow && tip - e.slot > margin) return e.as(St.confirmed, e.slot);
        return e;
    }

    /** What each copy expects the host to answer to {@code settled}. */
    public synchronized Expected expect(String id, String txHash, String status, @Nullable Long slot, long tip) {
        return new Expected(id, outcome(lower.get(id), txHash, status, slot, tip), outcome(upper.get(id), txHash, status, slot, tip),
                status, slot, tip);
    }

    private Outcome outcome(@Nullable Res e, String txHash, String status, @Nullable Long slot, long tip) {
        if (e == null) return Outcome.err("bad/unknownReservation");
        if (!e.txHash.equals(txHash)) return Outcome.err("bad/conflict");
        Res next;
        switch (status) {
            case "confirmed" -> {
                if (slot == null || slot < e.from || slot >= e.to) return Outcome.err("bad/conflict");
                if (slot > tip || (e.slot != null && !e.slot.equals(slot))) return Outcome.err("bad/conflict");
                if (e.st == St.confirmed) next = e;
                else if (!e.live()) return Outcome.err("bad/conflict");
                else next = e.as(St.shallow, slot);
            }
            case "rolledBack" -> {
                if (e.st == St.confirmed) return Outcome.err("refusal/deepRollback");
                if (!e.live()) return Outcome.err("bad/conflict");
                next = e.as(St.prepared, null);
            }
            case "expired" -> {
                if (e.st == St.expired) next = e;
                else if (e.st != St.prepared) return Outcome.err("bad/conflict");
                else if (tip - e.to <= margin || reconcile) return Outcome.err("bad/notExpired");
                else next = e.as(St.expired, null);
            }
            case "discarded" -> {
                if (e.st != St.prepared && e.st != St.discarded) return Outcome.err("bad/conflict");
                next = e.as(St.discarded, null);
            }
            default -> throw new IllegalArgumentException(status);
        }
        return Outcome.of(reconcile ? next.st : advance(next, tip).st);
    }

    /**
     * The host's reply to an expected event: a state, or its error {@code kind/code}. Returns false when it matches
     * neither copy (the caller keeps the reservation and fences the host). On a match the copies collapse onto the
     * one the host showed it holds, and the event and its tip are applied as the host applies them.
     */
    public synchronized boolean reply(Expected x, @Nullable St state, @Nullable String error) {
        Outcome got = new Outcome(state, error);
        boolean lo = x.lower.equals(got), up = x.upper.equals(got);
        if (!lo && !up) return false;
        if (lo && !up) upper = new LinkedHashMap<>(lower);
        else if (up && !lo) lower = new LinkedHashMap<>(upper);
        if (error != null) {
            if (error.equals("refusal/deepRollback")) deep = true;
            return true;   // the host changed nothing (a deep rollback only fences building)
        }
        apply(lower, x);
        apply(upper, x);
        return true;
    }

    private void apply(Map<String, Res> m, Expected x) {
        Res e = m.get(x.id);
        Res next = switch (x.status) {
            case "confirmed" -> e.st == St.confirmed ? e : e.as(St.shallow, x.slot);
            case "rolledBack" -> e.as(St.prepared, null);
            case "expired" -> e.as(St.expired, null);
            case "discarded" -> e.as(St.discarded, null);
            default -> throw new IllegalArgumentException(x.status);
        };
        m.put(x.id, reconcile ? next : advance(next, x.tip));
        advanceAll(m, x.tip);
    }

    /**
     * A restore batch the host answered with {@code ids} (one per transaction, in order); identical bodies share an id.
     * {@code complete} closes restore (the host then advances thresholds again). Returns false when the ids are not
     * consistent with the batch (the caller keeps the journal and fences the host).
     */
    public synchronized boolean restored(List<Restored> batch, List<String> ids, boolean complete) {
        if (!reconcile || ids.size() != batch.size()) return false;
        Map<String, String> byHash = new HashMap<>();
        for (Res r : lower.values()) if (r.live()) byHash.put(r.txHash, r.id);
        Map<String, Res> added = new LinkedHashMap<>();
        for (int i = 0; i < batch.size(); i++) {
            Restored t = batch.get(i);
            String id = ids.get(i);
            if (id == null || !JournalStore.RESERVATION.matcher(id).matches()) return false;
            String known = byHash.get(t.txHash());
            if (known != null) {
                if (!known.equals(id)) return false;
                Res r = lower.containsKey(id) ? lower.get(id) : added.get(id);
                if (r.st != t.st() || !Objects.equals(r.slot, t.slot())) return false;
                continue;
            }
            if (lower.containsKey(id) || added.containsKey(id)) return false;
            added.put(id, new Res(id, t.txHash(), t.validFrom() == null ? 0 : t.validFrom(), t.validTo(), t.st(), t.slot()));
            byHash.put(t.txHash(), id);
        }
        lower.putAll(added);
        upper.putAll(added);
        if (complete) reconcile = false;
        return true;
    }

    /** One transaction of a restore batch, as sent. */
    public record Restored(String txHash, @Nullable Long validFrom, long validTo, St st, @Nullable Long slot) {
    }
}
