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
 * ADR-005 D17's files (M3.3b1), under one root in the IDE system directory, never in a project:
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
            return new Entry(reservation, txHash, cbor, validFrom, validTo, handout, node, s, at, at == null ? null : inclusion, events, label);
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
    public record Rewind(long tip, String reason, Set<String> pending) {
        public Rewind {
            pending = Set.copyOf(pending);
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
                              @Nullable Rewind rewind, List<Registration> registrations) {
        public ChainRecord {
            modules = Map.copyOf(modules);
            registrations = List.copyOf(registrations);
        }

        ChainRecord with(@Nullable Long mark, Map<String, Long> modules, long generation, @Nullable Fence fence, @Nullable Rewind rewind,
                         List<Registration> registrations) {
            return new ChainRecord(chain, mark, modules, generation, fence, rewind, registrations);
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

    /** The store under the IDE system directory. */
    public static JournalStore inSystemDirectory() {
        return new JournalStore(Path.of(com.intellij.openapi.application.PathManager.getSystemPath(), "intelliada", "joust-journal"));
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
            return in::unlock;   // re-entered by the same thread: the file lock is already held
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
            throw new StoreFailed("the module's host lock cannot be taken (" + e.getClass().getSimpleName() + ")", e);
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

    public Loaded<ChainRecord> record(String chainKey) throws StoreFailed {
        JsonNode n = read(recordFile(chainKey));
        if (n == null) return Loaded.missing();
        try {
            fields(n, "version", "chain", "mark", "modules", "generation", "fence", "rewind", "registrations");
            version(n);
            Map<String, Long> modules = new java.util.LinkedHashMap<>();
            JsonNode ms = n.get("modules");
            if (ms == null || !ms.isObject()) throw new Bad();
            for (var it = ms.fields(); it.hasNext(); ) {
                var m = it.next();
                if (!KEY.matcher(m.getKey()).matches() || !m.getValue().isIntegralNumber() || !m.getValue().canConvertToLong()
                        || m.getValue().longValue() < 1) throw new Bad();
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
                fields(r, "tip", "reason", "pending");
                Set<String> pending = new LinkedHashSet<>();
                JsonNode ps = r.get("pending");
                if (!ps.isArray()) throw new Bad();
                for (JsonNode x : ps) {
                    if (!x.isTextual() || !KEY.matcher(x.textValue()).matches() || !pending.add(x.textValue())) throw new Bad();
                }
                rewind = new Rewind(slot(r, "tip"), boundedText(r, "reason"), pending);
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
            for (long g : modules.values()) if (g > generation) throw new Bad();
            return new Loaded<>(new ChainRecord(chain, mark, modules, generation, fence, rewind, regs), true);
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
            ObjectNode r = n.putObject("rewind").put("tip", c.rewind().tip()).put("reason", c.rewind().reason());
            ArrayNode ps = r.putArray("pending");
            c.rewind().pending().stream().sorted().forEach(ps::add);
        }
        ArrayNode rs = n.putArray("registrations");
        for (Registration g : c.registrations()) {
            ObjectNode o = rs.addObject().put("credential", g.credential()).put("txHash", g.txHash());
            if (g.slot() != null) o.put("slot", g.slot());
            else o.put("message", g.message());
        }
        write(recordFile(key(c.chain())), n);
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
            forceDir(f.getParent());
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

    private static void fields(JsonNode n, String... allowed) throws Bad {
        if (!n.isObject()) throw new Bad();
        Set<String> a = Set.of(allowed);
        for (var it = n.fieldNames(); it.hasNext(); ) if (!a.contains(it.next())) throw new Bad();
        for (String f : allowed) if (!n.has(f)) throw new Bad();
    }

    private static void version(JsonNode n) throws Bad {
        JsonNode v = n.get("version");
        if (v == null || !v.isIntegralNumber() || v.intValue() != VERSION) throw new Bad();
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
    private @Nullable JsonNode read(Path f) throws StoreFailed {
        byte[] b;
        try {
            if (Files.isSymbolicLink(f)) throw new StoreFailed("a journal file is a symbolic link: " + f.getFileName());
            if (!Files.exists(f, LinkOption.NOFOLLOW_LINKS)) return null;
            if (Files.size(f) > MAX_FILE) return HostJson.object().put("$tooLarge", true);
            b = Files.readAllBytes(f);
        } catch (NoSuchFileException e) {
            return null;
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
            forceDir(dir);
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
    private void privateDir(Path dir) throws IOException {
        Path rel = root.relativize(dir.toAbsolutePath().normalize());
        if (rel.startsWith("..")) throw new IOException("outside the journal's root");
        List<Path> chain = new ArrayList<>();
        for (Path p = dir; p != null && p.startsWith(root); p = p.getParent()) chain.add(0, p);
        if (!Files.isDirectory(root.getParent() == null ? root : root.getParent())) Files.createDirectories(root.getParent());
        for (Path p : chain) {
            if (Files.isSymbolicLink(p)) throw new IOException("a journal directory is a symbolic link");
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    if (posix(p.getParent())) {
                        Files.createDirectory(p, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                    } else {
                        Files.createDirectory(p);
                    }
                    forceDir(p.getParent());   // the new directory's entry is durable before anything is written in it
                } catch (FileAlreadyExistsException raced) {
                    // made meanwhile; checked below
                }
            }
            if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) throw new IOException("a journal path is not a directory");
            if (posix(p)) {
                PosixFileAttributeView v = Files.getFileAttributeView(p, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                var owner = v.getOwner();
                if (!owner.equals(me())) throw new IOException("a journal directory belongs to another user");
                v.setPermissions(PosixFilePermissions.fromString("rwx------"));
            }
        }
    }
}
