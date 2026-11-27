package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Entry;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Loaded;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.ModuleJournal;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.StoreFailed;
import com.bloxbean.intelliada.idea.joust.tx.TxCbor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

/**
 * One module's journal on one chain (ADR-005 D17, simplified for devnets and test networks by §12 E12 and L44).
 * <p>
 * The journal is a history and restart aid: it keeps each body IntelliAda handed out, with what the host and the chain
 * showed of it, so a restarted host is restored with the bodies that can still land and the run panel can show what
 * happened. It never blocks a developer:
 * <ul>
 *   <li>a missing journal is a new start; one that cannot be read is moved aside, said once, and a new one begun;</li>
 *   <li>each module restores only its own bodies, from the chain as it is now;</li>
 *   <li>only a live body (prepared, or included but not yet final) holds its inputs; another module's live body is
 *   refused as a conflict at admission, a final one is history only.</li>
 * </ul>
 * What stays strict, because signing (M3.3b2) depends on it: an entry is written before its body is handed out (I25), a
 * handed-out body is released only by chain evidence or expiry (I30), and one host per module and chain (I33).
 */
public final class ChainJournal {

    /** Building, handing out or restoring is not possible now; the reason is shown as plain text. */
    public static final class Fenced extends Exception {
        public Fenced(String why) {
            super(why, null, false, false);
        }
    }

    /** What a new host finds for its module on the chain. */
    public enum History {
        /** Nothing journaled for this module on this chain. */
        NEW_CHAIN,
        /** The module's journal, read: its live and final bodies are restored. */
        COMPLETE,
        /** The module's journal could not be read; it was moved aside and a new one begun. */
        SET_ASIDE
    }

    /**
     * What opening found: the bodies to restore, whether the host starts restoring, and, for a journal moved aside, a
     * note for the run panel's console.
     */
    public record Opening(List<Entry> survivors, boolean restoring, History history, @Nullable String note) {
    }

    private final JournalStore store;
    private final String chain;
    private final String chainKey;
    private final String module;
    private final long settlementSlots;

    /**
     * @param chain           the chain identity (D14: public magic, or devnet magic and block-1 hash), never a node configuration
     * @param module          the module's stable key ({@link JournalStore#key} of its real path)
     * @param settlementSlots the value sent to the host in {@code initialize} (D14b)
     */
    public ChainJournal(JournalStore store, String chain, String module, long settlementSlots) {
        this.store = store;
        this.chain = chain;
        this.chainKey = JournalStore.key(chain);
        this.module = module;
        this.settlementSlots = settlementSlots;
    }

    public String chain() {
        return chain;
    }

    public String module() {
        return module;
    }

    public long settlementSlots() {
        return settlementSlots;
    }

    /** Whether the chain is a local devnet (its identity says so, D14): a lower tip there is acted on at once. */
    public boolean devnet() {
        return chain.startsWith("devnet-");
    }

    // ---- the chain's in-process monitor -------------------------------------------------------------------------------

    private static final Map<String, ReentrantLock> MONITORS = new ConcurrentHashMap<>();

    /**
     * Enters the chain's in-process monitor (M33B1-CDX-153, Fable S8): every read-then-decide sequence on the chain in this
     * IDE (a settlement pass, a restore) holds it, so no other module's sequence interleaves with it. Bounded: a wait past
     * 30 s refuses with a message instead of hanging the caller behind another module's slow host (#39).
     * <p>Lock order, never inverted: the session's call lock, {@code Settlement}'s monitor, this monitor, the store's chain
     * lock. Nothing holding it calls into another module's session.
     */
    public JournalStore.Held enter() throws Fenced, InterruptedException {
        ReentrantLock m = MONITORS.computeIfAbsent(store.identity() + "|" + chainKey, k -> new ReentrantLock());
        if (!m.tryLock(30, TimeUnit.SECONDS)) {
            throw new Fenced("another module's settlement on this chain is taking long; try again in a moment");
        }
        return m::unlock;
    }

    // ---- opening ------------------------------------------------------------------------------------------------------

