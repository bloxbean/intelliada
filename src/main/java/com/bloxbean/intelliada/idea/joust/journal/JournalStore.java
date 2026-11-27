package com.bloxbean.intelliada.idea.joust.journal;

import com.bloxbean.intelliada.idea.joust.host.HostJson;
import com.bloxbean.intelliada.idea.joust.tx.TxCbor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * ADR-005 D17's files (M3.3b1), under one root per user ({@link #forUser}), never in a project or an IDE's own directories:
 * <pre>
 *   registry/&lt;chain&gt;.json                 the chain's registry entry: highest tip and now, the admission generation
 *   chains/&lt;chain&gt;/record.json             the chain record: mark, module list, fences, reconciliation, registrations
 *   chains/&lt;chain&gt;/&lt;module&gt;.journal.json   one module's journal
 *   chains/&lt;chain&gt;/fenced.json             a chain whose record and registry entry were both lost (no override)
 *   chains/&lt;chain&gt;/lock                    the chain's file lock
 * </pre>
 * {@code <chain>} and {@code <module>} are SHA-256 prefixes of the chain identity (D14) and the module's real path. The
 * registry lives apart from the chain's directory so that each can be lost or replaced without the other (ADR5-CDX-24).
 * Every write goes to a fresh temporary file, is forced to disk, renamed over the old file, and the directory forced
 * where the platform allows; directories are this user's and private, files private. Loads are strict: a file of
 * another version, with an unknown field, or with an invalid value is {@link Loaded#unreadable unreadable}, never empty.
 * Nothing here holds key material.
 */
public final class JournalStore {

    public static final int VERSION = 1;
    static final Pattern RESERVATION = Pattern.compile("[\\x21-\\x7e]{1,128}");
    private static final Pattern HASH32 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern HASH28 = Pattern.compile("[0-9a-f]{56}");
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{32}");
    static final long MAX_FILE = 64L * 1024 * 1024;

    /** A store failure: nothing it guards may proceed. */
    public static final class StoreFailed extends Exception {
        public StoreFailed(String why) {
            super(why, null, false, false);
        }

        public StoreFailed(String why, Throwable cause) {
            super(why, cause, false, false);
        }
    }

    /** A file's state on load: absent, unreadable (exists but is not one this version writes), or a value. */
    public record Loaded<T>(@Nullable T value, boolean exists) {
        static <T> Loaded<T> missing() {
            return new Loaded<>(null, false);
        }

        static <T> Loaded<T> unreadable() {
            return new Loaded<>(null, true);
        }

        public boolean ok() {
            return value != null;
        }

        public boolean lost() {
            return exists && value == null;
        }
    }

    // ---- the values ---------------------------------------------------------------------------------------------------

    public enum Handout { none, approvalPending, submitted, exported }

    public enum State { prepared, shallow, confirmed, expired, discarded }

    /**
     * One journaled body (ADR5-CDX-16): the host's reservation id, the body as handed out, its validity read from the
     * body itself, the handout, the node configuration that bound it (metadata only), the reservation state as the host
     * returned it, the events IntelliAda observed, and a short label.
     */
    public record Entry(String reservation, String txHash, String cbor, @Nullable Long validFrom, long validTo, Handout handout,
                        String node, State state, @Nullable Long slot, @Nullable Inclusion inclusion, List<String> events, String label) {
        public Entry {
            events = List.copyOf(events);
        }

        public boolean live() {
            return state == State.prepared || state == State.shallow;
        }

        /** The host's state; {@code at} is the inclusion slot of a shallow or confirmed reservation. */
        public Entry withState(State s, @Nullable Long at) {
            // The inclusion evidence stays with the entry until it is cleared on purpose (a reported rollback; M33B1-CDX-48).
            return new Entry(reservation, txHash, cbor, validFrom, validTo, handout, node, s, at, inclusion, events, label);
        }

        /** The chain's evidence of the body's inclusion, as IntelliAda observed it (null: not seen on chain). */
        public Entry withInclusion(@Nullable Inclusion i) {
            return new Entry(reservation, txHash, cbor, validFrom, validTo, handout, node, state, slot, i, events, label);
        }

        public Entry withReservation(String id) {
            return new Entry(id, txHash, cbor, validFrom, validTo, handout, node, state, slot, inclusion, events, label);
        }

        public Entry withHandout(Handout h) {
            return new Entry(reservation, txHash, cbor, validFrom, validTo, h, node, state, slot, inclusion, events, label);
        }

        public Entry withEvent(String e) {
            List<String> next = new ArrayList<>(events);
            next.add(e);
            if (next.size() > 64) next = next.subList(next.size() - 64, next.size());
            return new Entry(reservation, txHash, cbor, validFrom, validTo, handout, node, state, slot, inclusion, next, label);
        }

        /** The body's facts, read again from its bytes. */
        public TxCbor.Body body() throws TxCbor.Malformed {
            return TxCbor.body(TxCbor.transaction(cbor));
        }
    }

    /** Where the chain showed a body: its slot and the block (height and hash) holding it. */
    public record Inclusion(long slot, long height, String block) {
    }

    /** A module's journal on one chain. */
    public record ModuleJournal(String chain, String module, long generation, List<Entry> entries) {
        public ModuleJournal {
            entries = List.copyOf(entries);
        }
    }

    /** A recovery fence: building waits until an observed tip is strictly above {@code until}. */
    public record Fence(long until, String reason) {
    }

    /** A rewind's outstanding reconciliation, persisted before any host is restarted (D17 "Rewind"). */
    public record Rewind(long tip, String reason, Set<String> pending, String id, Set<String> evidence) {
        public Rewind {
            pending = Set.copyOf(pending);
            evidence = Set.copyOf(evidence);
        }

        public Rewind(long tip, String reason, Set<String> pending, String id) {
            this(tip, reason, pending, id, Set.of());
        }

        /** A new rewind's unique id (M33B1-CDX-5): a host acknowledges only the rewind it was restored after. */
        public static String newId() {
            return HexFormat.of().formatHex(random(8));
        }
    }

    /**
     * Registration evidence (D15, ADR5-CDX-10): a credential with the registering transaction and exactly one of its
     * inclusion slot or the node's duplicate-registration message.
     */
    public record Registration(String credential, String txHash, @Nullable Long slot, @Nullable String message) {
    }

    /**
     * The chain record: the high-water mark ({@code max(validTo + settlementSlots)} over handed-out bodies), every module
     * that ever held a handed-out body here with the generation of its journal's last admission (a journal behind it is
     * lost history; M33B1-CDX-2, -DES-8), the admission generation, the recovery fence and the outstanding rewind.
     */
    public record ChainRecord(String chain, @Nullable Long mark, Map<String, Long> modules, long generation, @Nullable Fence fence,
                              @Nullable Rewind rewind, List<Registration> registrations, @Nullable Long recovered, long rewinds) {
        public ChainRecord {
            modules = Map.copyOf(modules);
            registrations = List.copyOf(registrations);
        }

        public ChainRecord(String chain, @Nullable Long mark, Map<String, Long> modules, long generation, @Nullable Fence fence,
                           @Nullable Rewind rewind, List<Registration> registrations) {
            this(chain, mark, modules, generation, fence, rewind, registrations, null, 0);
        }

        ChainRecord with(@Nullable Long mark, Map<String, Long> modules, long generation, @Nullable Fence fence, @Nullable Rewind rewind,
                         List<Registration> registrations) {
            return new ChainRecord(chain, mark, modules, generation, fence, rewind, registrations, recovered, rewinds);
        }

        /**
         * A new rewind recorded (M33B1-CDX-34): every rewind raises the chain's rewind count, and a host admits only at
         * the count it was started (or restored) at, so one that missed a rewind must restart.
         */
        public ChainRecord withNewRewind(Rewind r) {
            return new ChainRecord(chain, mark, modules, generation, fence, r, registrations, recovered, rewinds + 1);
        }

        /**
         * The highest recovery fence ever lifted on this chain (M33B1-CDX-43): a chain back at or below it may hold bodies
         * a lost journal held, so it is fenced again.
         */
        public ChainRecord withRecovered(@Nullable Long r) {
            return new ChainRecord(chain, mark, modules, generation, fence, rewind, registrations, r, rewinds);
        }

        public ChainRecord withFence(@Nullable Fence f) {
            return with(mark, modules, generation, f, rewind, registrations);
        }

        public ChainRecord withRewind(@Nullable Rewind r) {
            return with(mark, modules, generation, fence, r, registrations);
        }

        public ChainRecord withModules(Map<String, Long> m) {
            return with(mark, m, generation, fence, rewind, registrations);
        }

        public ChainRecord withRegistrations(List<Registration> r) {
            return with(mark, modules, generation, fence, rewind, r);
        }
    }

    /** The registry entry: the highest tip and {@code now} ever observed or sent on the chain, and the generation. */
    public record RegistryEntry(String chain, long highestTip, long highestNow, long generation) {
    }

    // ---- the store ----------------------------------------------------------------------------------------------------

    private final Path root;
    private static final Map<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    public JournalStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * The store for this user, independent of the IDE product and version (M33B1-DES-20): {@code ~/.intelliada/joust-journal},
     * beside the legacy account cache. An IDE's system directory belongs to one product and version and is not migrated on
     * upgrade, so a store there would lose every handed-out body's record on an ordinary update, and two IDE versions
     * side by side would not see each other's locks.
     */
    public static JournalStore forUser() {
        return new JournalStore(Path.of(System.getProperty("user.home"), ".intelliada", "joust-journal"));
    }

    public Path root() {
        return root;
    }

    public static String key(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 32);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    Path chainDir(String chainKey) {
        return root.resolve("chains").resolve(chainKey);
    }

    Path registryFile(String chainKey) {
        return root.resolve("registry").resolve(chainKey + ".json");
    }

    Path recordFile(String chainKey) {
        return chainDir(chainKey).resolve("record.json");
    }

    Path journalFile(String chainKey, String moduleKey) {
        return chainDir(chainKey).resolve(moduleKey + ".journal.json");
    }

    Path fencedFile(String chainKey) {
        return chainDir(chainKey).resolve("fenced.json");
    }

    /** The chain lock: in this process, then the file lock across IDE processes; bounded wait. */
    public interface Held extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Directories whose entry changed (a rename) but whose sync failed (M33B1-CDX-122): every lock taken afterwards
     * syncs them first, and fails while one cannot be, so no operation reports success over a change that is not durable.
     */
    private static final java.util.concurrent.ConcurrentHashMap<Path, Set<String>> UNSYNCED = new java.util.concurrent.ConcurrentHashMap<>();

    /** Records {@code dir} as owed a sync for the chain the written file belongs to (M33B1-CDX-128, -130). */
    private void unsynced(Path dir, Path file) {
        String name = file.getFileName().toString();
        String chain = name.endsWith(".json") && dir.getFileName() != null && dir.getFileName().toString().equals("registry")
                ? name.substring(0, name.length() - 5) : chainOf(dir);
        Path real;
        try {
            real = dir.toRealPath();
        } catch (IOException e) {
            real = Path.of(identity()).resolve(root.relativize(dir.toAbsolutePath().normalize()));
        }
        // Every chain whose rename it holds, until one successful sync covers them all (M33B1-CDX-134).
        UNSYNCED.computeIfAbsent(real, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(chain == null ? "" : chain);
    }

    /** The chain a store directory belongs to ({@code chains/<key>/...}), or null for a shared one. */
    private @Nullable String chainOf(Path dir) {
        Path rel = root.toAbsolutePath().normalize().relativize(dir.toAbsolutePath().normalize());
        return rel.getNameCount() >= 2 && rel.getName(0).toString().equals("chains") ? rel.getName(1).toString() : null;
    }

    private void syncPending(String chainKey) throws StoreFailed {
        Path me = Path.of(identity());
        for (var en : List.copyOf(UNSYNCED.entrySet())) {
            Path d = en.getKey();
            // This store's, and this chain's or shared (M33B1-CDX-128, -130).
            if (!d.startsWith(me) || !(en.getValue().contains("") || en.getValue().contains(chainKey))) continue;
            try {
                if (failSyncsForTests) throw new IOException("a test's sync failure");
                forceDir(d);
                UNSYNCED.remove(d);
            } catch (NoSuchFileException gone) {
                UNSYNCED.remove(d);
            } catch (IOException e) {
                throw new StoreFailed("a journal directory cannot be made durable (" + e.getClass().getSimpleName() + ")", e);
            }
        }
    }

    /** Tests: directory syncs fail while set. */
    volatile boolean failSyncsForTests;

    /**
     * The store's physical identity (M33B1-CDX-128): the real path of its deepest existing ancestor, with the rest of
     * the root's components after it. It is the same for every spelling of one store, before and after the root exists.
     */
    public String identity() {
        Path p = root.toAbsolutePath().normalize();
        java.util.Deque<Path> rest = new java.util.ArrayDeque<>();
        while (p != null) {
            try {
                Path real = p.toRealPath();
                for (Path r : rest) real = real.resolve(r);
                return real.toString();
            } catch (IOException e) {
                if (p.getFileName() != null) rest.addFirst(p.getFileName());
                p = p.getParent();
            }
        }
        return root.toAbsolutePath().normalize().toString();
    }

    public Held lock(String chainKey) throws StoreFailed, InterruptedException {
        requireKey(chainKey);
        // Keyed by the directory's real path: two spellings of one store share one in-process lock, so this JVM never
        // opens a second descriptor of a lock file it holds (closing one would drop the OS lock; M33B1-CDX-4).
        String real;
        try {
            privateDir(chainDir(chainKey));
            real = chainDir(chainKey).toRealPath().toString();
        } catch (IOException e) {
            throw new StoreFailed("the chain's journal directory cannot be used (" + e.getClass().getSimpleName() + ")", e);
        }
        ReentrantLock in = LOCKS.computeIfAbsent(real, k -> new ReentrantLock());
        if (!in.tryLock(30, TimeUnit.SECONDS)) throw new StoreFailed("the chain's journal is busy");
        if (in.getHoldCount() > 1) {
            // Re-entered by the same thread: the file lock is already held; pending syncs are still owed (M33B1-CDX-129).
            try {
                syncPending(chainKey);
            } catch (StoreFailed e) {
                in.unlock();
                throw e;
            }
            return in::unlock;
        }
        FileChannel ch = null;
        try {
            ch = FileChannel.open(chainDir(chainKey).resolve("lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            FileLock fl;
            while ((fl = tryFileLock(ch)) == null) {
                if (System.nanoTime() > end) throw new StoreFailed("another IDE holds the chain's journal");
                Thread.sleep(50);
            }
            syncPending(chainKey);   // nothing proceeds over a change that is not yet durable (M33B1-CDX-122)
            FileChannel channel = ch;
            FileLock lock = fl;
            return () -> {
                try {
                    lock.release();
                } catch (IOException ignored) {
                    // the channel's close releases it
                }
                try {
                    channel.close();
                } catch (IOException ignored) {
                    // nothing more to release
                }
                in.unlock();
            };
        } catch (IOException e) {
            closeQuietly(ch);
            in.unlock();
            throw new StoreFailed("the chain's journal cannot be locked (" + e.getClass().getSimpleName() + ")", e);
        } catch (StoreFailed | InterruptedException | RuntimeException e) {
            closeQuietly(ch);
            in.unlock();
            throw e;
        }
    }

    /**
     * I33: the one live host of a module on a chain, across IDE processes. Held for the host session's life; refused at
     * once when another process (or another session in this one) holds it.
     */
    public Held ownModule(String chainKey, String moduleKey) throws StoreFailed {
        requireKey(chainKey);
        requireKey(moduleKey);
        String real;
        try {
            privateDir(chainDir(chainKey));
            real = chainDir(chainKey).toRealPath().resolve(moduleKey + ".host").toString();
        } catch (IOException e) {
            // The reason kept (M33B1-CDX-86): a loose parent's chmod hint reaches the panel.
            throw new StoreFailed("the module's host lock cannot be taken: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
        // Reserved in this JVM before any descriptor is opened: a refused second owner never opens (and so never closes)
        // a descriptor of the live owner's lock file, which would release its OS lock (M33B1-CDX-4).
        if (OWNED.putIfAbsent(real, Boolean.TRUE) != null) {
            throw new StoreFailed("another dev host of this module is running on this chain (in this IDE)");
        }
        FileChannel ch = null;
        try {
            ch = FileChannel.open(Path.of(real), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock fl = tryFileLock(ch);
            if (fl == null) throw new StoreFailed("another dev host of this module is running on this chain (in another IDE)");
            FileChannel channel = ch;
            java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
            return () -> {
                if (!closed.compareAndSet(false, true)) return;
                try {
                    fl.release();
                } catch (IOException ignored) {
                    // closing the channel releases it
                }
                closeQuietly(channel);
                OWNED.remove(real);
            };
        } catch (IOException e) {
            closeQuietly(ch);
            OWNED.remove(real);
            throw new StoreFailed("the module's host lock cannot be taken (" + e.getClass().getSimpleName() + ")", e);
        } catch (StoreFailed | RuntimeException e) {
            closeQuietly(ch);
            OWNED.remove(real);
            throw e;
        }
    }

    private static final Map<String, Boolean> OWNED = new ConcurrentHashMap<>();
    /** Directories whose entry in their parent this process has synced. */
    private static final Set<String> DURABLE = ConcurrentHashMap.newKeySet();

    /**
     * Syncs {@code dir}'s entry in its parent once per process; recorded only after the sync succeeded, and a concurrent
     * writer waits for it, so no write is acknowledged above a sync still in progress or one that failed (M33B1-CDX-15).
     */
    private static void durable(Path dir) throws IOException {
        if (DURABLE.contains(dir.toString())) return;
        synchronized (DURABLE) {
            if (DURABLE.contains(dir.toString())) return;
            forceDir(dir.getParent());
            DURABLE.add(dir.toString());
        }
    }

    /** The user's home (the store's ancestors are synced up to it). */
    private static Path homeDir() {
        return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
    }

    private static @Nullable FileLock tryFileLock(FileChannel ch) throws IOException {
        try {
            return ch.tryLock();
        } catch (OverlappingFileLockException e) {
            return null;
        }
    }

    private static void closeQuietly(@Nullable FileChannel ch) {
        if (ch == null) return;
        try {
            ch.close();
        } catch (IOException ignored) {
            // closing only
        }
    }

    private static void requireKey(String k) {
        if (!KEY.matcher(k).matches()) throw new IllegalArgumentException("not a store key: " + k);
    }

    // ---- loads --------------------------------------------------------------------------------------------------------

    public Loaded<RegistryEntry> registry(String chainKey) throws StoreFailed {
        JsonNode n = read(registryFile(chainKey));
        if (n == null) return Loaded.missing();
        try {
            fields(n, "version", "chain", "highestTip", "highestNow", "generation");
            version(n);
            String chain = text(n, "chain");
            if (!key(chain).equals(chainKey)) throw new Bad();   // M33B1-SEC-5
            return new Loaded<>(new RegistryEntry(chain, slot(n, "highestTip"), slot(n, "highestNow"), slot(n, "generation")), true);
        } catch (Bad b) {
            return Loaded.unreadable();
        }
    }

    /** A rewind's evidence items the reader accepts: three per journaled entry, far above what any file limit lets in. */
    static final int MAX_EVIDENCE = 1_000_000;

    public Loaded<ChainRecord> record(String chainKey) throws StoreFailed {
        JsonNode n = read(recordFile(chainKey));
        if (n == null) return Loaded.missing();
        return parseRecord(n, chainKey);
    }

    private Loaded<ChainRecord> parseRecord(JsonNode n, String chainKey) {
        try {
            // Two fields are optional (a record written before them reads with their defaults; M33B1-CDX-52).
            fieldsOptional(n, Set.of("recovered", "rewinds"), "version", "chain", "mark", "modules", "generation", "fence", "rewind",
                    "registrations", "recovered", "rewinds");
            version(n);
            Map<String, Long> modules = new java.util.LinkedHashMap<>();
            JsonNode ms = n.get("modules");
            if (ms == null || !ms.isObject()) throw new Bad();
            for (var it = ms.fields(); it.hasNext(); ) {
                var m = it.next();
                if (!KEY.matcher(m.getKey()).matches() || !m.getValue().isIntegralNumber() || !m.getValue().canConvertToLong()
                        || !m.getValue().canConvertToLong() || m.getValue().longValue() < 1) throw new Bad();
                modules.put(m.getKey(), m.getValue().longValue());
            }
            Fence fence = null;
            JsonNode f = n.get("fence");
            if (f == null) throw new Bad();
            if (!f.isNull()) {
                fields(f, "until", "reason");
                fence = new Fence(slot(f, "until"), boundedText(f, "reason"));
            }
            Rewind rewind = null;
            JsonNode r = n.get("rewind");
            if (r == null) throw new Bad();
            if (!r.isNull()) {
                fieldsOptional(r, Set.of("evidence"), "tip", "reason", "pending", "id", "evidence");
                Set<String> pending = new LinkedHashSet<>();
                JsonNode ps = r.get("pending");
                if (!ps.isArray()) throw new Bad();
                for (JsonNode x : ps) {
                    if (!x.isTextual() || !KEY.matcher(x.textValue()).matches() || !pending.add(x.textValue())) throw new Bad();
                }
                String id = text(r, "id");
                if (!id.matches("[0-9a-f]{16}")) throw new Bad();
                Set<String> evidence = new LinkedHashSet<>();
                JsonNode ev = r.get("evidence");
                if (ev != null) {
                    if (!ev.isArray() || ev.size() > MAX_EVIDENCE) throw new Bad();
                    for (JsonNode x : ev) {
                        if (!x.isTextual() || x.textValue().length() > 200) throw new Bad();
                        evidence.add(x.textValue());
                    }
                }
                rewind = new Rewind(slot(r, "tip"), boundedText(r, "reason"), pending, id, evidence);
            }
            List<Registration> regs = new ArrayList<>();
            JsonNode rs = n.get("registrations");
            if (rs == null || !rs.isArray()) throw new Bad();
            Set<String> creds = new java.util.HashSet<>();
            for (JsonNode x : rs) {
                Registration g = registration(x);
                if (!creds.add(g.credential())) throw new Bad();
                regs.add(g);
            }
            String chain = text(n, "chain");
            if (!key(chain).equals(chainKey)) throw new Bad();   // M33B1-SEC-5
            Long mark = nullableSlot(n, "mark");
            long generation = slot(n, "generation");
            // A record that lists handouts has a mark, and no module is ahead of the record (M33B1-CDX-18).
            if (!modules.isEmpty() && mark == null) throw new Bad();
            Long recovered = n.has("recovered") ? nullableSlot(n, "recovered") : null;
            long rewinds = n.has("rewinds") ? slot(n, "rewinds") : 0;
            return new Loaded<>(new ChainRecord(chain, mark, modules, generation, fence, rewind, regs, recovered, rewinds), true);
        } catch (Bad b) {
            return Loaded.unreadable();
        }
    }

    public Loaded<ModuleJournal> journal(String chainKey, String moduleKey) throws StoreFailed {
        requireKey(moduleKey);
        JsonNode n = read(journalFile(chainKey, moduleKey));
        if (n == null) return Loaded.missing();
        try {
            fields(n, "version", "chain", "module", "generation", "entries");
            version(n);
            if (!moduleKey.equals(text(n, "module")) || !key(text(n, "chain")).equals(chainKey)) throw new Bad();
            long generation = slot(n, "generation");
            JsonNode es = n.get("entries");
            if (es == null || !es.isArray()) throw new Bad();
            List<Entry> entries = new ArrayList<>();
            Set<String> ids = new java.util.HashSet<>();
            for (JsonNode e : es) {
                Entry x = entry(e);
                if (!ids.add(x.reservation())) throw new Bad();
                entries.add(x);
            }
            return new Loaded<>(new ModuleJournal(text(n, "chain"), moduleKey, generation, entries), true);
        } catch (Bad b) {
            return Loaded.unreadable();
        }
    }

    /** Every module journal file present in the chain's directory (listed in the record or not). */
    public List<String> journalModules(String chainKey) throws StoreFailed {
        Path dir = chainDir(chainKey);
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) return out;
        try (var s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                if (n.endsWith(".journal.json") && KEY.matcher(n.substring(0, n.length() - ".journal.json".length())).matches()) {
                    out.add(n.substring(0, n.length() - ".journal.json".length()));
                } else if (n.endsWith(".journal.json")) {
                    throw new StoreFailed("an unexpected journal file in the chain's directory: " + n);
                }
            }
        } catch (IOException e) {
            throw new StoreFailed("the chain's directory cannot be listed (" + e.getClass().getSimpleName() + ")", e);
        }
        out.sort(null);
        return out;
    }

    /** Whether the chain carries the both-lost fence (ADR5-SEC-19): only a recreated devnet lifts it. */
    public boolean permanentlyFenced(String chainKey) throws StoreFailed {
        return read(fencedFile(chainKey)) != null || Files.exists(fencedFile(chainKey), LinkOption.NOFOLLOW_LINKS);
    }

    // ---- writes -------------------------------------------------------------------------------------------------------

    public void writeRegistry(RegistryEntry r) throws StoreFailed {
        ObjectNode n = HostJson.object().put("version", VERSION).put("chain", r.chain()).put("highestTip", r.highestTip())
                .put("highestNow", r.highestNow()).put("generation", r.generation());
        write(registryFile(key(r.chain())), n);
    }

    public void writeRecord(ChainRecord c) throws StoreFailed {
        ObjectNode n = HostJson.object().put("version", VERSION).put("chain", c.chain());
        if (c.mark() == null) n.putNull("mark");
        else n.put("mark", c.mark());
        ObjectNode ms = n.putObject("modules");
        c.modules().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> ms.put(e.getKey(), e.getValue()));
        n.put("generation", c.generation());
        if (c.fence() == null) n.putNull("fence");
        else n.putObject("fence").put("until", c.fence().until()).put("reason", c.fence().reason());
        if (c.rewind() == null) n.putNull("rewind");
        else {
            ObjectNode r = n.putObject("rewind").put("tip", c.rewind().tip()).put("reason", c.rewind().reason()).put("id", c.rewind().id());
            ArrayNode ps = r.putArray("pending");
            c.rewind().pending().stream().sorted().forEach(ps::add);
            ArrayNode es = r.putArray("evidence");
            c.rewind().evidence().stream().sorted().forEach(es::add);
        }
        if (c.recovered() == null) n.putNull("recovered");
        else n.put("recovered", c.recovered());
        n.put("rewinds", c.rewinds());
        ArrayNode rs = n.putArray("registrations");
        for (Registration g : c.registrations()) {
            ObjectNode o = rs.addObject().put("credential", g.credential()).put("txHash", g.txHash());
            if (g.slot() != null) o.put("slot", g.slot());
            else o.put("message", g.message());
        }
        // The record its reader would load, or nothing replaces the readable one (M33B1-CDX-70).
        try {
            JsonNode back = HostJson.MAPPER.readTree(HostJson.MAPPER.writeValueAsBytes(n));
            if (!parseRecord(back, key(c.chain())).ok()) throw new StoreFailed("the chain record would not read back; it was not written");
        } catch (IOException e) {
            throw new StoreFailed("the chain record could not be serialized", e);
        }
        write(recordFile(key(c.chain())), n);
    }

    /** Whether {@code j} fits the loader's file limit when written (checked before any write it depends on; M33B1-CDX-13). */
    public boolean fits(ModuleJournal j) {
        try {
            ObjectNode n = HostJson.object().put("version", VERSION).put("chain", j.chain()).put("module", j.module()).put("generation", j.generation());
            ArrayNode es = n.putArray("entries");
            for (Entry e : j.entries()) es.add(json(e));
            return HostJson.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(n).length + 1 <= MAX_FILE;
        } catch (IOException e) {
            return false;
        }
    }

    public void writeJournal(ModuleJournal j) throws StoreFailed {
        ObjectNode n = HostJson.object().put("version", VERSION).put("chain", j.chain()).put("module", j.module())
                .put("generation", j.generation());
        ArrayNode es = n.putArray("entries");
        for (Entry e : j.entries()) es.add(json(e));
        write(journalFile(key(j.chain()), j.module()), n);
    }

    public void writePermanentFence(String chain, String reason) throws StoreFailed {
        write(fencedFile(key(chain)), HostJson.object().put("version", VERSION).put("chain", chain).put("reason", reason));
    }

    /** Moves an unreadable journal aside (renamed, never deleted) once the fence over its history has cleared. */
    void setAside(String chainKey, String moduleKey) throws StoreFailed {
        Path f = journalFile(chainKey, moduleKey);
        try {
            Files.move(f, f.resolveSibling(f.getFileName() + ".lost-" + System.currentTimeMillis()), StandardCopyOption.ATOMIC_MOVE);
            try {
                forceDir(f.getParent());
            } catch (IOException e) {
                unsynced(f.getParent(), f);
                throw e;
            }
        } catch (NoSuchFileException gone) {
            // nothing to move
        } catch (IOException e) {
            throw new StoreFailed("an unreadable journal cannot be moved aside (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    /** Removes a recreated devnet's old state (only ever called for a positively recreated devnet, D14). */
    public void forget(String chain) throws StoreFailed {
        String k = key(chain);
        try {
            Path dir = chainDir(k);
            if (Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                try (var s = Files.list(dir)) {
                    for (Path p : (Iterable<Path>) s::iterator) {
                        String name = p.getFileName().toString();
                        // Locks stay: a new inode would let a second owner in beside a running host (M33B1-SEC-5).
                        if (!name.equals("lock") && !name.endsWith(".host")) Files.deleteIfExists(p);
                    }
                }
            }
            Files.deleteIfExists(registryFile(k));
        } catch (IOException e) {
            throw new StoreFailed("the chain's old state cannot be removed (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    static ObjectNode json(Entry e) {
        ObjectNode o = HostJson.object().put("reservation", e.reservation()).put("txHash", e.txHash()).put("cbor", e.cbor());
        if (e.validFrom() == null) o.putNull("validFrom");
        else o.put("validFrom", e.validFrom());
        o.put("validTo", e.validTo()).put("handout", e.handout().name()).put("node", e.node()).put("state", e.state().name());
        if (e.slot() == null) o.putNull("slot");
        else o.put("slot", e.slot());
        if (e.inclusion() == null) o.putNull("inclusion");
        else o.putObject("inclusion").put("slot", e.inclusion().slot()).put("height", e.inclusion().height()).put("block", e.inclusion().block());
        ArrayNode ev = o.putArray("events");
        e.events().forEach(ev::add);
        o.put("label", e.label());
        return o;
    }

    // ---- strict parsing -----------------------------------------------------------------------------------------------

    private static final class Bad extends Exception {
        Bad() {
            super(null, null, false, false);
        }
    }

    private static void fieldsOptional(JsonNode n, Set<String> optional, String... allowed) throws Bad {
        if (!n.isObject()) throw new Bad();
        Set<String> a = Set.of(allowed);
        for (var it = n.fieldNames(); it.hasNext(); ) if (!a.contains(it.next())) throw new Bad();
        for (String f : allowed) if (!optional.contains(f) && !n.has(f)) throw new Bad();
    }

    private static void fields(JsonNode n, String... allowed) throws Bad {
        if (!n.isObject()) throw new Bad();
        Set<String> a = Set.of(allowed);
        for (var it = n.fieldNames(); it.hasNext(); ) if (!a.contains(it.next())) throw new Bad();
        for (String f : allowed) if (!n.has(f)) throw new Bad();
    }

    private static void version(JsonNode n) throws Bad {
        JsonNode v = n.get("version");
        // Exactly this version: no narrowing of a larger integer onto it (M33B1-CDX-81).
        if (v == null || !v.isIntegralNumber() || !v.canConvertToInt() || v.intValue() != VERSION) throw new Bad();
    }

    private static String text(JsonNode n, String f) throws Bad {
        JsonNode v = n.get(f);
        if (v == null || !v.isTextual() || v.textValue().isEmpty() || v.textValue().length() > 512) throw new Bad();
        return v.textValue();
    }

    private static String boundedText(JsonNode n, String f) throws Bad {
        JsonNode v = n.get(f);
        if (v == null || !v.isTextual() || v.textValue().length() > 1000) throw new Bad();
        return v.textValue();
    }

    private static long slot(JsonNode n, String f) throws Bad {
        JsonNode v = n.get(f);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToLong() || v.longValue() < 0) throw new Bad();
        return v.longValue();
    }

    private static @Nullable Long nullableSlot(JsonNode n, String f) throws Bad {
        JsonNode v = n.get(f);
        if (v == null) throw new Bad();
        return v.isNull() ? null : slot(n, f);
    }

    private static Registration registration(JsonNode x) throws Bad {
        if (!x.isObject()) throw new Bad();
        boolean bySlot = x.has("slot");
        if (bySlot == x.has("message")) throw new Bad();
        if (bySlot) fields(x, "credential", "txHash", "slot");
        else fields(x, "credential", "txHash", "message");
        String c = text(x, "credential"), h = text(x, "txHash");
        if (!HASH28.matcher(c).matches() || !HASH32.matcher(h).matches()) throw new Bad();
        if (bySlot) return new Registration(c, h, slot(x, "slot"), null);
        String m = text(x, "message");
        if (m.length() > 500) throw new Bad();
        return new Registration(c, h, null, m);
    }

    /** An entry, its body read again: its hash and validity must be the ones recorded (as the playground's journal). */
    static Entry entry(JsonNode e) throws Bad {
        fields(e, "reservation", "txHash", "cbor", "validFrom", "validTo", "handout", "node", "state", "slot", "inclusion", "events", "label");
        String id = text(e, "reservation");
        if (!RESERVATION.matcher(id).matches()) throw new Bad();
        String hash = text(e, "txHash");
        if (!HASH32.matcher(hash).matches()) throw new Bad();
        JsonNode cb = e.get("cbor");
        if (!cb.isTextual() || cb.textValue().isEmpty() || cb.textValue().length() % 2 != 0 || cb.textValue().length() > 2 * MAX_BODY_BYTES
                || !cb.textValue().matches("[0-9a-f]+")) throw new Bad();
        Handout handout;
        State state;
        try {
            handout = Handout.valueOf(text(e, "handout"));
            state = State.valueOf(text(e, "state"));
        } catch (IllegalArgumentException x) {
            throw new Bad();
        }
        Long from = nullableSlot(e, "validFrom"), slot = nullableSlot(e, "slot");
        long to = slot(e, "validTo");
        if ((state == State.shallow || state == State.confirmed) != (slot != null)) throw new Bad();
        Inclusion inclusion = null;
        JsonNode in = e.get("inclusion");
        if (!in.isNull()) {
            fields(in, "slot", "height", "block");
            String block = text(in, "block");
            if (!HASH32.matcher(block).matches()) throw new Bad();
            inclusion = new Inclusion(slot(in, "slot"), slot(in, "height"), block);
        }
        List<String> events = new ArrayList<>();
        JsonNode ev = e.get("events");
        if (!ev.isArray() || ev.size() > 64) throw new Bad();
        for (JsonNode x : ev) {
            if (!x.isTextual() || x.textValue().length() > 200) throw new Bad();
            events.add(x.textValue());
        }
        String node = boundedText(e, "node"), label = boundedText(e, "label");
        if (label.length() > 200) throw new Bad();
        TxCbor.Body b;
        try {
            b = TxCbor.body(TxCbor.transaction(cb.textValue()));
        } catch (TxCbor.Malformed m) {
            throw new Bad();
        }
        if (!b.txHash().equals(hash) || b.validTo() == null || b.validTo() != to || !java.util.Objects.equals(b.validFrom(), from)) throw new Bad();
        return new Entry(id, hash, cb.textValue(), from, to, handout, node, state, slot, inclusion, events, label);
    }

    /**
     * The largest body the journal holds (M33B1-SEC-3): four times Conway's 16 KiB maximum transaction size, so a body
     * a node could accept always fits, and the store's strings stay far inside the JSON reader's limits.
     */
    public static final int MAX_BODY_BYTES = 64 * 1024;

    /**
     * Whether {@code e} reads back exactly as written (M33B1-CDX-13): admission checks a candidate entry with the
     * loader's own rules before anything is written, so a journal never holds an entry its next load refuses.
     */
    public static boolean readsBack(Entry e) {
        try {
            return entry(HostJson.MAPPER.readTree(HostJson.MAPPER.writeValueAsBytes(json(e)))).equals(e);
        } catch (Bad | IOException x) {
            return false;
        }
    }

    // ---- files --------------------------------------------------------------------------------------------------------

    /** The file's JSON, or null when it does not exist; an existing file that cannot be read fails (never empty). */
    /**
     * On an ACL file system, before a file's bytes are trusted (M33B1-CDX-97, -98): every directory from the root to the
     * file, and the file itself, passes {@link #aclPrivate}. Access there is not governed by the path (traversal is not
     * checked), so a private root does not make what is under it private. On POSIX the 0700 root does.
     */
    /** The directories above a file to be read, verified before the file is looked at (M33B1-CDX-111). */
    private void trustedDirectories(@Nullable Path dir) throws StoreFailed {
        if (dir == null) return;
        try {
            realDirectories(dir);
            Path parent = root.getParent();
            if (parent == null || posix(parent)) return;
            guardParent(parent);
            for (Path p = dir; p != null && p.startsWith(root); p = p.getParent()) {
                try {
                    aclPrivate(p);
                } catch (NoSuchFileException absent) {
                    // not created yet
                }
            }
        } catch (IOException e) {
            throw new StoreFailed("the journal is not private: " + e.getMessage(), e);
        }
    }

    private void readable(Path f) throws IOException, StoreFailed {
        Path dir = f.getParent();
        if (dir == null || posix(dir)) return;
        try {
            aclPrivate(f);
        } catch (java.nio.file.AccessDeniedException denied) {
            throw denied;   // an unreadable leaf in verified directories is lost history (M33B1-CDX-105)
        } catch (IOException e) {
            throw new StoreFailed("the journal is not private: " + e.getMessage(), e);
        }
    }

    /**
     * Each directory from the root down to {@code dir} is a real directory (M33B1-CDX-103): no symbolic link, junction
     * or other reparse point, and it resolves to itself under the root's real path.
     */
    private void realDirectories(Path dir) throws IOException {
        try {
            Files.readAttributes(root, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException absent) {
            return;
        }
        guardParent(root.getParent());   // before the root's real path is derived through it (M33B1-CDX-107)
        Path realRoot = root.getParent() == null ? root.toRealPath() : root.getParent().toRealPath().resolve(root.getFileName());
        if (!root.toRealPath().equals(realRoot)) throw new IOException("the journal's root resolves elsewhere: " + root);
        for (Path p = dir; p != null && p.startsWith(root); p = p.getParent()) {
            // Throwing reads (M33B1-CDX-111): only a missing component is skipped; one that cannot be read refuses.
            java.nio.file.attribute.BasicFileAttributes a;
            try {
                a = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (NoSuchFileException absent) {
                continue;
            }
            if (a.isSymbolicLink() || a.isOther() || !a.isDirectory()) throw new IOException("a journal directory is a link or junction: " + p);
            if (!p.toRealPath().equals(realRoot.resolve(root.relativize(p)))) throw new IOException("a journal directory resolves elsewhere: " + p);
        }
    }

    private @Nullable JsonNode read(Path f) throws StoreFailed {
        byte[] b;
        try {
            trustedDirectories(f.getParent());   // before the leaf is interpreted, present or not (M33B1-CDX-111)
            // A throwing no-follow read (M33B1-CDX-108): missing and denied are told apart, never both "absent".
            var fa = Files.readAttributes(f, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (fa.isSymbolicLink() || fa.isOther()) throw new StoreFailed("a journal file is a link: " + f.getFileName());
            readable(f);
            if (Files.size(f) > MAX_FILE) return HostJson.object().put("$tooLarge", true);
            b = Files.readAllBytes(f);
        } catch (NoSuchFileException e) {
            return null;
        } catch (java.nio.file.AccessDeniedException e) {
            // A file that exists but stays unreadable is lost history, never a storage failure that blocks recovery
            // (M33B1-CDX-10); it is set aside once the fence over it lifts.
            return HostJson.object().put("$denied", true);
        } catch (IOException e) {
            throw new StoreFailed("a journal file cannot be read (" + e.getClass().getSimpleName() + "): " + f, e);
        }
        try {
            JsonNode n = HostJson.MAPPER.readTree(b);
            return n == null ? HostJson.object().put("$empty", true) : n;
        } catch (IOException e) {
            return HostJson.object().put("$notJson", true);   // unreadable: never an empty file
        }
    }

    /** Tests: writes to the files this matches fail, as a full disk would. */
    java.util.function.Predicate<Path> failWritesForTests = p -> false;

    private void write(Path f, JsonNode value) throws StoreFailed {
        if (failWritesForTests.test(f)) throw new StoreFailed("a journal file cannot be written (a test's failure): " + f.getFileName());
        Path dir = f.getParent();
        Path tmp = null;
        try {
            privateDir(dir);
            byte[] bytes = (HostJson.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n").getBytes(StandardCharsets.UTF_8);
            // Never write what the loader would refuse (M33B1-CDX-13).
            if (bytes.length > MAX_FILE) throw new StoreFailed("a journal file would exceed " + (MAX_FILE >> 20) + " MiB: " + f.getFileName());
            tmp = dir.resolve(f.getFileName() + "." + HexFormat.of().formatHex(random(6)) + ".tmp");
            try (FileChannel ch = FileChannel.open(tmp, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    posix(dir) ? new java.nio.file.attribute.FileAttribute<?>[]{
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))}
                            : new java.nio.file.attribute.FileAttribute<?>[0])) {
                var buf = java.nio.ByteBuffer.wrap(bytes);
                while (buf.hasRemaining()) ch.write(buf);
                ch.force(true);
            }
            try {
                Files.move(tmp, f, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new StoreFailed("the journal's file system cannot replace files atomically", e);
            }
            tmp = null;
            try {
                if (failSyncsForTests) throw new IOException("a test's sync failure");
                forceDir(dir);
            } catch (IOException e) {
                unsynced(dir, f);   // retried before the next locked operation on its chain (M33B1-CDX-122, -130)
                throw e;
            }
        } catch (IOException e) {
            throw new StoreFailed("a journal file cannot be written (" + e.getClass().getSimpleName() + "): " + f.getFileName(), e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // left for the next write's directory
                }
            }
        }
    }

    private static byte[] random(int n) {
        byte[] b = new byte[n];
        new java.security.SecureRandom().nextBytes(b);
        return b;
    }

    private static boolean posix(Path p) {
        return p.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    /** Forces a directory's entries to disk; a failure is a write failure (M33B1-CDX-15). Windows cannot open one. */
    private static void forceDir(Path dir) throws IOException {
        if (!posix(dir)) return;
        try (FileChannel ch = FileChannel.open(dir, StandardOpenOption.READ)) {
            ch.force(true);
        }
    }

    /** This process's own user, as the file system reports it for a file this process creates (M33B1-SEC-5). */
    private static volatile java.nio.file.attribute.UserPrincipal me;

    private static java.nio.file.attribute.UserPrincipal me() throws IOException {
        if (me == null) {
            Path probe = Files.createTempFile("intelliada-owner", ".probe");
            try {
                me = Files.getOwner(probe, LinkOption.NOFOLLOW_LINKS);
            } finally {
                Files.deleteIfExists(probe);
            }
        }
        return me;
    }

    /**
     * Creates {@code dir} (and its parents up to the root) private to this user; an existing one must be a real
     * directory owned by this user, and is made private.
     */
    /**
     * The root's parent decides the root's name (M33B1-CDX-80): it must be a real directory, owned by this user, that no
     * other user can write. One of this user's that others can write (a umask of 002 on Linux; M33B1-DES-22) loses its
     * group and other write bits: the legacy account cache sharing {@code ~/.intelliada} needs only the owner's.
     */
    static void guardParent(@Nullable Path parent) throws IOException {
        if (parent == null) return;
        // No-follow attributes: a junction reports a directory and "other" (M33B1-CDX-107).
        var pa = Files.readAttributes(parent, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (pa.isSymbolicLink() || pa.isOther()) throw new IOException("the journal's parent directory " + parent + " is a symbolic link or junction");
        if (!pa.isDirectory()) throw new IOException("the journal's parent " + parent + " is not a directory");
        if (!posix(parent)) {
            aclPrivate(parent);   // ACL-backed file systems (Windows; M33B1-CDX-85)
            return;
        }
        PosixFileAttributeView v = Files.getFileAttributeView(parent, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        var a = v.readAttributes();
        if (!a.owner().equals(me())) throw new IOException("the journal's parent directory " + parent + " belongs to another user");
        if (a.permissions().contains(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE)
                || a.permissions().contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE)) {
            Set<java.nio.file.attribute.PosixFilePermission> tight = java.util.EnumSet.copyOf(a.permissions());
            tight.remove(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE);
            tight.remove(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE);
            try {
                v.setPermissions(tight);
            } catch (IOException e) {
                throw new IOException("other users can write " + parent + "; run chmod go-w on it to use IntelliAda's journal", e);
            }
            var again = v.readAttributes().permissions();
            if (again.contains(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE) || again.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE)) {
                throw new IOException("other users can write " + parent + "; run chmod go-w on it to use IntelliAda's journal");
            }
        }
    }

    /** Tests: creates the root's private directory chain as a first write would. */
    void admitStoreCheckForTests() throws IOException {
        privateDir(root);
    }

    /**
     * On an ACL file system: the parent belongs to this user and grants no other principal the right to change its
     * entries, apart from the system and administrators (who can anyway). Where neither POSIX nor ACL attributes exist,
     * the privacy cannot be established and the store refuses (M33B1-CDX-85).
     */
    static void aclPrivate(Path dir) throws IOException {
        java.nio.file.attribute.AclFileAttributeView v = Files.getFileAttributeView(dir, java.nio.file.attribute.AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (v == null) throw new IOException("the privacy of " + dir + " cannot be verified on this file system");
        var lookup = dir.getFileSystem().getUserPrincipalLookupService();
        // This process's own identity, as the POSIX branch takes it (M33B1-SEC-14): never a name that can resolve elsewhere.
        aclCheck(dir, v.getOwner(), me(), v.getAcl(), trustedPrincipals(lookup));
    }

    private static final Set<java.nio.file.attribute.AclEntryPermission> ACL_WRITES = java.util.EnumSet.of(
            java.nio.file.attribute.AclEntryPermission.ADD_FILE, java.nio.file.attribute.AclEntryPermission.ADD_SUBDIRECTORY,
            java.nio.file.attribute.AclEntryPermission.DELETE_CHILD, java.nio.file.attribute.AclEntryPermission.DELETE,
            java.nio.file.attribute.AclEntryPermission.WRITE_DATA, java.nio.file.attribute.AclEntryPermission.APPEND_DATA,
            java.nio.file.attribute.AclEntryPermission.WRITE_ACL, java.nio.file.attribute.AclEntryPermission.WRITE_OWNER);

    /**
     * The rule, by principal identity (M33B1-CDX-91): on Windows a principal's equality is its security identifier, so a
     * domain group that merely shares a well-known name is not trusted.
     */
    static void aclCheck(Path dir, java.nio.file.attribute.UserPrincipal owner, java.nio.file.attribute.UserPrincipal me,
                         List<java.nio.file.attribute.AclEntry> acl, Set<java.nio.file.attribute.UserPrincipal> trusted) throws IOException {
        if (!owner.equals(me)) throw new IOException("the journal directory " + dir + " belongs to another user");
        // An empty list is how the JDK reports a null DACL too, which grants everyone everything (M33B1-CDX-96).
        if (acl.isEmpty()) throw new IOException("the access list of " + dir + " cannot be verified (empty or null)");
        for (var e : acl) {
            if (e.type() != java.nio.file.attribute.AclEntryType.ALLOW || java.util.Collections.disjoint(e.permissions(), ACL_WRITES)) continue;
            if (e.principal().equals(me) || trusted.contains(e.principal())) continue;
            throw new IOException(e.principal().getName() + " can change " + dir + "; remove its write access to use IntelliAda's journal");
        }
    }

    /**
     * The local system, the built-in administrators, the creator-owner placeholder and the installer service, resolved by
     * the OS to their identities. One that does not resolve (a localized name) is simply not trusted: a refusal, never
     * an acceptance.
     */
    private static Set<java.nio.file.attribute.UserPrincipal> trustedPrincipals(java.nio.file.attribute.UserPrincipalLookupService lookup) {
        Set<java.nio.file.attribute.UserPrincipal> out = new java.util.HashSet<>();
        for (String n : List.of("NT AUTHORITY\\SYSTEM", "BUILTIN\\Administrators", "CREATOR OWNER", "NT SERVICE\\TrustedInstaller")) {
            try {
                out.add(lookup.lookupPrincipalByName(n));
            } catch (IOException | UnsupportedOperationException e) {
                // not resolvable here: not trusted
            }
        }
        return out;
    }

    /** A new directory on an ACL file system: this user only, inherited by what is created in it (M33B1-CDX-92). */
    private static void aclRestrict(Path dir) throws IOException {
        java.nio.file.attribute.AclFileAttributeView v = Files.getFileAttributeView(dir, java.nio.file.attribute.AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (v == null) throw new IOException("the privacy of " + dir + " cannot be established on this file system");
        var me = me();
        v.setAcl(List.of(java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(me)
                .setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class))
                .setFlags(java.nio.file.attribute.AclEntryFlag.FILE_INHERIT, java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT).build()));
    }

    private void privateDir(Path dir) throws IOException {
        Path rel = root.relativize(dir.toAbsolutePath().normalize());
        if (rel.startsWith("..")) throw new IOException("outside the journal's root");
        List<Path> chain = new ArrayList<>();
        for (Path p = dir; p != null && p.startsWith(root); p = p.getParent()) chain.add(0, p);
        // Every ancestor this store creates, up to the first that exists (the user's home), is made durable in its
        // parent before anything is written beneath it (M33B1-CDX-15).
        List<Path> missing = new ArrayList<>();
        for (Path a = root.getParent(); a != null && !Files.isDirectory(a); a = a.getParent()) missing.add(0, a);
        for (Path a : missing) {
            try {
                // The root's own parent is created private: whoever can write it can rename the root away (M33B1-CDX-80).
                if (a.equals(root.getParent()) && a.getParent() != null && posix(a.getParent())) {
                    Files.createDirectory(a, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                } else {
                    Files.createDirectory(a);
                }
            } catch (FileAlreadyExistsException raced) {
                // made meanwhile
            }
        }
        guardParent(root.getParent());
        // Up to the user's home, and never into directories that are not this user's.
        Path home = homeDir();
        for (Path a = root.getParent(); a != null && a.getParent() != null && !a.equals(home); a = a.getParent()) {
            if (posix(a) && !Files.getOwner(a.getParent(), LinkOption.NOFOLLOW_LINKS).equals(me())) break;
            durable(a);
        }
        realDirectories(dir);   // the existing ones, before anything is created beneath them (M33B1-CDX-107)
        for (Path p : chain) {
            if (Files.isSymbolicLink(p)) throw new IOException("a journal directory is a symbolic link");
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    if (posix(p.getParent())) {
                        Files.createDirectory(p, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                    } else {
                        Files.createDirectory(p);
                        aclRestrict(p);
                    }
                    forceDir(p.getParent());   // the new directory's entry is durable before anything is written in it
                } catch (FileAlreadyExistsException raced) {
                    // made meanwhile; checked below
                }
            }
            if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) throw new IOException("a journal path is not a directory");
            // Its entry in its parent made durable once per process, whoever created it (a retry after a crash between
            // creation and sync, or a concurrent first use; M33B1-CDX-15).
            if (p.getParent() != null) durable(p);
            if (posix(p)) {
                PosixFileAttributeView v = Files.getFileAttributeView(p, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                var owner = v.getOwner();
                if (!owner.equals(me())) throw new IOException("a journal directory belongs to another user");
                v.setPermissions(PosixFilePermissions.fromString("rwx------"));
            } else {
                aclPrivate(p);   // every directory of the store, existing ones included (M33B1-CDX-92)
            }
        }
        realDirectories(dir);   // once they all exist (M33B1-CDX-103)
    }
}
