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
 * (I25), cross-module overlap on the chain (I33), and the chain's fences and rewinds. Every operation runs under the
 * chain lock and reads the files again, and {@link #open} and {@link #admit} classify the chain's history with one
 * function (M33B1-CDX-1, -SEC-2): admission is possible only where a new host would find complete history.
 */
public final class ChainJournal {

    /** Building, signing or restoring is not possible now; the reason is shown as plain text. */
    public static final class Fenced extends Exception {
        public Fenced(String why) {
            super(why, null, false, false);
        }
    }

    /** The chain's history as a new host finds it (M33B1-DES-9). */
    public enum History {
        /** Nothing recorded for this chain. */
        NEW_CHAIN,
        /** Record, registry entry and every listed journal intact, no fence: restore and resume at once. */
        COMPLETE,
        /** Intact, but a persisted recovery fence or an outstanding rewind holds building. */
        FENCED,
        /** A listed module's journal is missing, unreadable or behind the record: fenced until a tip passes the mark. */
        LOST_JOURNAL,
        /** The record (or its registry entry) is missing, unreadable or stale: fenced until the derived bound. */
        LOST_MARK,
        /** Record and registry entry both lost while history survives: building stays blocked. */
        BOTH_LOST
    }

    /**
     * A new host's starting point: the module's surviving entries, whether the host starts with {@code restoring:true},
     * and the slot an observed tip must strictly pass before restore may complete (null: at once). A {@code permanent}
     * reason means the chain stays fenced (views and listings only).
     */
    public record Opening(List<Entry> survivors, boolean restoring, @Nullable Long completeAbove, @Nullable String permanent,
                          History history, long revision, long recordRevision, long rewinds) {
        public Opening(List<Entry> survivors, boolean restoring, @Nullable Long completeAbove, @Nullable String permanent, History history) {
            this(survivors, restoring, completeAbove, permanent, history, 0, 0, 0);
        }
    }

    /** Where the chain holds a body now (D18), to check retained inclusions at open; null when it does not. */
    @FunctionalInterface
    public interface Inclusions {
        @Nullable JournalStore.Inclusion of(String txHash) throws Exception;
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

    // ---- the one classification ---------------------------------------------------------------------------------------

    /** The chain's files, read under the lock. */
    private record Files(boolean permanent, Loaded<RegistryEntry> reg, Loaded<ChainRecord> rec, Map<String, Loaded<ModuleJournal>> journals) {
        @Nullable ChainRecord record() {
            return rec.value();
        }
    }

    private enum Kind { NEW, INTACT, LOST_JOURNALS, LOST_MARK, BOTH_LOST }

    private record Classified(Kind kind, List<String> lost, String why) {
    }

    private Files load() throws StoreFailed {
        boolean permanent = store.permanentlyFenced(chainKey);
        Loaded<RegistryEntry> reg = store.registry(chainKey);
        Loaded<ChainRecord> rec = store.record(chainKey);
        Set<String> names = new LinkedHashSet<>(store.journalModules(chainKey));
        if (rec.ok()) names.addAll(rec.value().modules().keySet());
        names.add(module);
        Map<String, Loaded<ModuleJournal>> journals = new LinkedHashMap<>();
        for (String m : names) journals.put(m, store.journal(chainKey, m));
        return new Files(permanent, reg, rec, journals);
    }

    /** Whether a journal file is evidence of history: unreadable, or holding entries or an admission. */
    private static boolean evidence(Loaded<ModuleJournal> j) {
        return j.lost() || (j.ok() && (!j.value().entries().isEmpty() || j.value().generation() > 0));
    }

    private static Classified classify(Files f) {
        if (f.permanent()) return new Classified(Kind.BOTH_LOST, List.of(), PERMANENT);
        ChainRecord c = f.record();
        boolean anyEvidence = f.journals().values().stream().anyMatch(ChainJournal::evidence);
        if (c == null) {
            if (!f.rec().exists() && !f.reg().exists() && !anyEvidence) return new Classified(Kind.NEW, List.of(), "new chain");
            if (f.reg().ok()) return new Classified(Kind.LOST_MARK, List.of(), "the chain record is missing or unreadable");
            return new Classified(Kind.BOTH_LOST, List.of(), PERMANENT);
        }
        // The registry entry vouches for the record's freshness: unreadable, or missing beside an admitted record, it
        // cannot (M33B1-SEC-1).
        if (f.reg().lost()) return new Classified(Kind.LOST_MARK, List.of(), "the chain's registry entry is unreadable");
        if (!f.reg().exists() && c.generation() >= 1) return new Classified(Kind.LOST_MARK, List.of(), "the chain's registry entry is missing");
        if (f.reg().ok() && f.reg().value().generation() > c.generation()) {
            return new Classified(Kind.LOST_MARK, List.of(), "the chain record is older than the registry");
        }
        for (var e : f.journals().entrySet()) {
            if (evidence(e.getValue()) && !c.modules().containsKey(e.getKey())) {
                return new Classified(Kind.LOST_MARK, List.of(), "a journal on this chain is not in its record");
            }
        }
        List<String> lost = new ArrayList<>();
        for (var m : c.modules().entrySet()) {
            Loaded<ModuleJournal> j = f.journals().getOrDefault(m.getKey(), Loaded.missing());
            // Every journal write bumps the module's revision in the record first (M33B1-CDX-2): a journal ahead of its
            // record shows the record is stale (M33B1-CDX-22).
            if (j.ok() && j.value().generation() > m.getValue()) {
                return new Classified(Kind.LOST_MARK, List.of(), "a journal on this chain is newer than its record");
            }
        }
        for (var m : c.modules().entrySet()) {
            Loaded<ModuleJournal> j = f.journals().getOrDefault(m.getKey(), Loaded.missing());
            // Missing, unreadable, or behind the revision the record holds for it (M33B1-CDX-2, -DES-8).
            if (!j.ok() || j.value().generation() < m.getValue()) lost.add(m.getKey());
        }
        if (!lost.isEmpty()) return new Classified(Kind.LOST_JOURNALS, lost, "a module's journal on this chain is missing, unreadable or older than its record");
        return new Classified(Kind.INTACT, List.of(), "complete");
    }

    /** The classification, with the irreversible both-lost marker persisted wherever it is found (M33B1-CDX-20). */
    /**
     * Why the chain's record cannot be trusted now (missing while a registry or journals prove history, or proven stale
     * by the registry), or null; under the chain lock (M33B1-CDX-101, -106). A lost module journal is not this: its
     * fence handles it.
     */
    public @Nullable String recordLost() throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            Classified k = judge(load());
            return k.kind() == Kind.LOST_MARK || k.kind() == Kind.BOTH_LOST ? k.why() : null;
        }
    }

    private Classified judge(Files f) throws StoreFailed {
        Classified k = classify(f);
        if (k.kind() == Kind.BOTH_LOST && !f.permanent()) store.writePermanentFence(chain, PERMANENT);
        return k;
    }

    static final String PERMANENT = "IntelliAda lost both its record and its registry entry for this chain while its history survives; "
            + "building on this chain stays blocked (views and listings still work). A recreated devnet starts afresh";

    // ---- open ---------------------------------------------------------------------------------------------------------

    /**
     * Classifies the chain's history for a new host of this module (D17 "Restart"), persisting what the classification
     * requires before anything is lifted: a lost mark's derived bound and its fence, a lost journal's fence, the both-lost
     * marker. {@code tipAtDetection} is a tip read now (a rewind before detection cannot lower the base).
     */
    public Opening open(long tipAtDetection) throws StoreFailed, InterruptedException {
        return open(tipAtDetection, null);
    }

    /**
     * As {@link #open(long)}; {@code inclusions} re-reads every retained inclusion of every module, so a fork while no
     * host ran is found even at a higher tip (M33B1-CDX-21).
     */
    public Opening open(long tipAtDetection, @Nullable Inclusions inclusions) throws StoreFailed, InterruptedException {
        return open(() -> tipAtDetection, inclusions);
    }

    /** A chain point read now (each round of an open reads it again; M33B1-CDX-66). */
    @FunctionalInterface
    public interface Tip {
        long slot() throws Exception;
    }

    /**
     * As {@link #open(long, Inclusions)}, with the tip read afresh for each round, so a chain that only advanced while
     * the inclusions were read is never taken for a rewind (M33B1-CDX-66).
     */
    public Opening open(Tip tip, @Nullable Inclusions inclusions) throws StoreFailed, InterruptedException {
        // A rewind this process could not persist is persisted before anything opens on the chain (M33B1-CDX-84). When
        // the record refuses it (history to recover first), the open recovers, then the rewind is persisted and the chain
        // opened again, so this opening reconciles it.
        if (UNSAVED.containsKey(unsavedKey())) {
            try {
                persistUnsaved();
            } catch (StoreFailed refused) {
                open1(tip, inclusions);
                persistUnsaved();
            }
        }
        return open1(tip, inclusions);
    }

    private Opening open1(Tip tip, @Nullable Inclusions inclusions) throws StoreFailed, InterruptedException {
        // Irreversible loss needs only local evidence: persisted before the first provider request (M33B1-CDX-71).
        try (var held = store.lock(chainKey)) {
            if (judge(load()).kind() == Kind.BOTH_LOST) return new Opening(List.of(), false, null, PERMANENT, History.BOTH_LOST);
        }
        long first;
        try {
            first = tip.slot();
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception unread) {
            throw new StoreFailed("the node could not be read for IntelliAda's journal (" + unread.getClass().getSimpleName() + "); start again");
        }
        long tipAtDetection = first;   // the highest tip read: a loss bound's base
        long latest = first;          // the latest, coherent with the last reads: whether anything reopened (M33B1-CDX-66)
        // The retained inclusions are read before the chain lock is taken (M33B1-DES-14): the network never holds every
        // other module's admission and settlement. A failed read fails this open only; it is not evidence of a rewind.
        if (inclusions == null) return open0(tipAtDetection, null, null);
        Map<String, JournalStore.Inclusion> now = new LinkedHashMap<>();
        Map<String, JournalStore.Inclusion> readFor = new LinkedHashMap<>();   // the recorded inclusion each answer was read against
        // An inclusion recorded after the snapshot, or recorded differently since it was read, is read (again) before the
        // open completes (M33B1-CDX-55, -CDX-66): a few rounds, then the open fails ("start again").
        for (int round = 0; round < 4; round++) {
            Map<String, JournalStore.Inclusion> recorded;
            try (var held = store.lock(chainKey)) {
                Files f = load();
                // Irreversible loss, and a lost mark the registry already proves, are persisted under this first lock,
                // before any provider read (M33B1-CDX-58, -CDX-65).
                Classified k = judge(f);
                if (k.kind() == Kind.BOTH_LOST) return new Opening(List.of(), false, null, PERMANENT, History.BOTH_LOST);
                if (k.kind() == Kind.LOST_MARK) lostMark(f, tipAtDetection, k.why());
                recorded = retainedInclusionMap(load());
            }
            Set<String> wanted = new LinkedHashSet<>();
            for (var r : recorded.entrySet()) if (!r.getValue().equals(readFor.get(r.getKey()))) wanted.add(r.getKey());
            if (wanted.isEmpty()) {
                Opening o = open0(latest, now, recorded);
                if (o != null) return o;
                continue;
            }
            for (String h : wanted) readFor.put(h, recorded.get(h));
            try {
                long t = tip.slot();
                tipAtDetection = Math.max(tipAtDetection, t);
                latest = t;
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception unread) {
                throw new StoreFailed("the node could not be read for IntelliAda's journal (" + unread.getClass().getSimpleName() + "); start again");
            }
            for (String h : wanted) {
                try {
                    now.put(h, inclusions.of(h));
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception unread) {
                    throw new StoreFailed("the node could not be read for IntelliAda's journal (" + unread.getClass().getSimpleName() + "); start again");
                }
            }
        }
        throw new StoreFailed("IntelliAda's journal kept changing while the session opened; start again");
    }

    private static Map<String, JournalStore.Inclusion> retainedInclusionMap(Files f) {
        Map<String, JournalStore.Inclusion> out = new LinkedHashMap<>();
        for (var j : f.journals().values()) {
            if (j.ok()) for (Entry e : j.value().entries()) if (e.inclusion() != null) out.put(e.txHash(), e.inclusion());
        }
        return out;
    }

    /** The open under the lock; null when the retained inclusions changed since {@code checked} was read (read them first). */
    private @Nullable Opening open0(long tipAtDetection, @Nullable Map<String, JournalStore.Inclusion> inclusions,
                                    @Nullable Map<String, JournalStore.Inclusion> checked) throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            Files f = load();
            if (checked != null && !checked.equals(retainedInclusionMap(f))) return null;
            Classified k = judge(f);   // both lost: persisted, so a restored backup cannot lift it (ADR5-SEC-19, ADR5-CDX-3)
            ChainRecord c = f.record();
            Loaded<ModuleJournal> mine = f.journals().getOrDefault(module, Loaded.missing());
            List<Entry> survivors = mine.ok() ? mine.value().entries() : List.of();
            long revision = mine.ok() ? mine.value().generation() : 0;
            lastWritten = revision;
            switch (k.kind()) {
                case BOTH_LOST -> {
                    return new Opening(List.of(), false, null, PERMANENT, History.BOTH_LOST);
                }
                case NEW -> {
                    return new Opening(List.of(), false, null, null, History.NEW_CHAIN);
                }
                case LOST_JOURNALS -> {
                    long until = Math.max(c.mark(), c.fence() == null ? 0 : c.fence().until());
                    persist(f, c.withFence(new Fence(until, k.why())));
                    return new Opening(survivors, true, until, null, History.LOST_JOURNAL, revision, held(c), c.rewinds());
                }
                case LOST_MARK -> {
                    long bound = lostMark(f, tipAtDetection, k.why());
                    ChainRecord now = store.record(chainKey).value();
                    return new Opening(survivors, true, bound, null, History.LOST_MARK, revision, held(now), now == null ? 0 : now.rewinds());
                }
                default -> {
                    // A rewind while no host ran (M33B1-CDX-21), also when an earlier one is still being reconciled
                    // (M33B1-CDX-37): any module's retained entry the tip (or the chain's present inclusions) reopens makes
                    // every module with entries reconcile again, under a new rewind id.
                    String touched = reopened(f, tipAtDetection, inclusions);
                    // Every piece of evidence the chain point gives, not only the first (M33B1-CDX-56). A rewind still being
                    // reconciled is renewed only on evidence it has not recorded, or a lower point (M33B1-CDX-50): a restore
                    // observed before a fork cannot acknowledge it, and the same evidence seen again keeps the id.
                    Set<String> evidence = evidence(f, tipAtDetection, inclusions);
                    // ... or evidence reaching a module that already left it (M33B1-CDX-78): it reconciled before this fork.
                    boolean renew = !evidence.isEmpty() && (c.rewind() == null || tipAtDetection < c.rewind().tip()
                            || !c.rewind().evidence().containsAll(evidence)
                            || touchedOutside(f, tipAtDetection, inclusions, c.rewind().pending()));
                    if (renew) {
                        Set<String> pending = pendingModules(f);
                        Set<String> known = new LinkedHashSet<>(evidence);
                        long at = tipAtDetection;
                        if (c.rewind() != null) {
                            pending.addAll(c.rewind().pending());
                            known.addAll(c.rewind().evidence());
                            at = Math.min(at, c.rewind().tip());
                        }
                        c = persistRewind(f, c, new Rewind(at, touched, pending, Rewind.newId(), known));   // M33B1-CDX-87
                    }
                    // Back at or below a recovered bound (M33B1-CDX-43): a lost journal's bodies may land again.
                    if (c.recovered() != null && tipAtDetection <= c.recovered() && c.fence() == null) {
                        c = c.withFence(new Fence(c.recovered(), "the chain is back below where IntelliAda recovered lost history"));
                        persist(f, c);
                    }
                    boolean restoring = !survivors.isEmpty() || c.fence() != null || c.rewind() != null;
                    Long above = c.fence() == null ? null : c.fence().until();
                    History h = c.fence() == null && c.rewind() == null ? History.COMPLETE : History.FENCED;
                    return new Opening(survivors, restoring, above, null, h, revision, held(c), c.rewinds());
                }
            }
        }
    }

    private long held(@Nullable ChainRecord c) {
        return c == null ? 0 : c.modules().getOrDefault(module, 0L);
    }

    /** Why {@code tip} (or the chain's present inclusions) reopens a journaled body of any module (D14's touch rules), or null. */
    /**
     * The admission's form of the touch rules: its tip is the one its body was prepared against, which may trail another
     * module's newest observation, so only a chain point more than a margin below recorded evidence counts.
     */
    private @Nullable String reopenedFar(Files f, long tip) {
        for (var j : f.journals().values()) {
            if (!j.ok()) continue;
            for (Entry e : j.value().entries()) {
                if (e.inclusion() != null && e.inclusion().slot() > tip + settlementSlots) {
                    return "the chain is at slot " + tip + ", far below a recorded inclusion";
                }
                if (e.state() == JournalStore.State.expired && e.validTo() > tip + settlementSlots) {
                    return "the chain is at slot " + tip + ", where an expired body can land again";
                }
            }
        }
        return null;
    }

    static final String FORK = "the chain no longer shows a recorded inclusion";

    /** Each entry's reopening evidence at {@code tip}, as {@code kind:txHash}. */
    private Set<String> evidence(Files f, long tip, @Nullable Map<String, JournalStore.Inclusion> inclusions) {
        Set<String> out = new java.util.TreeSet<>();
        for (var j : f.journals().values()) {
            if (!j.ok()) continue;
            for (Entry e : j.value().entries()) {
                if (e.inclusion() != null && inclusions != null && inclusions.containsKey(e.txHash())
                        && !e.inclusion().equals(inclusions.get(e.txHash()))) {
                    // The recorded block and what the chain shows now (another block, or absent): two different forks
                    // never share one key (M33B1-CDX-56).
                    JournalStore.Inclusion seen = inclusions.get(e.txHash());
                    out.add("fork:" + e.txHash() + ":" + e.inclusion().block() + ">" + (seen == null ? "absent" : seen.block()));
                }
                if (e.inclusion() != null && e.inclusion().slot() > tip) out.add("below:" + e.txHash());
                if (e.state() == JournalStore.State.confirmed && e.slot() != null && tip - e.slot() <= settlementSlots) out.add("margin:" + e.txHash());
                if (e.state() == JournalStore.State.expired && e.validTo() > tip) out.add("valid:" + e.txHash());
            }
        }
        return out;
    }

    /** Whether a module outside {@code pending} holds an entry {@code tip} reopens. */
    private boolean touchedOutside(Files f, long tip, @Nullable Map<String, JournalStore.Inclusion> inclusions, Set<String> pending) {
        for (var e : f.journals().entrySet()) {
            if (pending.contains(e.getKey()) || !e.getValue().ok()) continue;
            Files one = new Files(f.permanent(), f.reg(), f.rec(), Map.of(e.getKey(), e.getValue()));
            if (reopened(one, tip, inclusions) != null) return true;
        }
        return false;
    }

    private @Nullable String reopened(Files f, long tip, @Nullable Map<String, JournalStore.Inclusion> inclusions) {
        for (var j : f.journals().values()) {
            if (!j.ok()) continue;
            for (Entry e : j.value().entries()) {
                // Only an inclusion read and found different is evidence (an entry written since the read is not checked).
                if (e.inclusion() != null && inclusions != null && inclusions.containsKey(e.txHash())
                        && !e.inclusion().equals(inclusions.get(e.txHash()))) {
                    return FORK + " where it was";
                }
                if (e.inclusion() != null && e.inclusion().slot() > tip) return "the chain is at slot " + tip + ", below a recorded inclusion";
                if (e.state() == JournalStore.State.confirmed && e.slot() != null && tip - e.slot() <= settlementSlots) {
                    return "the chain is at slot " + tip + ", inside a final inclusion's margin";
                }
                if (e.state() == JournalStore.State.expired && e.validTo() > tip) return "the chain is at slot " + tip + ", where an expired body can land again";
            }
        }
        return null;
    }

    /** Every module with a journaled body on the chain (a rewind's pending reconciliations). */
    private Set<String> pendingModules(Files f) {
        return pendingModules(f, true);
    }

    /** Every module with journaled bodies, and this one when {@code self}. */
    private Set<String> pendingModules(Files f, boolean self) {
        Set<String> pending = new LinkedHashSet<>();
        for (var e : f.journals().entrySet()) {
            if (e.getValue().ok() && !e.getValue().value().entries().isEmpty()) pending.add(e.getKey());
        }
        if (self) pending.add(module);
        return pending;
    }

    /**
     * A conservative bound from the policy's hard ceilings (ADR5-CDX-3, -SEC-18): the base is the highest of the
     * registry's tip and {@code now} (when readable), the record's mark and fence, every journal's {@code validTo} and
     * inclusion slot, and the tip now. The record and the registry entry are rewritten with it, and the registry's
     * maxima raised to the base, so a later loss can never derive a lower bound (M33B1-CDX-3). Persisted before anything
     * is lifted.
     */
    private long lostMark(Files f, long tip, String why) throws StoreFailed {
        ChainRecord c = f.record();
        long base = tip;
        long gen = 0;
        if (f.reg().ok()) {
            base = Math.max(base, Math.max(f.reg().value().highestTip(), f.reg().value().highestNow()));
            gen = f.reg().value().generation();
        }
        Map<String, Long> modules = new LinkedHashMap<>();
        if (c != null) {
            if (c.mark() != null) base = Math.max(base, c.mark());
            if (c.fence() != null) base = Math.max(base, c.fence().until());
            gen = Math.max(gen, c.generation());
            modules.putAll(c.modules());
        }
        for (var e : f.journals().entrySet()) {
            // An unreadable journal stays listed (lost), so a restart keeps this bound rather than deriving another
            // (M33B1-CDX-23); it is set aside once the fence lifts.
            if (e.getValue().lost()) modules.merge(e.getKey(), 1L, Math::max);
            if (!e.getValue().ok()) continue;
            ModuleJournal j = e.getValue().value();
            if (j.generation() > 0 || !j.entries().isEmpty()) modules.merge(e.getKey(), Math.max(1, j.generation()), Math::max);
            gen = Math.max(gen, j.generation());
            for (Entry x : j.entries()) {
                base = Math.max(base, x.validTo());
                if (x.slot() != null) base = Math.max(base, x.slot());
            }
        }
        long bound = Math.addExact(base, TimePolicy.MAX_NOW_AHEAD + TimePolicy.LARGEST_TTL + settlementSlots);
        gen = gen + 1;
        List<Registration> regs = c == null ? List.of() : c.registrations();
        Rewind rewind = c == null ? null : c.rewind();
        // A record rebuilt without the old one counts as a rewind no host saw (M33B1-CDX-101): its count is the new
        // generation, above any count the lost record held (each count change was a record write), so every host
        // opened before the rebuild stops on the count check.
        store.writeRecord(new ChainRecord(chain, bound, modules, gen, new Fence(bound, why), rewind, regs,
                c == null ? null : c.recovered(), gen));   // also over a stale record (M33B1-CDX-106)
        store.writeRegistry(new RegistryEntry(chain, base, base, gen));
        return bound;
    }

    // ---- admission ----------------------------------------------------------------------------------------------------

    /**
     * Journals a body before it is handed out (I25, ADR5-SEC-5): under the chain lock, only where the chain's history is
     * complete (the same classification a new host gets) with no fence or rewind, after checking that no live entry of
     * any module on the chain spends, references or publishes what this body does (I33), and that the entry reads back as
     * written (M33B1-CDX-13). The mark is raised to cover {@code validTo + settlementSlots}, the registry's highest tip
     * and now are raised, the module's admission generation is bound in the record, and the entry is written; any
     * failure refuses (the caller then settles the body {@code discarded}).
     *
     * @param tip the tip the body was prepared against; {@code now} the {@code now} sent
     */
    public void admit(Entry e, long tip, long now) throws StoreFailed, Fenced, InterruptedException {
        admit(e, tip, now, -1);
    }

    /**
     * As {@link #admit(Entry, long, long)}; {@code rewindsSeen} is the chain's rewind count the admitting host started
     * (or was restored) at (−1: unchecked). A host that missed a rewind admits nothing (M33B1-CDX-34).
     */
    public void admit(Entry e, long tip, long now, long rewindsSeen) throws StoreFailed, Fenced, InterruptedException {
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
        // Within what lost-mark recovery can bound (M33B1-CDX-31): validTo at most the largest TTL past tip or now.
        if (e.validTo() > Math.max(tip, now) + TimePolicy.LARGEST_TTL) {
            throw new Fenced("the prepared transaction is valid beyond what IntelliAda's journal can recover");
        }
        if (unsavedRewind() != null) throw new Fenced("a rewind of this chain is being recorded; start the dev host again");   // M33B1-CDX-84
        try (var held = store.lock(chainKey)) {
            // Decided under the lock (M33B1-CDX-88): a rewind registered by another module before it took the lock.
            if (unsavedHere()) throw new Fenced("a rewind of this chain is being recorded; start the dev host again");
            Files f = load();
            Classified k = judge(f);
            if (k.kind() != Kind.NEW && k.kind() != Kind.INTACT) {
                throw new Fenced(k.kind() == Kind.BOTH_LOST ? PERMANENT : k.why() + "; start the session again to recover");
            }
            ChainRecord c = f.record();
            if (c != null && c.fence() != null) throw new Fenced("building waits until the chain passes slot " + c.fence().until());
            if (c != null && c.rewind() != null) throw new Fenced("a rewind on this chain is being reconciled");
            if (c != null && c.recovered() != null && tip <= c.recovered()) {
                throw new Fenced("the chain is back below where IntelliAda recovered lost history (slot " + c.recovered() + ")");
            }
            if (rewindsSeen >= 0 && (c == null ? 0 : c.rewinds()) != rewindsSeen) {
                throw new Fenced("the chain was rewound since this dev host started; start it again");
            }
            // An admission whose chain point is far below any module's recorded evidence (offline ones included) is
            // refused (M33B1-CDX-46). It records no rewind: its tip is one unconfirmed value, and chain rewinds come only
            // from chain evidence under D14's rule, at the next settlement pass (M33B1-DES-16).
            String touched = c == null ? null : reopenedFar(f, tip);
            if (touched != null) throw new Fenced(touched + "; wait for the next settlement pass and try again");
            String overlap = overlap(body, f.journals(), tip);
            if (overlap != null) throw new Fenced(overlap);
            RegistryEntry reg = f.reg().value();
            long gen = Math.max(c == null ? 0 : c.generation(), reg == null ? 0 : reg.generation()) + 1;
            long mark = Math.max(c == null || c.mark() == null ? 0 : c.mark(), Math.addExact(e.validTo(), settlementSlots));
            Loaded<ModuleJournal> current = f.journals().getOrDefault(module, Loaded.missing());
            // Above the chain generation, so a module removed and admitted again never reuses a revision an old copy of
            // its journal could match (M33B1-CDX-63).
            long revision = Math.max(Math.max(c == null ? 0 : c.modules().getOrDefault(module, 0L), current.ok() ? current.value().generation() : 0),
                    c == null ? 0 : c.generation()) + 1;
            Map<String, Long> modules = new LinkedHashMap<>(c == null ? Map.of() : c.modules());
            modules.put(module, revision);
            // The remembered recovery bound and the rewind count are kept (M33B1-CDX-43).
            ChainRecord next = new ChainRecord(chain, mark, modules, gen, null, null, c == null ? List.of() : c.registrations(),
                    c == null ? null : c.recovered(), c == null ? 0 : c.rewinds());
            RegistryEntry base = reg == null ? null : repaired(reg, c, gen);
            RegistryEntry r = new RegistryEntry(chain, Math.max(tip, base == null ? 0 : base.highestTip()),
                    Math.max(now, base == null ? 0 : base.highestNow()), gen);
            Loaded<ModuleJournal> mine = f.journals().getOrDefault(module, Loaded.missing());
            List<Entry> entries = new ArrayList<>(mine.ok() ? mine.value().entries() : List.of());
            if (entries.stream().anyMatch(x -> x.reservation().equals(e.reservation()))) throw new Fenced("the host reused a reservation id");
            entries.add(e);
            if (!store.fits(new ModuleJournal(chain, module, revision, entries))) {
                throw new Fenced("this module's journal on the chain is full");   // before any write (M33B1-CDX-13)
            }
            // The mark first, then the registry, then the entry: a crash between them over-covers (and the journal behind
            // its record's generation is then lost history, fenced until the mark).
            store.writeRecord(next);
            store.writeRegistry(r);
            store.writeJournal(new ModuleJournal(chain, module, revision, entries));
            lastWritten = revision;
        }
    }

    /**
     * Writes this module's journal under the lock, bumping its revision in the record first (M33B1-CDX-2): a journal
     * restored from before any write, a handout's included, is then behind its record. Needs an intact record.
     */
    private void writeMine(List<Entry> entries, boolean recovery) throws StoreFailed, Fenced {
        Files f = load();
        ChainRecord c = f.record();
        if (c == null) throw new Fenced("the chain record is missing or unreadable");
        Loaded<ModuleJournal> j = f.journals().getOrDefault(module, Loaded.missing());
        if (j.lost()) throw new Fenced("this module's journal on the chain is unreadable");
        // An ordinary write needs this journal current with its record (M33B1-CDX-36): a stale journal is never
        // certified by a live update. A restore's replacement is the explicit recovery write, under its persisted fence.
        long held = c.modules().getOrDefault(module, 0L), have = j.ok() ? j.value().generation() : 0;
        // The one exception: the journal is exactly the revision this instance last wrote, the record ahead only by this
        // instance's own failed writes since.
        boolean ours = failedAt >= 0 && have == lastWritten && held == failedAt;
        if (!recovery && held != have && !ours) throw new Fenced("this module's journal is not current with its record; start the session again to recover");
        Kind kind = judge(f).kind();
        if (kind == Kind.LOST_MARK || kind == Kind.BOTH_LOST) throw new Fenced("the chain's history is incomplete; start the session again to recover");
        long next = Math.max(Math.max(held, have), c.generation()) + 1;
        if (!store.fits(new ModuleJournal(chain, module, next, entries))) throw new Fenced("this module's journal on the chain is full");   // M33B1-CDX-74
        long revision = next;
        Map<String, Long> modules = new LinkedHashMap<>(c.modules());
        modules.put(module, revision);
        // The chain's generation rises with every write, in the record and then the registry entry, as at admission
        // (M33B1-SEC-7): a record and journal restored together from before any write (an approval turned submitted)
        // are then behind the registry entry, a lost mark.
        RegistryEntry reg = f.reg().value();
        if (reg == null) throw new Fenced("the chain's registry entry is missing or unreadable");
        long gen = Math.max(c.generation(), reg.generation()) + 1;
        store.writeRecord(c.with(c.mark(), modules, gen, c.fence(), c.rewind(), c.registrations()));
        try {
            // The registry and the journal after the record: a failure of either is this instance's known failed write,
            // which the same owner may finish later (M33B1-CDX-60).
            store.writeRegistry(repaired(reg, c, gen));
            store.writeJournal(new ModuleJournal(chain, module, revision, entries));
        } catch (StoreFailed failed) {
            failedAt = revision;   // the record holds this revision; the journal stays at lastWritten (a known failed write)
            throw failed;
        }
        lastWritten = revision;
        failedAt = -1;
    }

    /** The record revision this instance's last failed journal write left (−1: none). */
    private volatile long failedAt = -1;

    /** The revision this instance last wrote (or read when it opened the chain); −1 before either. */
    private volatile long lastWritten = -1;

    /** Why {@code body} conflicts with a live entry on the chain, or null (I33; protocol D8's reference rule). */
    private @Nullable String overlap(TxCbor.Body body, Map<String, Loaded<ModuleJournal>> all, long tip) {
        Set<String> spends = new HashSet<>(body.inputs());
        spends.addAll(body.collateral());
        for (var j : all.values()) {
            if (!j.ok()) continue;
            for (Entry x : j.value().entries()) {
                // Every journaled body holds its inputs until it leaves the journal, final and expired ones included
                // (M33B1-CDX-46, -61, -64): any of them a rewind could reopen, at whatever tip the admission saw. Only a
                // discarded body (never handed out) releases them. The cost: an expired body's inputs wait out its
                // retention before another body may spend them (§12 E10; about 17 minutes on a devnet, 1.45 h on a public
                // network). The refusal says which transaction holds the input and until when (M33B1-DES-18, -DES-19).
                if (x.state() == JournalStore.State.discarded) continue;
                String holder = holder(x);
                TxCbor.Body b;
                try {
                    b = x.body();
                } catch (TxCbor.Malformed m) {
                    return "a journaled transaction cannot be read";
                }
                if (b.txHash().equals(body.txHash())) return "the same transaction is already journaled on this chain";
                Set<String> theirs = new HashSet<>(b.inputs());
                theirs.addAll(b.collateral());
                for (String s : spends) if (theirs.contains(s)) return "an input is reserved by " + holder;
                for (String s : body.referenceInputs()) if (theirs.contains(s)) {
                    return "a reference input is being spent by " + holder;
                }
                for (String p : body.publishes()) if (b.publishes().contains(p)) {
                    return holder + " already publishes " + p;
                }
            }
        }
        return null;
    }

    // ---- entries ------------------------------------------------------------------------------------------------------

    /**
     * This module's entries, read now. An unreadable journal reads as none here (its history is fenced by the
     * classification and it is set aside once that fence lifts; M33B1-CDX-10); writes to it are refused.
     */
    public List<Entry> entries() throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            Loaded<ModuleJournal> j = store.journal(chainKey, module);
            return j.ok() ? j.value().entries() : List.of();
        }
    }

    private ModuleJournal mine() throws StoreFailed, Fenced {
        Loaded<ModuleJournal> j = store.journal(chainKey, module);
        if (j.lost()) throw new Fenced("this module's journal on the chain is unreadable");
        return j.ok() ? j.value() : new ModuleJournal(chain, module, 0, List.of());
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
                    writeMine(next, false);
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Moves a journaled body's handout forward under the chain lock, on intact history with no fence or rewind
     * (M33B1-CDX-45): approval pending to submitted or exported. {@code tip} is a chain point read now: at or below a
     * recovered bound, nothing is handed out (M33B1-CDX-46).
     */
    public void handout(String reservation, JournalStore.Handout h) throws StoreFailed, Fenced, InterruptedException {
        handout(reservation, h, Long.MAX_VALUE);
    }

    public void handout(String reservation, JournalStore.Handout h, long tip) throws StoreFailed, Fenced, InterruptedException {
        try (var held = store.lock(chainKey)) {
            Files f = load();
            Kind k = judge(f).kind();
            ChainRecord c = f.record();
            if (k != Kind.INTACT || c == null || c.fence() != null || c.rewind() != null || unsavedHere()) {   // M33B1-CDX-89
                throw new Fenced("the chain's history is not complete now; nothing is handed out");
            }
            if (c.recovered() != null && tip <= c.recovered()) {
                throw new Fenced("the chain is back below where IntelliAda recovered lost history (slot " + c.recovered() + ")");
            }
            ModuleJournal j = mine();
            List<Entry> next = new ArrayList<>(j.entries());
            for (int i = 0; i < next.size(); i++) {
                Entry e = next.get(i);
                if (!e.reservation().equals(reservation)) continue;
                if (e.handout() != JournalStore.Handout.approvalPending && e.handout() != h) {
                    throw new Fenced("a " + e.handout() + " body cannot become " + h);
                }
                next.set(i, e.withHandout(h));
                writeMine(next, false);
                return;
            }
            throw new Fenced("the body is not journaled");
        }
    }

    /** Removes entries (a body declined before handout, or a final entry past its retention). */
    public void remove(Set<String> reservations) throws StoreFailed, Fenced, InterruptedException {
        remove(reservations, -1);
    }

    /**
     * As {@link #remove(Set)}, only under the chain's rewind count {@code rewindsSeen} and with no rewind open
     * (M33B1-CDX-68): a pass that observed the chain before a rewind never prunes a body that rewind must restore.
     * Returns false (nothing removed) otherwise.
     */
    public boolean remove(Set<String> reservations, long rewindsSeen) throws StoreFailed, Fenced, InterruptedException {
        try (var held = store.lock(chainKey)) {
            if (rewindsSeen >= 0) {
                ChainRecord c = store.record(chainKey).value();
                if (c == null || c.rewind() != null || c.rewinds() != rewindsSeen || unsavedHere()) return false;   // M33B1-CDX-90
            }
            removeLocked(reservations);
            return true;
        }
    }

    private void removeLocked(Set<String> reservations) throws StoreFailed, Fenced {
        {
            ModuleJournal j = mine();
            List<Entry> next = j.entries().stream().filter(e -> !reservations.contains(e.reservation())).toList();
            if (next.size() != j.entries().size()) writeMine(next, false);
        }
    }

    /**
     * Replaces the module's journal after a restore (D17: settlement then uses the ids {@code restore} returned); ids must
     * be distinct. An absent or unreadable journal replaced by nothing is left as it is (no empty file is made, which a
     * later classification would read as a module the record does not list; M33B1-CDX-10, -CDX-11).
     */
    public void replace(List<Entry> entries) throws StoreFailed, Fenced, InterruptedException {
        replace(entries, -1);
    }

    /**
     * As {@link #replace(List)}, only over the journal revision the opening read ({@code expected}; −1: any): a session
     * that ended cannot overwrite its successor's writes (M33B1-CDX-44).
     */
    public void replace(List<Entry> entries, long expected) throws StoreFailed, Fenced, InterruptedException {
        replace(entries, expected, -1, null);
    }

    /**
     * The restore's recovery write, bound to its opening (M33B1-CDX-36): the journal and the record revision it read, and,
     * for a fenced recovery, the fence that authorized it still persisted.
     */
    public void replace(List<Entry> entries, Opening o) throws StoreFailed, Fenced, InterruptedException {
        replace(entries, o.revision(), o.recordRevision(), o.completeAbove());
    }

    private void replace(List<Entry> entries, long expected, long recordRevision, @Nullable Long fence) throws StoreFailed, Fenced, InterruptedException {
        Set<String> ids = new HashSet<>();
        for (Entry e : entries) if (!ids.add(e.reservation())) throw new IllegalArgumentException("restored ids repeat");
        try (var held = store.lock(chainKey)) {
            Loaded<ModuleJournal> j = store.journal(chainKey, module);
            if (expected >= 0 && (j.ok() ? j.value().generation() : 0) != expected) {
                throw new Fenced("this module's journal changed since the session opened it");
            }
            ChainRecord c = store.record(chainKey).value();
            if (recordRevision >= 0 && held(c) != recordRevision) throw new Fenced("the chain record changed since the session opened it");
            if (fence != null && (c == null || c.fence() == null || c.fence().until() < fence)) {
                throw new Fenced("the recovery fence this restore relies on is gone; start the session again");
            }
            if (entries.isEmpty() && !j.ok()) return;
            if (j.lost()) throw new Fenced("this module's journal on the chain is unreadable");
            writeMine(entries, true);
        }
    }

    /** Which journaled transaction holds an input, and until when (the end of its retention for a final one). */
    private String holder(Entry x) {
        String what = switch (x.state()) {
            case prepared -> "a pending transaction";
            case shallow -> "a transaction included but not yet final";
            case confirmed -> "a confirmed transaction";
            case expired -> "an expired transaction";
            default -> "a transaction";
        };
        String label = x.label().isEmpty() ? "" : " (" + x.label() + ")";
        if (!x.live()) {
            long point = x.state() == JournalStore.State.confirmed && x.slot() != null ? Math.max(x.slot(), x.validTo()) : x.validTo();
            return what + label + " on this chain until slot " + (point + 2 * settlementSlots + TimePolicy.MAX_NOW_AHEAD);
        }
        return what + label + " on this chain until it settles";
    }

    /**
     * Whether a final entry has outlived its retention at {@code tip} (§12 E10): {@code 2 × settlementSlots +
     * maxNowAheadSlots} past the later of its inclusion and its {@code validTo} (M33B1-DES-5).
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
        return tip > point + 2 * settlementSlots + TimePolicy.MAX_NOW_AHEAD;
    }

    // ---- fences, rewinds, registrations -------------------------------------------------------------------------------

    /** Whether the chain's history is complete now (the classification admission needs). */
    public boolean intact() throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            Kind k = judge(load()).kind();
            return k == Kind.INTACT || k == Kind.NEW;
        }
    }

    /** The chain record now, or null when there is none (or it is unreadable). */
    public @Nullable ChainRecord record() throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            return store.record(chainKey).value();
        }
    }

    /**
     * Clears the recovery fence once an observed tip is strictly above it (D17; ADR5-CDX-3). Everything a lost or stale
     * journal could hold has settled by then: a missing module leaves the record, an unreadable file is moved aside (kept,
     * never deleted), and a journal behind its record is accepted at its own generation. Returns whether building is
     * unfenced afterwards (no fence, no rewind, not permanent, history intact).
     */
    public boolean observed(long tip) throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            Files f = load();
            Classified now = judge(f);
            if (now.kind() == Kind.BOTH_LOST) return false;
            // New evidence of a lost mark is persisted as its own bound before any older fence is lifted (M33B1-CDX-22).
            if (now.kind() == Kind.LOST_MARK) {
                lostMark(f, tip, now.why());
                return false;
            }
            ChainRecord c = f.record();
            if (c == null) return false;
            // Newly lost journals: a fence covering the current mark first (M33B1-CDX-54); an older, lower fence never
            // releases them.
            if (now.kind() == Kind.LOST_JOURNALS && c.mark() != null && (c.fence() == null || c.fence().until() < c.mark())) {
                persist(f, c.withFence(new Fence(c.mark(), now.why())));
                return false;
            }
            if (c.fence() != null && tip > c.fence().until()) {
                Map<String, Long> modules = new LinkedHashMap<>();
                for (var m : c.modules().entrySet()) {
                    Loaded<ModuleJournal> j = f.journals().getOrDefault(m.getKey(), Loaded.missing());
                    if (j.ok()) modules.put(m.getKey(), Math.max(1, j.value().generation()));
                    else if (j.exists()) store.setAside(chainKey, m.getKey());
                }
                for (var e : f.journals().entrySet()) {
                    if (e.getValue().lost() && !c.modules().containsKey(e.getKey())) store.setAside(chainKey, e.getKey());
                }
                long recovered = Math.max(c.recovered() == null ? 0 : c.recovered(), c.fence().until());
                c = c.withModules(modules).withFence(null).withRecovered(recovered);
                persist(f, c);
                f = load();
            }
            Classified k = classify(f);
            return k.kind() == Kind.INTACT && c.fence() == null && c.rewind() == null;
        }
    }

    /**
     * Persists a rewind's reconciliation before any host is restarted (D17 "Rewind"; M33B1-CDX-5): every module with a
     * journaled body on the chain must reconcile on a fresh host before the rewind clears, and admission stays closed
     * meanwhile. Other hosts of the chain see it in the record and stop.
     */
    /**
     * A rewind detected in this process and not yet persisted, per store and chain (M33B1-CDX-84, -87): the whole rewind
     * (its tip, reason, pending modules and evidence), registered before the chain lock is taken and before any write,
     * cleared once persisted. Admission, handout and pruning check it under the chain lock (M33B1-CDX-88..90); every
     * host of the chain stops on it; the next open persists it.
     */
    private record Unsaved(Rewind rewind, Set<String> tokens) {
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Unsaved> UNSAVED = new java.util.concurrent.ConcurrentHashMap<>();

    /** Per store and chain: one process can hold several stores (tests). */
    private String unsavedKey() {
        return store.root() + "|" + chainKey;
    }

    private boolean unsavedHere() {
        return UNSAVED.containsKey(unsavedKey());
    }

    private static Rewind union(Rewind a, @Nullable Rewind b) {
        if (b == null) return a;
        Set<String> pending = new LinkedHashSet<>(a.pending());
        pending.addAll(b.pending());
        Set<String> evidence = new LinkedHashSet<>(a.evidence());
        evidence.addAll(b.evidence());
        return new Rewind(Math.min(a.tip(), b.tip()), a.reason(), pending, Rewind.newId(), evidence);
    }

    /**
     * Registers {@code r} (merged with any unsaved rewind) in this process before anything is written, under
     * {@code token}: whoever persists the merged rewind consumes every token in it (M33B1-CDX-104).
     */
    private Unsaved register(Rewind r, String token) {
        return UNSAVED.compute(unsavedKey(), (k, u) -> {
            Set<String> tokens = new LinkedHashSet<>(u == null ? Set.of() : u.tokens());
            tokens.add(token);
            return new Unsaved(u == null ? r : union(r, u.rewind()), tokens);
        });
    }

    /**
     * Under the chain lock: persists {@code r} merged with this process's unsaved rewind, registered first, so a failed
     * write keeps it in memory (M33B1-CDX-87). Returns the record written.
     */
    private ChainRecord persistRewind(Files f, ChainRecord c, Rewind r) throws StoreFailed {
        Unsaved u = register(r, Rewind.newId());
        ChainRecord next = c.withNewRewind(u.rewind());
        persist(f, next);
        UNSAVED.remove(unsavedKey(), u);
        return next;
    }

    /**
     * Under the lock: when the record already holds the unsaved rewind {@code u}, the write that failed was the
     * registry's (M33B1-CDX-100, -109). On a new chain there is no registry yet: it is written at the record's
     * generation, as the failed write would have; otherwise both are written again. Returns whether it applied.
     */
    private boolean completeRecorded(Files f, Unsaved u) throws StoreFailed {
        ChainRecord c = f.rec().ok() ? f.rec().value() : null;
        if (c == null || c.rewind() == null || !c.rewind().id().equals(u.rewind().id())) return false;
        if (f.reg().value() == null && !f.reg().lost()) store.writeRegistry(new RegistryEntry(chain, 0, 0, c.generation()));
        else persist(f, c);
        UNSAVED.remove(unsavedKey(), u);
        return true;
    }

    /** Persists this process's unsaved rewind of the chain, if any; StoreFailed when it still cannot be written. */
    private void persistUnsaved() throws StoreFailed, InterruptedException {
        if (UNSAVED.get(unsavedKey()) == null) return;
        try (var held = store.lock(chainKey)) {
            // Read again under the lock (M33B1-CDX-99): a retry that ran meanwhile consumed it.
            Unsaved u = UNSAVED.get(unsavedKey());
            if (u == null) return;
            Files f = load();
            ChainRecord c = existing(f);
            if (completeRecorded(f, u)) return;
            // The modules it already named and those with journaled bodies; never the caller for asking (M33B1-CDX-94).
            Set<String> pending = new LinkedHashSet<>(u.rewind().pending());
            pending.addAll(pendingModules(f, false));
            persistRewind(f, c, union(new Rewind(u.rewind().tip(), u.rewind().reason(), pending, Rewind.newId(), u.rewind().evidence()), c.rewind()));
        }
    }

    /**
     * A rewind of this chain that this process detected and has not yet persisted, retried now (M33B1-CDX-84): its
     * reason while one was pending (persisted by this retry or not), or null. Every host of the chain stops on it, and
     * admission refuses until it is persisted.
     */
    /** The reason of this process's unsaved rewind of the chain, or null: a query, with no write (M33B1-DES-24). */
    public @Nullable String unsavedReason() {
        Unsaved u = UNSAVED.get(unsavedKey());
        return u == null ? null : u.rewind().reason();
    }

    public @Nullable String unsavedRewind() throws InterruptedException {
        Unsaved u = UNSAVED.get(unsavedKey());
        if (u == null) return null;
        try {
            persistUnsaved();
        } catch (StoreFailed e) {
            // still unsaved; refused meanwhile
        }
        return u.rewind().reason();
    }

    /**
     * Persists a rewind's reconciliation before any host is restarted (D17 "Rewind"; M33B1-CDX-5): every module with a
     * journaled body on the chain, and this one, must reconcile on a fresh host before the rewind clears, and admission
     * stays closed meanwhile. Other hosts of the chain see it and stop. Held in this process before the lock is taken
     * (M33B1-CDX-84, -88).
     */
    public void beginRewind(long tip, String reason) throws StoreFailed, InterruptedException {
        String token = Rewind.newId();
        register(new Rewind(tip, reason, new LinkedHashSet<>(Set.of(module)), Rewind.newId(), Set.of()), token);
        try (var held = store.lock(chainKey)) {
            // Persisted meanwhile by a retry, which carried this registration (M33B1-CDX-104): recorded once.
            Unsaved cur = UNSAVED.get(unsavedKey());
            if (cur == null || !cur.tokens().contains(token)) return;
            Files f0 = load();
            // Its record written by a helper whose registry write failed (M33B1-CDX-109): completed, never renamed.
            if (completeRecorded(f0, cur)) return;
            Files f = f0;
            ChainRecord c = existing(f);
            // A new id each time (M33B1-CDX-5): a restore observed before this rewind cannot acknowledge it.
            persistRewind(f, c, union(new Rewind(tip, reason, pendingModules(f, true), Rewind.newId(), Set.of()), c.rewind()));
        }
    }

    /**
     * This module reconciled rewind {@code id} (the final restore response of a host restored after it): it leaves that
     * rewind, which clears once none is pending. A different (newer) rewind is untouched (M33B1-CDX-5).
     */
    public void endRewind(String id) throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            Files f = load();
            ChainRecord c = existing(f);
            if (c.rewind() == null || !c.rewind().id().equals(id) || !c.rewind().pending().contains(module)) return;
            Set<String> pending = new LinkedHashSet<>(c.rewind().pending());
            pending.remove(module);
            persist(f, c.withRewind(pending.isEmpty() ? null   // the evidence stays with the rewind (M33B1-CDX-72)
                    : new Rewind(c.rewind().tip(), c.rewind().reason(), pending, c.rewind().id(), c.rewind().evidence())));
        }
    }

    /** Records registration evidence for the chain (one per credential; the first kept). */
    public void learn(Registration r) throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            Files f = load();
            ChainRecord c = existing(f);
            if (c.registrations().stream().anyMatch(x -> x.credential().equals(r.credential()))) return;
            List<Registration> next = new ArrayList<>(c.registrations());
            next.add(r);
            persist(f, c.withRegistrations(next));
        }
    }

    /** Drops evidence a rewind or a re-read disproved (ADR5-CDX-26). */
    public void forget(String credential) throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            Files f = load();
            ChainRecord c = existing(f);
            List<Registration> next = c.registrations().stream().filter(x -> !x.credential().equals(credential)).toList();
            if (next.size() != c.registrations().size()) persist(f, c.withRegistrations(next));
        }
    }

    /**
     * Writes the chain record, raising the chain generation in it and then in the registry entry (M33B1-CDX-57): every
     * record change, a rewind's or a fence's included, is then one a restored older record is visibly behind. A registry
     * entry behind the record is repaired first, its maxima raised to the record's mark, so it never carries a lower base
     * forward under a fresh generation (M33B1-CDX-59).
     */
    private void persist(Files f, ChainRecord c) throws StoreFailed {
        // A record the registry proves stale is never advanced past it (M33B1-CDX-62): the session must recover first.
        Kind kind = classify(f).kind();
        if (kind == Kind.LOST_MARK || kind == Kind.BOTH_LOST) {
            throw new StoreFailed("the chain's history is incomplete; start the session again to recover");
        }
        RegistryEntry reg = f.reg().value();
        if (reg == null) {
            // A new chain's first record change (a rewind before any admission) gets its registry entry too, so a restored
            // older record is behind it (M33B1-CDX-67).
            long gen = c.generation() + 1;
            store.writeRecord(c.with(c.mark(), c.modules(), gen, c.fence(), c.rewind(), c.registrations()));
            store.writeRegistry(new RegistryEntry(chain, 0, 0, gen));
            return;
        }
        long gen = Math.max(c.generation(), reg.generation()) + 1;
        store.writeRecord(c.with(c.mark(), c.modules(), gen, c.fence(), c.rewind(), c.registrations()));
        store.writeRegistry(repaired(reg, c, gen));
    }

    /** {@code reg} at generation {@code gen}; when it was behind {@code c}, its maxima raised to {@code c}'s mark. */
    private RegistryEntry repaired(RegistryEntry reg, @Nullable ChainRecord c, long gen) {
        long tip = reg.highestTip(), now = reg.highestNow();
        if (c != null && reg.generation() < c.generation() && c.mark() != null) {
            tip = Math.max(tip, c.mark());
            now = Math.max(now, c.mark());
        }
        return new RegistryEntry(chain, tip, now, gen);
    }

    /** The record, or a fresh one only for a new chain; any other state refuses (and both-lost is persisted; M33B1-CDX-20). */
    private ChainRecord existing(Files f) throws StoreFailed {
        if (f.rec().ok()) return f.rec().value();
        Classified k = judge(f);
        if (k.kind() == Kind.NEW) return new ChainRecord(chain, null, Map.of(), 0, null, null, List.of());
        throw new StoreFailed("the chain record is missing or unreadable (" + k.why() + ")");
    }

    /** Every live entry on the chain, of every module (I33: admission and the panel). */
    public List<Entry> liveOnChain() throws StoreFailed, InterruptedException {
        try (var held = store.lock(chainKey)) {
            List<Entry> out = new ArrayList<>();
            for (var j : load().journals().values()) {
                if (j.ok()) for (Entry e : j.value().entries()) if (e.live()) out.add(e);
            }
            return out;
        }
    }
}
