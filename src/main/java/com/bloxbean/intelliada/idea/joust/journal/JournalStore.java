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
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * ADR-005 D17's journal files (M3.3b1), simplified for devnets and test networks (§12 E12, L44): one root per user
 * ({@link #forUser}), one directory per chain, one file per module:
 * <pre>
 *   chains/&lt;chain&gt;/&lt;module&gt;.journal.json   one module's journal on the chain
 *   chains/&lt;chain&gt;/&lt;module&gt;.host            the module's host lock (one dev host per module and chain, across IDEs)
 *   chains/&lt;chain&gt;/lock                     the chain's file lock (writes of several modules are serialized)
 * </pre>
 * {@code <chain>} and {@code <module>} are SHA-256 prefixes of the chain identity (D14) and the module's real path. Each
 * write goes to a fresh temporary file, is forced to disk and renamed over the old file; the directory is forced where the
 * platform allows (best effort). Directories are created private to this user where the file system has POSIX modes.
 * A journal that cannot be read is moved aside by its owner, never guessed at. Nothing here holds key material.
 */
public final class JournalStore {

    /** The format (2: one file per module; version 1 never shipped). */
    public static final int VERSION = 2;
    static final Pattern RESERVATION = Pattern.compile("[\\x21-\\x7e]{1,128}");
    private static final Pattern HASH32 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{32}");
    static final long MAX_FILE = 64L * 1024 * 1024;

    /**
     * The largest body the journal holds (M33B1-SEC-3): four times Conway's 16 KiB maximum transaction size, so a body a
     * node could accept always fits.
     */
    public static final int MAX_BODY_BYTES = 64 * 1024;

    private static final com.intellij.openapi.diagnostic.Logger LOG = com.intellij.openapi.diagnostic.Logger.getInstance(JournalStore.class);

    /** A store failure: the operation it guards does not happen. */
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
     * returned it, the chain's evidence of its inclusion, the events IntelliAda observed, and a short label.
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
            next.add(e.length() > 200 ? e.substring(0, 200) : e);
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
    public record ModuleJournal(String chain, String module, List<Entry> entries) {
        public ModuleJournal {
            entries = List.copyOf(entries);
        }
    }

    // ---- the store ----------------------------------------------------------------------------------------------------

    private final Path root;
    private static final Map<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> OWNED = new ConcurrentHashMap<>();

    public JournalStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * The store for this user, independent of the IDE product and version (M33B1-DES-20): {@code ~/.intelliada/joust-journal}.
     * An IDE's system directory belongs to one product and version and is not migrated on upgrade.
     */
    public static JournalStore forUser() {
        return new JournalStore(Path.of(System.getProperty("user.home"), ".intelliada", "joust-journal"));
    }

    public Path root() {
        return root;
    }

    /** This store's identity in this process: its real path once it exists (two spellings of one store share it). */
    public String identity() {
        try {
            return root.toRealPath().toString();
        } catch (IOException e) {
            return root.toString();
        }
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

    Path journalFile(String chainKey, String moduleKey) {
        return chainDir(chainKey).resolve(moduleKey + ".journal.json");
    }

    /** A held lock. */
    public interface Held extends AutoCloseable {
        @Override
        void close();
    }

    /** The chain lock: in this process, then the file lock across IDE processes; a bounded wait. */
    public Held lock(String chainKey) throws StoreFailed, InterruptedException {
        requireKey(chainKey);
        Path dir = dirs(chainDir(chainKey));
        String real = realOf(dir);
        ReentrantLock in = LOCKS.computeIfAbsent(real, k -> new ReentrantLock());
        if (!in.tryLock(30, TimeUnit.SECONDS)) throw new StoreFailed("the chain's journal is busy; try again");
        if (in.getHoldCount() > 1) return in::unlock;   // re-entered by this thread: the file lock is held already
        FileChannel ch = null;
        try {
            ch = FileChannel.open(dir.resolve("lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            FileLock fl;
            while ((fl = tryFileLock(ch)) == null) {
                if (System.nanoTime() > end) throw new StoreFailed("another IDE holds the chain's journal; try again");
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
                closeQuietly(channel);
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
        String real = realOf(dirs(chainDir(chainKey))) + "/" + moduleKey + ".host";
        // Reserved in this JVM before any descriptor is opened: a refused second owner never opens (and so never closes) a
        // descriptor of the live owner's lock file, which would release its OS lock (M33B1-CDX-4).
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

    // ---- loads and writes ---------------------------------------------------------------------------------------------

    public Loaded<ModuleJournal> journal(String chainKey, String moduleKey) throws StoreFailed {
        requireKey(moduleKey);
        JsonNode n = read(journalFile(chainKey, moduleKey));
        if (n == null) return Loaded.missing();
        try {
            fields(n, "version", "chain", "module", "entries");
            version(n);
            if (!moduleKey.equals(text(n, "module")) || !key(text(n, "chain")).equals(chainKey)) throw new Bad();
            JsonNode es = n.get("entries");
            if (es == null || !es.isArray()) throw new Bad();
            List<Entry> entries = new ArrayList<>();
            Set<String> ids = new java.util.HashSet<>();
            for (JsonNode e : es) {
                Entry x = entry(e);
                if (!ids.add(x.reservation())) throw new Bad();
                entries.add(x);
            }
            return new Loaded<>(new ModuleJournal(text(n, "chain"), moduleKey, entries), true);
        } catch (Bad b) {
            return Loaded.unreadable();
        }
    }

    /** Every module journal file present in the chain's directory. */
    public List<String> journalModules(String chainKey) throws StoreFailed {
        Path dir = chainDir(chainKey);
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (var s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                String m = n.endsWith(".journal.json") ? n.substring(0, n.length() - ".journal.json".length()) : null;
                if (m != null && KEY.matcher(m).matches()) out.add(m);
            }
        } catch (IOException e) {
            throw new StoreFailed("the chain's directory cannot be listed (" + e.getClass().getSimpleName() + ")", e);
        }
        out.sort(null);
        return out;
    }

    public boolean fits(ModuleJournal j) {
        try {
            return bytes(json(j)).length <= MAX_FILE;
        } catch (IOException e) {
            return false;
        }
    }

    public void writeJournal(ModuleJournal j) throws StoreFailed {
        write(journalFile(key(j.chain()), j.module()), json(j));
    }

    /** Moves an unreadable journal aside (renamed, never deleted); returns the new name, or null when it was gone. */
    @Nullable Path setAside(String chainKey, String moduleKey) throws StoreFailed {
        Path f = journalFile(chainKey, moduleKey);
        Path aside = f.resolveSibling(f.getFileName() + ".lost-" + System.currentTimeMillis());
        try {
            Files.move(f, aside, StandardCopyOption.ATOMIC_MOVE);
            forceDirQuietly(f.getParent());
            return aside;
        } catch (NoSuchFileException gone) {
            return null;
        } catch (IOException e) {
            throw new StoreFailed("an unreadable journal cannot be moved aside (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    private static ObjectNode json(ModuleJournal j) {
        ObjectNode n = HostJson.object().put("version", VERSION).put("chain", j.chain()).put("module", j.module());
        ArrayNode es = n.putArray("entries");
        for (Entry e : j.entries()) es.add(json(e));
        return n;
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

    /**
     * Whether {@code e} reads back exactly as written (M33B1-CDX-13): admission checks a candidate entry with the loader's
     * own rules before anything is written, so a journal never holds an entry its next load refuses.
     */
    public static boolean readsBack(Entry e) {
        try {
            return entry(HostJson.MAPPER.readTree(HostJson.MAPPER.writeValueAsBytes(json(e)))).equals(e);
        } catch (Bad | IOException x) {
            return false;
        }
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

    /** An entry, its body read again: its hash and validity must be the ones recorded. */
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

    // ---- files --------------------------------------------------------------------------------------------------------

    /** The file's JSON, or null when it does not exist; one that exists but cannot be read parses as unreadable. */
    private @Nullable JsonNode read(Path f) throws StoreFailed {
        byte[] b;
        try {
            if (Files.size(f) > MAX_FILE) return HostJson.object().put("$tooLarge", true);
            b = Files.readAllBytes(f);
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            return HostJson.object().put("$unreadable", true);   // an unreadable file is moved aside by its owner
        }
        try {
            JsonNode n = HostJson.MAPPER.readTree(b);
            return n == null ? HostJson.object().put("$empty", true) : n;
        } catch (IOException e) {
            return HostJson.object().put("$notJson", true);
        }
    }

    /** Tests: writes to the files this matches fail, as a full disk would. */
    java.util.function.Predicate<Path> failWritesForTests = p -> false;

    private static byte[] bytes(JsonNode value) throws IOException {
        return (HostJson.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private void write(Path f, JsonNode value) throws StoreFailed {
        if (failWritesForTests.test(f)) throw new StoreFailed("a journal file cannot be written (a test's failure): " + f.getFileName());
        Path dir = f.getParent();
        Path tmp = null;
        try {
            dirs(dir);
            byte[] bytes = bytes(value);
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
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);   // best effort on a file system without it
            }
            tmp = null;
            forceDirQuietly(dir);
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

    /** Creates {@code dir} and its parents, private to this user where the file system has POSIX modes (best effort). */
    private static Path dirs(Path dir) throws StoreFailed {
        try {
            if (!Files.isDirectory(dir)) {
                if (posix(dir.getRoot() == null ? dir : dir.getRoot())) {
                    Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                } else {
                    Files.createDirectories(dir);
                }
            }
            return dir;
        } catch (IOException e) {
            throw new StoreFailed("the journal's directory cannot be created (" + e.getClass().getSimpleName() + "): " + dir, e);
        }
    }

    private static String realOf(Path dir) throws StoreFailed {
        try {
            return dir.toRealPath().toString();
        } catch (IOException e) {
            throw new StoreFailed("the chain's journal directory cannot be used (" + e.getClass().getSimpleName() + ")", e);
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

    /** Forces a directory's entries to disk where the platform allows; a failure is logged, never fatal. */
    private static void forceDirQuietly(Path dir) {
        if (!posix(dir)) return;
        try (FileChannel ch = FileChannel.open(dir, StandardOpenOption.READ)) {
            ch.force(true);
        } catch (IOException e) {
            LOG.debug("JOUST journal: the directory sync failed (" + e.getClass().getSimpleName() + "): " + dir);
        }
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
}