    /**
     * Opens this module's journal for a new host. Missing: a new start. Unreadable (another version, a damaged file):
     * moved aside under a {@code .lost-<time>} name, said once in the note, and a new journal begun. Otherwise its
     * entries are the host's to restore; the host starts restoring when there are any.
     */
    public Opening open() throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            Loaded<ModuleJournal> j = store.journal(chainKey, module);
            if (j.lost()) {
                java.nio.file.Path aside = store.setAside(chainKey, module);
                String note = "IntelliAda's journal for this module on this chain could not be read; it was moved aside"
                        + (aside == null ? "" : " to " + aside.getFileName()) + " and a new one begun";
                return new Opening(List.of(), false, History.SET_ASIDE, note);
            }
            if (!j.ok()) return new Opening(List.of(), false, History.NEW_CHAIN, null);
            List<Entry> survivors = j.value().entries();
            return new Opening(survivors, !survivors.isEmpty(), History.COMPLETE, null);
        }
    }

    // ---- entries ------------------------------------------------------------------------------------------------------

    /** This module's entries, read now; a journal that cannot be read reads as none. */
    public List<Entry> entries() throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            return mine();
        }
    }

    /** Updates one entry in place (the host's state, an inclusion, an event); an unknown reservation changes nothing. */
    public void update(String reservation, UnaryOperator<Entry> change) throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            List<Entry> next = new ArrayList<>();
            boolean changed = false;
            for (Entry e : mine()) {
                if (e.reservation().equals(reservation)) {
                    next.add(change.apply(e));
                    changed = true;
                } else {
                    next.add(e);
                }
            }
            if (changed) writeMine(next);
        }
    }

    /** Removes entries (a body declined before handout, or a final entry past its retention). */
    public void remove(Set<String> reservations) throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            List<Entry> mine = mine();
            List<Entry> next = mine.stream().filter(e -> !reservations.contains(e.reservation())).toList();
            if (next.size() != mine.size()) writeMine(next);
        }
    }

    /** Replaces this module's entries (a restore's result: the host's new ids and states). */
    public void replace(List<Entry> entries) throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            writeMine(entries);
        }
    }

    /**
     * Journals a body being handed out (I25), before it is signed or exported. Refused when the body cannot be read, its
     * facts differ from the entry's, it is valid beyond what a host would build, it would not fit, or another live body on
     * this chain (any module's) spends one of its inputs, is the same transaction, or publishes the same script.
     */
    @org.jetbrains.annotations.ApiStatus.Internal   // unreachable from production in M3.3b1; reviewed in M3.3b2 (ADR-005 L43, #38)
    public void admit(Entry e, long tip, long now) throws StoreFailed, Fenced, InterruptedException {
        if (e.handout() == JournalStore.Handout.none || e.state() != JournalStore.State.prepared) {
            throw new IllegalArgumentException("admission journals a prepared body being handed out");
        }
        if (e.cbor().length() > 2L * JournalStore.MAX_BODY_BYTES) throw new Fenced("the prepared transaction is larger than IntelliAda journals");
        TxCbor.Body body;
        try {
            body = e.body();
        } catch (TxCbor.Malformed m) {
            throw new Fenced("the prepared transaction cannot be read: " + m.getMessage());
        }
        if (!body.txHash().equals(e.txHash()) || body.validTo() == null || body.validTo() != e.validTo()
                || !java.util.Objects.equals(body.validFrom(), e.validFrom())) {
            throw new Fenced("the prepared transaction's hash or validity differs from the journal entry's");
        }
        if (!JournalStore.readsBack(e)) throw new Fenced("the journal entry would not read back as written");
        if (e.validTo() > Math.max(tip, now) + TimePolicy.LARGEST_TTL) {
            throw new Fenced("the prepared transaction is valid further ahead than a dev host builds");
        }
        try (var held = store.lock(chainKey)) {
            String conflict = overlap(body);
            if (conflict != null) throw new Fenced(conflict);
            List<Entry> next = new ArrayList<>(mine());
            if (next.stream().anyMatch(x -> x.reservation().equals(e.reservation()))) throw new Fenced("the host reused a reservation id");
            next.add(e);
            if (!store.fits(new ModuleJournal(chain, module, next))) throw new Fenced("this module's journal on the chain is full");
            writeMine(next);
        }
    }

    /** Moves a journaled body's handout forward (approval pending to submitted or exported). */
    @org.jetbrains.annotations.ApiStatus.Internal   // unreachable from production in M3.3b1; reviewed in M3.3b2 (ADR-005 L43, #38)
    public void handout(String reservation, JournalStore.Handout h) throws StoreFailed, Fenced, InterruptedException {
        try (var held = store.lock(chainKey)) {
            List<Entry> next = new ArrayList<>();
            boolean found = false;
            for (Entry e : mine()) {
                if (e.reservation().equals(reservation)) {
                    if (e.handout() != JournalStore.Handout.approvalPending) throw new Fenced("the body's handout cannot change");
                    next.add(e.withHandout(h).withEvent("handed out: " + h.name()));
                    found = true;
                } else {
                    next.add(e);
                }
            }
            if (!found) throw new Fenced("the body is not journaled");
            writeMine(next);
        }
    }

    /**
     * Whether a final entry has outlived its retention at {@code tip}: {@code 2 × settlementSlots} past the later of its
     * inclusion and its {@code validTo}. Final entries are history only; they hold no inputs.
     */
    public boolean retentionOver(Entry e, long tip) {
        long point = switch (e.state()) {
            case confirmed -> e.slot() == null ? e.validTo() : Math.max(e.slot(), e.validTo());
            case expired -> e.validTo();
            case discarded -> -1;
            default -> Long.MAX_VALUE;
        };
        if (point == Long.MAX_VALUE) return false;
        if (point < 0) return true;
        return tip > point + 2 * settlementSlots;
    }

    /** Every live entry on the chain, of every module whose journal reads (I33: admission and the panel). */
    public List<Entry> liveOnChain() throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            List<Entry> out = new ArrayList<>();
            for (String m : store.journalModules(chainKey)) {
                Loaded<ModuleJournal> j = store.journal(chainKey, m);
                if (j.ok()) for (Entry e : j.value().entries()) if (e.live()) out.add(e);
            }
            return out;
        }
    }

    // ---- inside the chain lock ----------------------------------------------------------------------------------------

    private List<Entry> mine() throws StoreFailed {
        Loaded<ModuleJournal> j = store.journal(chainKey, module);
        return j.ok() ? j.value().entries() : List.of();
    }

    private void writeMine(List<Entry> entries) throws StoreFailed {
        store.writeJournal(new ModuleJournal(chain, module, entries));
    }

    /**
     * Why {@code body} conflicts with a live body on the chain, or null (I33; protocol D8's reference rule). Only live
     * bodies hold inputs (Fable S11): an expired or final one is history. A journal that cannot be read is skipped.
     */
    private @Nullable String overlap(TxCbor.Body body) throws StoreFailed {
        Set<String> spends = new HashSet<>(body.inputs());
        spends.addAll(body.collateral());
        for (String m : store.journalModules(chainKey)) {
            Loaded<ModuleJournal> j = store.journal(chainKey, m);
            if (!j.ok()) continue;
            for (Entry x : j.value().entries()) {
                if (!x.live()) continue;
                TxCbor.Body b;
                try {
                    b = x.body();
                } catch (TxCbor.Malformed malformed) {
                    continue;
                }
                String holder = (x.state() == JournalStore.State.prepared ? "a pending transaction" : "a transaction included but not yet final")
                        + (x.label().isEmpty() ? "" : " (" + x.label() + ")");
                if (b.txHash().equals(body.txHash())) return "the same transaction is already pending on this chain";
                Set<String> theirs = new HashSet<>(b.inputs());
                theirs.addAll(b.collateral());
                for (String s : spends) if (theirs.contains(s)) return "an input is reserved by " + holder + " until it settles";
                for (String s : body.referenceInputs()) if (theirs.contains(s)) return "a reference input is being spent by " + holder;
                for (String p : body.publishes()) if (b.publishes().contains(p)) return holder + " already publishes " + p;
            }
        }
        return null;
    }
}
