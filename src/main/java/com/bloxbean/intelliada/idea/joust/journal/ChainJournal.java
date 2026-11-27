package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.journal.JournalStore.ChainRecord;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Entry;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Fence;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Loaded;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.ModuleJournal;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Registration;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.RegistryEntry;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.Rewind;
import com.bloxbean.intelliada.idea.joust.journal.JournalStore.StoreFailed;
import com.bloxbean.intelliada.idea.joust.tx.TxCbor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * ADR-005 D17 for one chain (D14's identity) and one contract module (M3.3b1): what a new host session must restore and
 * how long building waits, the one admission that journals a body and raises the chain's mark before it is handed out
 * (I25), cross-module overlap on the chain (I33), and the chain's fences. Every operation runs under the chain lock and
 * reads the files again; nothing is cached across operations, so two hosts of one chain (two modules, two IDEs) see
 * each other's writes.
 */
public final class ChainJournal {

    /** D14b's hard ceilings used for a lost mark: {@code maxNowAheadSlots} and the largest {@code maxTtlSlots}. */
    static final long MAX_NOW_AHEAD = 900, LARGEST_TTL = 7_200;
    /** How long a final (confirmed or expired) entry stays journaled past its threshold (§12 E10). */
    static final long RETAIN_AHEAD = MAX_NOW_AHEAD;

    /** Building, signing or restoring is not possible now; the reason is shown as plain text. */
    public static final class Fenced extends Exception {
        public Fenced(String why) {
            super(why, null, false, false);
        }
    }

    /**
     * A new host's starting point: the module's surviving entries, whether the host starts with {@code restoring:true},
     * and the slot an observed tip must strictly pass before restore may complete (null: at once). A {@code permanent}
     * reason means the chain stays fenced (views and listings only).
     */
    public record Opening(List<Entry> survivors, boolean restoring, @Nullable Long completeAbove, @Nullable String permanent,
                          String history) {
    }

    private final JournalStore store;
    private final String chain;
    private final String chainKey;
    private final String module;
    private final long settlementSlots;

    /**
     * @param chain           the chain identity (D14: public magic, or devnet magic and block-1 hash), never a node configuration
     * @param module          the module's stable key ({@link JournalStore#key} of its real path)
     * @param settlementSlots the chain's class value (D14b)
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

    // ---- open ---------------------------------------------------------------------------------------------------------

    /**
     * Classifies the chain's history for a new host of this module (D17 "Restart"), persisting what the classification
     * requires before anything is lifted: a lost mark's derived bound and its fence, a lost journal's fence, the both-lost
     * marker. {@code tipAtDetection} is a stable tip read now (a rewind before detection cannot lower the base).
     */
    public Opening open(long tipAtDetection) throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            if (store.permanentlyFenced(chainKey)) {
                return new Opening(List.of(), false, null, PERMANENT, "both lost");
            }
            Loaded<RegistryEntry> reg = store.registry(chainKey);
            Loaded<ChainRecord> rec = store.record(chainKey);
            Map<String, Loaded<ModuleJournal>> journals = journals(rec.value());
            boolean anyJournal = journals.values().stream().anyMatch(Loaded::exists);
            Loaded<ModuleJournal> mine = journals.getOrDefault(module, Loaded.missing());

            ChainRecord c = rec.value();
            boolean stale = c != null && reg.ok() && reg.value().generation() > c.generation();
            boolean unlisted = c != null && journals.entrySet().stream()
                    .anyMatch(e -> e.getValue().exists() && !c.modules().contains(e.getKey()));
            if (c != null && !stale && !unlisted) {
                List<String> lost = new ArrayList<>();
                for (String m : c.modules()) {
                    Loaded<ModuleJournal> j = journals.getOrDefault(m, Loaded.missing());
                    if (!j.ok()) lost.add(m);
                }
                List<Entry> survivors = mine.ok() ? mine.value().entries() : List.of();
                if (!lost.isEmpty()) {
                    // Lost history, mark intact: fenced until a tip strictly above the mark.
                    long until = Math.max(c.mark() == null ? 0 : c.mark(), c.fence() == null ? 0 : c.fence().until());
                    store.writeRecord(new ChainRecord(chain, c.mark(), c.modules(), c.generation(),
                            new Fence(until, "a module's journal on this chain is missing or unreadable"), c.rewind(), c.registrations()));
                    return new Opening(survivors, true, until, null, "lost journal");
                }
                boolean restoring = !survivors.isEmpty() || c.fence() != null || c.rewind() != null;
                Long above = c.fence() == null ? null : c.fence().until();
                return new Opening(survivors, restoring, above, null, c.fence() == null && c.rewind() == null ? "complete" : "fenced");
            }
            if (c == null && !rec.exists() && !reg.exists() && !anyJournal) {
                return new Opening(List.of(), false, null, null, "new chain");
            }
            if (!reg.ok() && (rec.exists() || anyJournal || reg.exists())) {
                // Registry entry and chain record both unusable while history survives: no bound can be derived and no
                // restored copy can be shown fresh (ADR5-SEC-19, ADR5-CDX-3). Persisted, so a restored backup cannot lift it.
                store.writePermanentFence(chain, PERMANENT);
                return new Opening(List.of(), false, null, PERMANENT, "both lost");
            }
            // Lost mark (or a stale record): a conservative bound from the policy's hard ceilings (ADR5-CDX-3, -SEC-18).
            long base = Math.max(tipAtDetection, Math.max(reg.value().highestTip(), reg.value().highestNow()));
            Set<String> modules = new LinkedHashSet<>();
            for (var e : journals.entrySet()) {
                if (!e.getValue().ok()) continue;
                modules.add(e.getKey());
                for (Entry x : e.getValue().value().entries()) {
                    base = Math.max(base, x.validTo());
                    if (x.slot() != null) base = Math.max(base, x.slot());
                }
            }
            if (c != null) {
                modules.addAll(c.modules());
                if (c.mark() != null) base = Math.max(base, c.mark());
            }
            long bound = base + MAX_NOW_AHEAD + LARGEST_TTL + settlementSlots;
            long gen = Math.max(reg.value().generation(), c == null ? 0 : c.generation()) + 1;
            List<Registration> regs = c == null ? List.of() : c.registrations();
            // Persisted before anything is lifted, so an IDE restart during recovery keeps the bound and its fence.
            store.writeRecord(new ChainRecord(chain, bound, modules, gen, new Fence(bound, stale || unlisted
                    ? "the chain record is older than the registry" : "the chain record is missing or unreadable"), null, regs));
            store.writeRegistry(new RegistryEntry(chain, reg.value().highestTip(), reg.value().highestNow(), gen));
            List<Entry> survivors = mine.ok() ? mine.value().entries() : List.of();
            return new Opening(survivors, true, bound, null, "lost mark");
        }
    }

    static final String PERMANENT = "IntelliAda lost both its record and its registry entry for this chain while a journal survives; "
            + "building on this chain stays blocked (views and listings still work). A recreated devnet starts afresh";

    /** Every module journal of the chain: those the record lists and those present in its directory. */
    private Map<String, Loaded<ModuleJournal>> journals(@Nullable ChainRecord c) throws StoreFailed {
        Set<String> names = new LinkedHashSet<>(store.journalModules(chainKey));
        if (c != null) names.addAll(c.modules());
        names.add(module);
        Map<String, Loaded<ModuleJournal>> out = new LinkedHashMap<>();
        for (String m : names) {
            Loaded<ModuleJournal> j = store.journal(chainKey, m);
            if (j.ok() && !j.value().chain().equals(chain)) j = Loaded.unreadable();
            out.put(m, j);
        }
        return out;
    }

    // ---- admission ----------------------------------------------------------------------------------------------------

    /**
     * Journals a body before it is handed out (I25, ADR5-SEC-5): under the chain lock, after checking that building is
     * not fenced and that no live entry of any module on the chain spends, references or publishes what this body does
     * (I33), the mark is raised to cover {@code validTo + settlementSlots}, the registry's highest tip and now are raised,
     * and the entry is written; any failure refuses (the caller then settles the body {@code discarded}).
     *
     * @param tip the stable tip the body was prepared against; {@code now} the {@code now} sent
     */
    public void admit(Entry e, long tip, long now) throws StoreFailed, Fenced, InterruptedException {
        if (e.handout() == JournalStore.Handout.none || e.state() != JournalStore.State.prepared) {
            throw new IllegalArgumentException("admission journals a prepared body being handed out");
        }
        TxCbor.Body body;
        try {
            body = e.body();
        } catch (TxCbor.Malformed m) {
            throw new Fenced("the prepared transaction cannot be read: " + m.getMessage());
        }
        if (!body.txHash().equals(e.txHash()) || body.validTo() == null || body.validTo() != e.validTo()) {
            throw new Fenced("the prepared transaction's hash or validity differs from the host's");
        }
        try (var held = store.lock(chainKey)) {
            if (store.permanentlyFenced(chainKey)) throw new Fenced(PERMANENT);
            Loaded<ChainRecord> rec = store.record(chainKey);
            Loaded<RegistryEntry> reg = store.registry(chainKey);
            if (rec.exists() && !rec.ok()) throw new Fenced("the chain record is unreadable; start the session again to recover");
            if (reg.exists() && !reg.ok() && rec.ok()) {
                // The record is intact: the registry entry is rebuilt from it at this admission.
                reg = Loaded.missing();
            }
            ChainRecord c = rec.value();
            if (c != null && c.fence() != null) throw new Fenced("building waits until the chain passes slot " + c.fence().until());
            if (c != null && c.rewind() != null) throw new Fenced("a rewind is being reconciled");
            if (c == null && (reg.ok() || !store.journalModules(chainKey).isEmpty())) {
                throw new Fenced("the chain record is missing; start the session again to recover");
            }
            if (reg.ok() && c != null && reg.value().generation() > c.generation()) {
                throw new Fenced("the chain record is older than the registry; start the session again to recover");
            }
            Map<String, Loaded<ModuleJournal>> all = journals(c);
            for (var j : all.entrySet()) {
                if (j.getValue().exists() && !j.getValue().ok()) throw new Fenced("a journal on this chain is unreadable");
                if (c != null && j.getValue().exists() && !c.modules().contains(j.getKey())) {
                    throw new Fenced("a journal on this chain is not in its record; start the session again to recover");
                }
            }
            String overlap = overlap(body, e.reservation(), all);
            if (overlap != null) throw new Fenced(overlap);
            long mark = Math.max(c == null || c.mark() == null ? 0 : c.mark(), Math.addExact(e.validTo(), settlementSlots));
            Set<String> modules = new LinkedHashSet<>(c == null ? Set.of() : c.modules());
            modules.add(module);
            long gen = Math.max(c == null ? 0 : c.generation(), reg.ok() ? reg.value().generation() : 0) + 1;
            ChainRecord next = new ChainRecord(chain, mark, modules, gen, null, null, c == null ? List.of() : c.registrations());
            RegistryEntry r = new RegistryEntry(chain, Math.max(tip, reg.ok() ? reg.value().highestTip() : 0),
                    Math.max(now, reg.ok() ? reg.value().highestNow() : 0), gen);
            List<Entry> entries = new ArrayList<>(all.get(module).ok() ? all.get(module).value().entries() : List.of());
            if (entries.stream().anyMatch(x -> x.reservation().equals(e.reservation()))) {
                throw new Fenced("the host reused a reservation id");
            }
            entries.add(e);
            // The mark first, then the registry, then the entry: a crash between them leaves a mark that over-covers.
            store.writeRecord(next);
            store.writeRegistry(r);
            store.writeJournal(new ModuleJournal(chain, module, entries));
        }
    }

    /** Why {@code body} conflicts with a live entry on the chain, or null (I33; protocol D8's reference rule). */
    private static @Nullable String overlap(TxCbor.Body body, String reservation, Map<String, Loaded<ModuleJournal>> all) {
        Set<String> spends = new HashSet<>(body.inputs());
        spends.addAll(body.collateral());
        for (var j : all.values()) {
            if (!j.ok()) continue;
            for (Entry x : j.value().entries()) {
                if (!x.live()) continue;
                TxCbor.Body b;
                try {
                    b = x.body();
                } catch (TxCbor.Malformed m) {
                    return "a journaled transaction cannot be read";
                }
                if (b.txHash().equals(body.txHash())) return "the same transaction is already journaled on this chain";
                Set<String> theirs = new HashSet<>(b.inputs());
                theirs.addAll(b.collateral());
                for (String s : spends) if (theirs.contains(s)) return "an input is reserved by another live transaction on this chain";
                for (String s : body.referenceInputs()) if (theirs.contains(s)) {
                    return "a reference input is being spent by another live transaction on this chain";
                }
                for (String p : body.publishes()) if (b.publishes().contains(p)) {
                    return "another live transaction on this chain already publishes " + p;
                }
            }
        }
        return null;
    }

    // ---- entries ------------------------------------------------------------------------------------------------------

    /** This module's entries, read now (strict). */
    public List<Entry> entries() throws StoreFailed, Fenced, InterruptedException {
        try (var held = store.lock(chainKey)) {
            return mine().entries();
        }
    }

    private ModuleJournal mine() throws StoreFailed, Fenced {
        Loaded<ModuleJournal> j = store.journal(chainKey, module);
        if (j.lost()) throw new Fenced("this module's journal on the chain is unreadable");
        if (j.ok() && !j.value().chain().equals(chain)) throw new Fenced("this module's journal belongs to another chain");
        return j.ok() ? j.value() : new ModuleJournal(chain, module, List.of());
    }

    /** Changes one entry; false when it is not journaled. Written before it returns. */
    public boolean update(String reservation, UnaryOperator<Entry> change) throws StoreFailed, Fenced, InterruptedException {
        try (var held = store.lock(chainKey)) {
            ModuleJournal j = mine();
            List<Entry> next = new ArrayList<>(j.entries());
            for (int i = 0; i < next.size(); i++) {
                if (next.get(i).reservation().equals(reservation)) {
                    Entry e = change.apply(next.get(i));
                    if (!e.reservation().equals(reservation) || !e.txHash().equals(next.get(i).txHash())) {
                        throw new IllegalArgumentException("an update keeps the reservation and the body");
                    }
                    next.set(i, e);
                    store.writeJournal(new ModuleJournal(chain, module, next));
                    return true;
                }
            }
            return false;
        }
    }

    /** Removes entries (a body declined before handout, or a final entry past its retention). */
    public void remove(Set<String> reservations) throws StoreFailed, Fenced, InterruptedException {
        try (var held = store.lock(chainKey)) {
            ModuleJournal j = mine();
            List<Entry> next = j.entries().stream().filter(e -> !reservations.contains(e.reservation())).toList();
            if (next.size() != j.entries().size()) store.writeJournal(new ModuleJournal(chain, module, next));
        }
    }

    /**
     * Replaces the module's journal after a restore (D17: settlement then uses the ids {@code restore} returned); every
     * entry must read back as valid, and ids must be distinct.
     */
    public void replace(List<Entry> entries) throws StoreFailed, Fenced, InterruptedException {
        Set<String> ids = new HashSet<>();
        for (Entry e : entries) if (!ids.add(e.reservation())) throw new IllegalArgumentException("restored ids repeat");
        try (var held = store.lock(chainKey)) {
            mine();
            store.writeJournal(new ModuleJournal(chain, module, entries));
        }
    }

    /** Whether a final entry has outlived its retention at {@code tip} (§12 E10). */
    public boolean retentionOver(Entry e, long tip) {
        long point = switch (e.state()) {
            case confirmed -> e.slot() == null ? e.validTo() : e.slot();
            case expired -> e.validTo();
            case discarded -> -1;
            default -> Long.MAX_VALUE;
        };
        if (point == Long.MAX_VALUE) return false;
        if (point < 0) return true;
        return tip > point + 2 * settlementSlots + RETAIN_AHEAD;
    }

    // ---- fences, rewinds, registrations -------------------------------------------------------------------------------

    /** The chain record now, or null when there is none (or it is unreadable). */
    public @Nullable ChainRecord record() throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            return store.record(chainKey).value();
        }
    }

    /**
     * Clears the recovery fence once an observed tip is strictly above it (D17; ADR5-CDX-3). Returns whether building
     * is unfenced afterwards (no fence, no rewind, not permanent).
     */
    public boolean observed(long tip) throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            if (store.permanentlyFenced(chainKey)) return false;
            Loaded<ChainRecord> rec = store.record(chainKey);
            ChainRecord c = rec.value();
            if (c == null) return !rec.exists();
            if (c.fence() != null && tip > c.fence().until()) {
                // Everything a lost journal could hold has settled by now: its module leaves the record and an unreadable
                // file is moved aside (kept, never deleted), so the module can journal again.
                Set<String> modules = new LinkedHashSet<>();
                for (String m : c.modules()) {
                    Loaded<ModuleJournal> j = store.journal(chainKey, m);
                    if (j.ok()) modules.add(m);
                    else if (j.exists()) store.setAside(chainKey, m);
                }
                for (String m : store.journalModules(chainKey)) {
                    if (store.journal(chainKey, m).lost()) store.setAside(chainKey, m);
                    else modules.add(m);
                }
                c = new ChainRecord(chain, c.mark(), modules, c.generation(), null, c.rewind(), c.registrations());
                store.writeRecord(c);
            }
            return c.fence() == null && c.rewind() == null;
        }
    }

    /** Persists a rewind's reconciliation (and a fence on building) before any host is restarted (D17 "Rewind"). */
    public void beginRewind(long tip, String reason) throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            ChainRecord c = existing();
            store.writeRecord(new ChainRecord(chain, c.mark(), c.modules(), c.generation(), c.fence(),
                    new Rewind(c.rewind() == null ? tip : Math.min(tip, c.rewind().tip()), reason), c.registrations()));
        }
    }

    /** Clears the reconciliation after the final restore response (D17). */
    public void endRewind() throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            ChainRecord c = existing();
            if (c.rewind() == null) return;
            store.writeRecord(new ChainRecord(chain, c.mark(), c.modules(), c.generation(), c.fence(), null, c.registrations()));
        }
    }

    /** Records registration evidence for the chain (one per credential; the first kept). */
    public void learn(Registration r) throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            ChainRecord c = existing();
            if (c.registrations().stream().anyMatch(x -> x.credential().equals(r.credential()))) return;
            List<Registration> next = new ArrayList<>(c.registrations());
            next.add(r);
            store.writeRecord(new ChainRecord(chain, c.mark(), c.modules(), c.generation(), c.fence(), c.rewind(), next));
        }
    }

    /** Drops evidence a rewind or a re-read disproved (ADR5-CDX-26). */
    public void forget(String credential) throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            ChainRecord c = existing();
            List<Registration> next = c.registrations().stream().filter(x -> !x.credential().equals(credential)).toList();
            if (next.size() != c.registrations().size()) {
                store.writeRecord(new ChainRecord(chain, c.mark(), c.modules(), c.generation(), c.fence(), c.rewind(), next));
            }
        }
    }

    private ChainRecord existing() throws StoreFailed {
        Loaded<ChainRecord> rec = store.record(chainKey);
        if (rec.ok()) return rec.value();
        if (rec.exists()) throw new StoreFailed("the chain record is unreadable");
        return new ChainRecord(chain, null, Set.of(), 0, null, null, List.of());
    }

    /** Every live entry on the chain, of every module (I33: admission and the panel). */
    public List<Entry> liveOnChain() throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            List<Entry> out = new ArrayList<>();
            for (var j : journals(store.record(chainKey).value()).values()) {
                if (j.ok()) for (Entry e : j.value().entries()) if (e.live()) out.add(e);
            }
            return out;
        }
    }
}
