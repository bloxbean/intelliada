package com.bloxbean.intelliada.idea.joust.host;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;

/**
 * The private, read-only copy of a launch set that the host runs from (ADR-005 D11 step 3, I24) and the anchors read from
 * it before launch (step 4, I26).
 * <ul>
 *   <li>Each source is checked to be a regular file without following a link, then copied under a deadline (a FIFO or
 *   device swapped in cannot stall the launch indefinitely), and <b>the copy's bytes</b> are hashed as they are written.</li>
 *   <li>Every hash must equal the spec's; the digest is recomputed by the protocol's fingerprint format and must equal the
 *   spec's.</li>
 *   <li>Descriptions and Plutus blueprints ({@code META-INF/joust/*.json}, {@code META-INF/plutus/**.json}) found in the
 *   copies are kept in memory, in class-path order.</li>
 * </ul>
 */
public final class HostSnapshot {

    static final long MAX_FILE_BYTES = 512L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024;
    static final int MAX_ANCHOR_BYTES = 8 * 1024 * 1024;
    static final long MAX_ANCHORS_TOTAL = 64L * 1024 * 1024;
    static final long COPY_DEADLINE_MS = 120_000;

    /** One anchor resource: where it came from (ordinal, relative path or jar entry) and its bytes. */
    public record Anchor(int ordinal, String path, byte[] bytes) {
    }

    private final Path root;
    private final List<Path> classPath;
    private final String digest;
    private final List<Anchor> anchors;
    /** The class files of each class-path entry, by ordinal (ADR-005 D13 anchors: which entry holds a class). */
    private final Map<Integer, java.util.Set<String>> classes;
    /** Versioned class copies ({@code META-INF/versions/N/}) by entry, with their versions. */
    private final Map<Integer, Map<String, java.util.Set<Integer>>> versioned;
    /** The jars whose manifest says {@code Multi-Release: true}. */
    private final java.util.Set<Integer> multiRelease;
    /** The directory entries. */
    private final java.util.Set<Integer> directories;
    /**
     * Per directory entry, the protected package roots its file system serves under their canonical names, whatever the
     * spelling of the copied paths (M33A-CDX-54). Resolved when the snapshot is made, never later.
     */
    private final Map<Integer, java.util.Set<String>> packagesServed;

    /**
     * Per entry, how many physical entries carry each class-file name ({@code Foo.class}), counted as the runtime counts
     * them when it pairs a validator with its blueprint: every jar entry (base, versioned, repeated) and every directory
     * file or folder of that name (M33A-CDX-61, -CDX-62).
     */
    private final Map<Integer, Map<String, Integer>> fileNames;
    /** Why classes in this launch set cannot be resolved as every JDK would: no anchors then (M33A-DES-26). */
    private final List<String> ambiguities;

    /** The reasons the class resolution is ambiguous across JDKs; empty when it is not. */
    public List<String> ambiguities() {
        return ambiguities;
    }

    /** How many physical entries of {@code ordinal} carry the file name {@code simpleFile}, as the runtime counts them. */
    public int physicalNamed(int ordinal, String simpleFile) {
        return fileNames.getOrDefault(ordinal, Map.of()).getOrDefault(simpleFile, 0);
    }

    private static void countName(Map<Integer, Map<String, Integer>> names, int ordinal, String path) {
        String seg = path.substring(path.lastIndexOf('/') + 1);
        if (seg.endsWith(".class")) names.computeIfAbsent(ordinal, k -> new java.util.HashMap<>()).merge(seg, 1, Integer::sum);
    }

    /** The package roots of JOUST's reviewed jars: no other entry may serve classes in them (D12). */
    public static final List<String> PROTECTED_PACKAGES = List.of("org/julclang/state/api/", "org/julclang/state/runtime/",
            "org/julclang/state/serve/", "org/julclang/state/host/", "org/julclang/state/verifier/");

    /** Classes IntelliAda anchors besides each description's validator. */
    public static final List<String> VERIFIER_CLASSES = List.of("org/julclang/state/verifier/OutputVerifier.class",
            "org/julclang/state/verifier/MapVerifier.class");

    /** Whether the entry with this ordinal is a directory. */
    public boolean isDirectory(int ordinal) {
        return directories.contains(ordinal);
    }

    /** Whether the directory entry with this ordinal serves a class in {@code pkg} (one of {@link #PROTECTED_PACKAGES}). */
    public boolean servesPackage(int ordinal, String pkg) {
        return packagesServed.getOrDefault(ordinal, java.util.Set.of()).contains(pkg);
    }

    /**
     * A loose key for names a directory may treat as equal: case-folded both ways and decomposed. Used only to
     * over-report ambiguity, never to decide what an entry serves (that is the file system's answer).
     */
    public static String loose(String s) {
        return java.text.Normalizer.normalize(s.toUpperCase(java.util.Locale.ROOT).toLowerCase(java.util.Locale.ROOT),
                java.text.Normalizer.Form.NFKD);
    }

    /** Whether {@code dir}'s file system ignores case: a file created there is found under another casing. For tests. */
    public static boolean ignoresCase(Path dir) throws IOException {
        String name = ".intelliada-case-probe-" + java.util.UUID.randomUUID().toString().replace("-", "");
        Path probe = Files.createFile(dir.resolve(name));
        try {
            return Files.exists(dir.resolve(name.toUpperCase(java.util.Locale.ROOT)), LinkOption.NOFOLLOW_LINKS);
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    static final int MAX_CLASS_NAMES = 1_000_000;

    private HostSnapshot(Path root, List<Path> classPath, String digest, List<Anchor> anchors, Map<Integer, java.util.Set<String>> classes,
                         Map<Integer, Map<String, java.util.Set<Integer>>> versioned, java.util.Set<Integer> multiRelease,
                         java.util.Set<Integer> directories, Map<Integer, java.util.Set<String>> packagesServed,
                         Map<Integer, Map<String, Integer>> fileNames, List<String> ambiguities) {
        this.root = root;
        this.classPath = classPath;
        this.digest = digest;
        this.anchors = anchors;
        this.classes = classes;
        this.versioned = versioned;
        this.multiRelease = multiRelease;
        this.directories = directories;
        this.packagesServed = packagesServed;
        this.fileNames = fileNames;
        this.ambiguities = ambiguities;
    }

    /**
     * Every class file physically in the entry with this ordinal ({@code pkg/Name.class}), a versioned copy under its
     * plain name: for the duplicate and shadowing checks (D12, D13).
     */
    public java.util.Set<String> classesAt(int ordinal) {
        java.util.Set<String> all = new java.util.LinkedHashSet<>(classes.getOrDefault(ordinal, java.util.Set.of()));
        all.addAll(versioned.getOrDefault(ordinal, Map.of()).keySet());
        return all;
    }

    /**
     * The first entry, in class-path order, from which a JVM of feature version {@code javaFeature} loads
     * {@code classPath}: a base entry, or a versioned copy of at most that version in a multi-release jar (never in a
     * directory, never in a jar without {@code Multi-Release: true}; M33A-CDX-8). -1 when none.
     */
    public int firstWithClass(String classPath, int javaFeature) {
        java.util.TreeSet<Integer> ordinals = new java.util.TreeSet<>(classes.keySet());
        ordinals.addAll(versioned.keySet());
        for (int o : ordinals) {
            if (directories.contains(o)) {
                // What the directory's file system serves under the name, resolved when the snapshot was made.
                if (classes.getOrDefault(o, java.util.Set.of()).contains(classPath)) return o;
                continue;   // a directory never serves a versioned copy
            }
            if (classes.getOrDefault(o, java.util.Set.of()).contains(classPath)) return o;
            if (multiRelease.contains(o)) {
                java.util.Set<Integer> vs = versioned.getOrDefault(o, Map.of()).get(classPath);
                if (vs != null && vs.stream().anyMatch(v -> v >= 9 && v <= javaFeature)) return o;
            }
        }
        return -1;
    }

    /** Every entry holding {@code classPath}, as a base or a versioned copy, in class-path order (D13; M33A-SEC-1). */
    public List<Integer> entriesHolding(String classPath) {
        java.util.TreeSet<Integer> ordinals = new java.util.TreeSet<>(classes.keySet());
        ordinals.addAll(versioned.keySet());
        List<Integer> out = new ArrayList<>();
        for (int o : ordinals) {
            if (classesAt(o).contains(classPath)) out.add(o);
        }
        return out;
    }

    /** A version directory as the JDK spells it: a feature number without leading zeros (M33A-CDX-55). */
    private static final java.util.regex.Pattern VERSIONED = java.util.regex.Pattern.compile("META-INF/versions/([1-9][0-9]{0,2})/(.+)");

    /** A class file's plain name, or null if not a class. */
    static String className(String entry) {
        // Exact, as in a jar (M33A-CDX-45). What a directory serves under another spelling is resolved separately.
        if (!entry.endsWith(".class") || entry.endsWith("module-info.class") || entry.endsWith("package-info.class")) return null;
        java.util.regex.Matcher m = VERSIONED.matcher(entry);
        return m.matches() ? m.group(2) : entry;
    }

    private static void addClass(Map<Integer, java.util.Set<String>> classes, Map<Integer, Map<String, java.util.Set<Integer>>> versioned,
                                 int ordinal, String entry, long[] count) throws HostSpec.Invalid {
        String c = className(entry);
        if (c == null) return;
        if (++count[0] > MAX_CLASS_NAMES) throw new HostSpec.Invalid("the launch set has too many classes");
        java.util.regex.Matcher m = VERSIONED.matcher(entry);
        if (m.matches()) {
            versioned.computeIfAbsent(ordinal, k -> new LinkedHashMap<>()).computeIfAbsent(c, k -> new java.util.TreeSet<>())
                    .add(Integer.parseInt(m.group(1)));
        } else {
            classes.computeIfAbsent(ordinal, k -> new java.util.LinkedHashSet<>()).add(c);
        }
    }

    /** The snapshot directory ({@code build.snapshot} in {@code initialize}). */
    public Path root() {
        return root;
    }

    /** The class path, in ordinal order, inside the snapshot. */
    public List<Path> classPath() {
        return classPath;
    }

    /** The digest computed from the copies (equal to the spec's). */
    public String digest() {
        return digest;
    }

    /** The in-memory anchors, in class-path order (I26). */
    public List<Anchor> anchors() {
        return anchors;
    }

    /** Copies, hashes and checks {@code spec}'s launch set into a new private directory under {@code base}. */
    public static HostSnapshot create(HostSpec spec, Path base) throws HostSpec.Invalid, IOException, InterruptedException {
        Files.createDirectories(base);
        String id = UUID.randomUUID().toString();
        Path root = base.resolve(".tmp-" + id);
        Path done = base.resolve(id);
        createPrivateDir(root);
        boolean ok = false;
        try {
            FutureTask<HostSnapshot> task = new FutureTask<>(() -> copyAll(spec, root));
            Thread t = new Thread(task, "JOUST host snapshot");
            t.setDaemon(true);
            t.start();
            HostSnapshot s;
            try {
                s = task.get(COPY_DEADLINE_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new HostSpec.Invalid("copying the launch set did not finish in time");
            } catch (InterruptedException e) {
                throw e;
            } catch (ExecutionException e) {
                Throwable c = e.getCause();
                if (c instanceof HostSpec.Invalid i) throw i;
                if (c instanceof IOException io) throw io;
                throw new IOException(c);
            } finally {
                // Every exit stops the copy and waits (bounded) for it, so nothing writes after cleanup (M31A-SEC-6).
                if (!task.isDone()) {
                    task.cancel(true);
                    t.join(5000);
                }
            }
            // Atomic: the snapshot appears under its final name only when complete (ADR-005 D11 step 3); a directory is
            // renamed before it is made read-only (macOS refuses to move a directory without write permission on it).
            Files.move(root, done, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            try {
                makeReadOnly(done);
            } catch (IOException e) {
                delete(done);
                throw e;
            }
            List<Path> cp = new ArrayList<>();
            for (Path p : s.classPath) cp.add(done.resolve(s.root.relativize(p)));
            ok = true;
            // The resolutions were made in the copy before it moved; the moved directory serves the same names.
            return new HostSnapshot(done, List.copyOf(cp), s.digest, s.anchors, s.classes, s.versioned, s.multiRelease, s.directories,
                    s.packagesServed, s.fileNames, s.ambiguities);
        } finally {
            if (!ok) delete(root);
        }
    }

    private static HostSnapshot copyAll(HostSpec spec, Path root) throws HostSpec.Invalid, IOException {
        StringBuilder lines = new StringBuilder();
        List<Path> cp = new ArrayList<>();
        List<Anchor> anchors = new ArrayList<>();
        Map<Integer, java.util.Set<String>> classes = new LinkedHashMap<>();
        Map<Integer, Map<String, java.util.Set<Integer>>> versioned = new LinkedHashMap<>();
        java.util.Set<Integer> multiRelease = new java.util.HashSet<>();
        java.util.Set<Integer> directories = new java.util.HashSet<>();
        long[] total = {0, 0};
        long[] classCount = {0};
        Map<Integer, Path> dirPaths = new LinkedHashMap<>();
        Map<Integer, Map<String, Integer>> fileNames = new LinkedHashMap<>();
        List<String> ambiguities = new ArrayList<>();
        for (HostSpec.Entry e : spec.launch()) {
            int o = e.ordinal();
            if (Thread.currentThread().isInterrupted()) throw new HostSpec.Invalid("copying the launch set was cancelled");
            if (e.jar()) {
                Path dst = root.resolve(o + ".jar");
                String h = copyFile(e.path(), dst, total);
                if (!h.equals(e.sha256())) throw new HostSpec.Invalid("a class-path jar changed since joustHostSpec ran (ordinal " + o + ")");
                lines.append(h).append(' ').append(o).append(" jar \n");
                if (jarAnchors(dst, o, anchors, total, classes, versioned, classCount, fileNames, ambiguities)) multiRelease.add(o);
                cp.add(dst);
            } else {
                // The declared root must be a real directory (not a link), even when it lists no files (M31A-CDX-12).
                if (!Files.isDirectory(e.path(), LinkOption.NOFOLLOW_LINKS)) {
                    throw new HostSpec.Invalid("a class-path directory is missing or a link; run the build again");
                }
                Path dir = root.resolve(String.valueOf(o));
                directories.add(o);
                dirPaths.put(o, dir);
                createPrivateDir(dir);
                List<String> rels = new ArrayList<>(e.files().keySet());
                rels.sort(HostSnapshot::compareUtf8);
                java.util.Set<String> folders = new java.util.HashSet<>();
                for (String rel : rels) {
                    countName(fileNames, o, rel);
                    for (int k = rel.indexOf('/'); k > 0; k = rel.indexOf('/', k + 1)) {
                        if (folders.add(rel.substring(0, k))) countName(fileNames, o, rel.substring(0, k));
                    }
                }
                // Where the platform offers it, every component is opened relative to the directory handle already
                // checked, never by a fresh path lookup, so no component can be swapped for a link (M31A-CDX-24).
                try (java.nio.file.DirectoryStream<Path> ds = openRoot(e.path())) {
                    java.nio.file.SecureDirectoryStream<Path> sds = ds instanceof java.nio.file.SecureDirectoryStream<Path> x ? x : null;
                    for (String rel : rels) {
                        if (Thread.currentThread().isInterrupted()) throw new HostSpec.Invalid("copying the launch set was cancelled");
                        Path dst = dir.resolve(rel);
                        if (!dst.normalize().startsWith(dir)) throw new HostSpec.Invalid("a relative path leaves its entry");
                        Files.createDirectories(dst.getParent());
                        String h = sds != null ? copySecure(sds, rel, dst, total) : copyFile(noLinkBelow(e.path(), rel), dst, total);
                        if (!h.equals(e.files().get(rel))) throw new HostSpec.Invalid("a class-path file changed since joustHostSpec ran: " + rel);
                        lines.append(h).append(' ').append(o).append(" dir ").append(rel).append('\n');
                        if (isAnchor(rel)) anchors.add(new Anchor(o, rel, readCapped(dst, total)));
                        addClass(classes, versioned, o, rel, classCount);
                    }
                }
                cp.add(dir);
            }
        }
        String digest = sha256Hex(lines.toString().getBytes(StandardCharsets.UTF_8));
        if (!digest.equals(spec.digest())) throw new HostSpec.Invalid("the launch set's digest does not match the launch spec");
        Map<Integer, java.util.Set<String>> packagesServed = resolveInDirectories(dirPaths, anchors, classes, total);
        anchors.sort(java.util.Comparator.comparingInt(Anchor::ordinal));   // class-path order; stable within an entry
        Map<Integer, java.util.Set<String>> frozen = new LinkedHashMap<>();
        classes.forEach((k, v) -> frozen.put(k, Collections.unmodifiableSet(v)));
        return new HostSnapshot(root, List.copyOf(cp), digest, Collections.unmodifiableList(anchors), Collections.unmodifiableMap(frozen),
                Collections.unmodifiableMap(versioned), java.util.Set.copyOf(multiRelease), java.util.Set.copyOf(directories),
                Collections.unmodifiableMap(packagesServed), Collections.unmodifiableMap(fileNames), List.copyOf(ambiguities));
    }

    /**
     * Asks each copied directory's own file system what it serves under the names IntelliAda anchors, as the host's class
     * loader asks: a file system may treat other spellings as the same name (case, Unicode folding such as {@code ſ}
     * for {@code s}, normalization), and its answer is the only exact one (M33A-CDX-54). The names: every anchor named
     * exactly anywhere on the class path; each description's validator class and blueprint; the verifiers'; and the
     * protected package roots. A directory serving one under another spelling holds it, as if it were listed so.
     * Resolved here, once, from the private copy before the host starts; never later.
     */
    private static Map<Integer, java.util.Set<String>> resolveInDirectories(Map<Integer, Path> dirs, List<Anchor> anchors,
                                                                          Map<Integer, java.util.Set<String>> classes, long[] total)
            throws HostSpec.Invalid, IOException {
        Map<Integer, java.util.Set<String>> packages = new LinkedHashMap<>();
        if (dirs.isEmpty()) return packages;
        java.util.Set<String> descriptions = new java.util.TreeSet<>(), blueprints = new java.util.TreeSet<>();
        for (Anchor a : anchors) (a.path().startsWith("META-INF/joust/") ? descriptions : blueprints).add(a.path());
        resolveAnchors(dirs, descriptions, anchors, total);
        java.util.Set<String> wanted = new java.util.TreeSet<>(VERIFIER_CLASSES);
        for (String v : VERIFIER_CLASSES) blueprints.add("META-INF/plutus/" + v.substring(v.lastIndexOf('/') + 1, v.length() - 6) + ".plutus.json");
        for (Anchor a : List.copyOf(anchors)) {
            if (!a.path().startsWith("META-INF/joust/")) continue;
            JsonNode x;
            try {
                x = HostJson.parseResource(a.bytes()).path("x-joust");
            } catch (HostJson.Malformed m) {
                continue;   // refused when anchored
            }
            String pkg = x.path("package").textValue(), validator = x.path("validator").textValue();
            if (pkg == null || validator == null || !pkg.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
                    || !validator.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
            wanted.add(pkg.replace('.', '/') + "/" + validator + ".class");
            blueprints.add("META-INF/plutus/" + validator + ".plutus.json");
        }
        resolveAnchors(dirs, blueprints, anchors, total);
        for (var d : dirs.entrySet()) {
            java.util.Set<String> listed = classes.computeIfAbsent(d.getKey(), k -> new java.util.LinkedHashSet<>());
            for (String c : wanted) {
                cancelled();
                if (!listed.contains(c) && serves(d.getValue(), c)) listed.add(c);
            }
            java.util.Set<String> pk = new java.util.LinkedHashSet<>();
            for (String pkg : PROTECTED_PACKAGES) {
                // A class beneath the package root, as the file system serves it; resources alone are no class (M33A-CDX-63).
                Path root = d.getValue().resolve(pkg);
                if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) continue;
                try (var w = Files.walk(root)) {
                    var it = w.iterator();
                    while (it.hasNext()) {
                        cancelled();
                        Path f = it.next();
                        if (Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS) && loose(f.getFileName().toString()).endsWith(".class")) {
                            pk.add(pkg);
                            break;
                        }
                    }
                }
            }
            packages.put(d.getKey(), java.util.Set.copyOf(pk));
        }
        return packages;
    }

    /** Adds, for each directory, the anchors its file system serves under {@code names} that it does not list so. */
    private static void resolveAnchors(Map<Integer, Path> dirs, java.util.Set<String> names, List<Anchor> anchors, long[] total)
            throws HostSpec.Invalid, IOException {
        java.util.Set<String> listed = new java.util.HashSet<>();
        for (Anchor a : anchors) listed.add(a.ordinal() + "|" + a.path());
        for (var d : dirs.entrySet()) {
            for (String n : names) {
                cancelled();
                if (listed.contains(d.getKey() + "|" + n) || !serves(d.getValue(), n)) continue;
                anchors.add(new Anchor(d.getKey(), n, readCapped(d.getValue().resolve(n), total)));
            }
        }
    }

    /** Whether the copied directory's file system serves a regular file under {@code rel}. */
    private static boolean serves(Path dir, String rel) {
        if (!HostSpec.safeRelative(rel)) return false;
        try {
            return Files.isRegularFile(dir.resolve(rel), LinkOption.NOFOLLOW_LINKS);
        } catch (java.nio.file.InvalidPathException e) {
            return false;   // a name this file system cannot hold is not served from it (M33A-CDX-65)
        }
    }

    /** Stops the resolution pass once the snapshot is cancelled (M33A-CDX-66). */
    private static void cancelled() throws HostSpec.Invalid {
        if (Thread.currentThread().isInterrupted()) throw new HostSpec.Invalid("copying the launch set was cancelled");
    }

    /**
     * Opens a launch directory: through its parent's secure handle without following a link where the platform offers
     * one, else by path with its identity ({@code fileKey}) checked before and after the open (M31A-CDX-34).
     */
    private static java.nio.file.DirectoryStream<Path> openRoot(Path dir) throws HostSpec.Invalid, IOException {
        Path parent = dir.getParent();
        if (parent != null) {
            try (java.nio.file.DirectoryStream<Path> ps = Files.newDirectoryStream(parent)) {
                if (ps instanceof java.nio.file.SecureDirectoryStream<Path> sp) {
                    try {
                        return sp.newDirectoryStream(dir.getFileName(), LinkOption.NOFOLLOW_LINKS);
                    } catch (IOException e) {
                        throw new HostSpec.Invalid("a class-path directory is missing or a link; run the build again");
                    }
                }
            }
        }
        Object before = Files.readAttributes(dir, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
        java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(dir);
        Object after = Files.readAttributes(dir, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
        // Where the platform has no file key (Windows' JBR returns null), the identity check is unavailable and the
        // per-component no-link checks stand alone; a concurrent swap by same-user code is outside ADR-005's threat
        // boundary (L33) (M31A-CDX-40).
        if (before != null && !before.equals(after)) {
            ds.close();
            throw new HostSpec.Invalid("a class-path directory changed while it was opened");
        }
        return ds;
    }

    /**
     * Copies {@code rel} below the directory {@code root} is open on: each parent is opened relative to the previous
     * handle without following links, and the file's attributes and contents are read through the last one.
     */
    private static String copySecure(java.nio.file.SecureDirectoryStream<Path> root, String rel, Path dst, long[] total)
            throws HostSpec.Invalid, IOException {
        String[] segs = rel.split("/");
        List<java.nio.file.SecureDirectoryStream<Path>> opened = new ArrayList<>();
        try {
            java.nio.file.SecureDirectoryStream<Path> d = root;
            for (int i = 0; i < segs.length - 1; i++) {
                try {
                    d = d.newDirectoryStream(Path.of(segs[i]), LinkOption.NOFOLLOW_LINKS);
                } catch (IOException e) {
                    throw new HostSpec.Invalid("a class-path directory under an entry is missing or a link: " + rel);
                }
                opened.add(d);
            }
            Path name = Path.of(segs[segs.length - 1]);
            BasicFileAttributes a;
            try {
                a = d.getFileAttributeView(name, java.nio.file.attribute.BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                        .readAttributes();
            } catch (IOException e) {
                throw new HostSpec.Invalid("a class-path file is missing; run the build again");
            }
            if (!a.isRegularFile()) throw new HostSpec.Invalid("a class-path entry is not a regular file");
            if (a.size() > MAX_FILE_BYTES) throw new HostSpec.Invalid("a class-path file is too large");
            try (var ch = d.newByteChannel(name, java.util.Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                 InputStream in = java.nio.channels.Channels.newInputStream(ch)) {
                return copyStream(in, dst, total);
            }
        } finally {
            for (int i = opened.size() - 1; i >= 0; i--) {
                try {
                    opened.get(i).close();
                } catch (IOException ignored) {
                    // read-only handles
                }
            }
        }
    }

    /** {@code root/rel}, refusing a link at any component below {@code root} (M31A-CDX-12). */
    static Path noLinkBelow(Path root, String rel) throws HostSpec.Invalid {
        Path p = root;
        String[] segs = rel.split("/");
        for (int i = 0; i < segs.length - 1; i++) {
            p = p.resolve(segs[i]);
            if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                throw new HostSpec.Invalid("a class-path directory under an entry is missing or a link: " + rel);
            }
        }
        return p.resolve(segs[segs.length - 1]);
    }

    /** Copies one regular file (never through a link) and returns the SHA-256 of the bytes written. */
    private static String copyFile(Path src, Path dst, long[] total) throws HostSpec.Invalid, IOException {
        BasicFileAttributes a;
        try {
            a = Files.readAttributes(src, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new HostSpec.Invalid("a class-path file is missing; run the build again");
        }
        if (!a.isRegularFile()) throw new HostSpec.Invalid("a class-path entry is not a regular file");
        if (a.size() > MAX_FILE_BYTES) throw new HostSpec.Invalid("a class-path file is too large");
        try (InputStream in = Files.newInputStream(src, LinkOption.NOFOLLOW_LINKS)) {
            return copyStream(in, dst, total);
        }
    }

    /** Writes {@code in} to a new {@code dst} under the caps, returning the SHA-256 of the bytes written. */
    private static String copyStream(InputStream in, Path dst, long[] total) throws HostSpec.Invalid, IOException {
        MessageDigest md = sha256();
        try (OutputStream out = Files.newOutputStream(dst, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buf = new byte[64 * 1024];
            long n = 0;
            int r;
            while ((r = in.read(buf)) > 0) {
                n += r;
                total[0] += r;
                if (n > MAX_FILE_BYTES || total[0] > MAX_TOTAL_BYTES) throw new HostSpec.Invalid("the launch set is too large");
                md.update(buf, 0, r);
                out.write(buf, 0, r);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    static boolean isAnchor(String rel) {
        return (rel.startsWith("META-INF/joust/") && rel.endsWith(".json") && rel.indexOf('/', "META-INF/joust/".length()) < 0)
                || (rel.startsWith("META-INF/plutus/") && rel.endsWith(".json"));
    }

    /**
     * The anchors that answer {@code path}, in class-path order: the exact name in a jar; in a directory, the file its
     * file system serves under that name, resolved when the snapshot was made (M33A-CDX-36, -CDX-54).
     */
    public List<Anchor> anchorsNamed(String path) {
        // One index by name, built once: no scan of every anchor per lookup (M33A-CDX-51).
        Map<String, List<Anchor>> index = byName;
        if (index == null) {
            index = new java.util.HashMap<>();
            for (Anchor a : anchors) index.computeIfAbsent(a.path(), k -> new ArrayList<>()).add(a);
            byName = index;
        }
        return List.copyOf(index.getOrDefault(path, List.of()));
    }

    private volatile Map<String, List<Anchor>> byName;

    private static byte[] readCapped(Path p, long[] total) throws HostSpec.Invalid, IOException {
        long size = Files.size(p);
        if (size > MAX_ANCHOR_BYTES) throw new HostSpec.Invalid("a description or blueprint is larger than 8 MiB");
        total[1] += size;
        if (total[1] > MAX_ANCHORS_TOTAL) throw new HostSpec.Invalid("the descriptions and blueprints are too large");
        return Files.readAllBytes(p);
    }

    /**
     * The anchors of a copied jar, read through {@link java.util.jar.JarFile}, the central directory the JVM itself uses
     * (M31A-CDX-13); a manifest adding a {@code Class-Path} is refused, as the host refuses it.
     */
    /** Records a jar's anchors and classes; true when its manifest makes it a multi-release jar. */
    private static boolean jarAnchors(Path jar, int ordinal, List<Anchor> anchors, long[] total, Map<Integer, java.util.Set<String>> classes,
                                      Map<Integer, Map<String, java.util.Set<Integer>>> versioned, long[] classCount,
                                      Map<Integer, Map<String, Integer>> fileNames, List<String> ambiguities)
            throws HostSpec.Invalid, IOException {
        try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jar.toFile(), false)) {
            // The effective manifest, read through a bounded stream before it is parsed (M31A-CDX-25).
            // Exactly one entry may be named like the manifest, in any case: with several, IntelliAda could check one while
            // the host's JVM selects another (M31A-CDX-30).
            java.util.jar.Manifest mf = null;
            ZipEntry me = null;
            for (var en0 = jf.entries(); en0.hasMoreElements(); ) {
                ZipEntry z0 = en0.nextElement();
                if (z0.getName().equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                    if (me != null) throw new HostSpec.Invalid("a class-path jar has more than one manifest (ordinal " + ordinal + ")");
                    me = z0;
                }
            }
            if (me != null) {
                byte[] mb;
                try (InputStream in = jf.getInputStream(me)) {
                    mb = in.readNBytes(MAX_ANCHOR_BYTES + 1);
                }
                if (mb.length > MAX_ANCHOR_BYTES) throw new HostSpec.Invalid("a class-path jar's manifest is too large");
                mf = new java.util.jar.Manifest(new java.io.ByteArrayInputStream(mb));
            }
            if (mf != null) {
                String cp = mf.getMainAttributes().getValue(java.util.jar.Attributes.Name.CLASS_PATH);
                if (cp != null && !cp.isEmpty()) {
                    throw new HostSpec.Invalid("a class-path jar's manifest adds a Class-Path, which the dev host refuses (ordinal " + ordinal + ")");
                }
            }
            if (jf.size() > 200_000) throw new HostSpec.Invalid("a class-path jar has too many entries");
            // The local headers and the central directory must name the same entries: a jar that reads differently to
            // a stream parser and to the JVM is refused (M31A-SEC-2).
            java.util.List<String> central = new java.util.ArrayList<>();
            jf.stream().forEach(e -> central.add(e.getName()));
            java.util.List<String> local = new java.util.ArrayList<>();
            try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(Files.newInputStream(jar))) {
                ZipEntry le;
                while ((le = z.getNextEntry()) != null) {
                    local.add(le.getName());
                    if (local.size() > 200_000) break;
                }
            }
            // A repeated name IntelliAda reads (an anchor, the manifest, RuntimeVersion): IntelliAda and the JVM could read
            // different bytes under it (M33A-SEC-2). Other repeats change nothing IntelliAda derives (M33A-DES-12).
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (String n : central) {
                boolean read = isAnchor(n) || n.equalsIgnoreCase("META-INF/MANIFEST.MF") || n.endsWith("org/julclang/state/runtime/RuntimeVersion.class");
                if (!seen.add(n) && read) throw new HostSpec.Invalid("a class-path jar repeats the entry " + n + " (ordinal " + ordinal + ")");
            }
            local.sort(null);
            central.sort(null);
            if (!local.equals(central)) throw new HostSpec.Invalid("a class-path jar's entries read differently to the JVM (ordinal " + ordinal + ")");
            var en = jf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                String name = ze.getName();
                countName(fileNames, ordinal, name);   // every physical entry, repeats included, as the runtime counts
                // Jar names are exact: the JVM looks them up case-sensitively (M33A-CDX-45).
                if (!ze.isDirectory() && HostSpec.safeRelative(name)) addClass(classes, versioned, ordinal, name, classCount);
                if (ze.isDirectory() || !isAnchor(name) || !HostSpec.safeRelative(name)) continue;
                byte[] b;
                try (InputStream in = jf.getInputStream(ze)) {
                    b = in.readNBytes(MAX_ANCHOR_BYTES + 1);
                }
                if (b.length > MAX_ANCHOR_BYTES) throw new HostSpec.Invalid("a description or blueprint is larger than 8 MiB");
                total[1] += b.length;
                if (total[1] > MAX_ANCHORS_TOTAL) throw new HostSpec.Invalid("the descriptions and blueprints are too large");
                anchors.add(new Anchor(ordinal, name, b));
            }
            // Multi-Release as the JDK reads it: Boolean.parseBoolean of the value, never trimmed. A value other than true or
            // false, or a verdict other than this JDK's own, may read differently on the host's JDK: the snapshot is kept
            // (views and listings still work) and nothing is anchored (M33A-CDX-55, -DES-26).
            String mr = mf == null ? null : mf.getMainAttributes().getValue("Multi-Release");
            boolean ours = Boolean.parseBoolean(mr);
            boolean jdk;
            try (java.util.jar.JarFile v = new java.util.jar.JarFile(jar.toFile(), false, java.util.zip.ZipFile.OPEN_READ, Runtime.version())) {
                jdk = v.isMultiRelease();
            }
            if ((mr != null && !mr.equalsIgnoreCase("true") && !mr.equalsIgnoreCase("false")) || ours != jdk) {
                ambiguities.add("a class-path jar's Multi-Release value may read differently on another JDK (ordinal " + ordinal + ")");
            }
            return ours;
        } catch (java.util.zip.ZipException e) {
            throw new HostSpec.Invalid("a class-path jar is not a readable zip");
        }
    }

    /** Unsigned UTF-8 byte order, as the protocol sorts a directory's relative paths. */
    static int compareUtf8(String a, String b) {
        return Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256Hex(byte[] b) {
        return HexFormat.of().formatHex(sha256().digest(b));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean posix(Path p) {
        return p.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private static void createPrivateDir(Path dir) throws IOException {
        if (posix(dir)) {
            Files.createDirectory(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } else {
            Files.createDirectory(dir);
        }
    }

    private static void makeReadOnly(Path root) throws IOException {
        if (!posix(root)) {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
                    f.toFile().setWritable(false, false);
                    return FileVisitResult.CONTINUE;
                }
            });
            return;
        }
        Set<PosixFilePermission> file = PosixFilePermissions.fromString("r--------");
        Set<PosixFilePermission> dir = PosixFilePermissions.fromString("r-x------");
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
                Files.setPosixFilePermissions(f, file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.setPosixFilePermissions(d, dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Deletes the snapshot (after the host exited); best effort. */
    public void delete() {
        delete(root);
    }

    static void delete(Path root) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    // The directory's permissions are set without following a link (M31A-CDX-35).
                    try {
                        var v = Files.getFileAttributeView(d, java.nio.file.attribute.PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                        if (v != null) v.setPermissions(PosixFilePermissions.fromString("rwx------"));
                        else d.toFile().setWritable(true, true);
                    } catch (IOException | UnsupportedOperationException ignored) {
                        // deletion below then fails and is swept later
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
                    // Windows keeps a read-only file from deletion (M31A-CDX-15); one failure does not stop the walk.
                    // A link is unlinked as it is: no permission change may reach its target (M31A-CDX-28).
                    try {
                        if (attrs.isSymbolicLink() || !attrs.isRegularFile()) {
                            Files.deleteIfExists(f);
                            return FileVisitResult.CONTINUE;
                        }
                        // POSIX unlinks a read-only file given a writable parent: no chmod on files (M31A-CDX-35).
                        if (f.getFileSystem().supportedFileAttributeViews().contains("dos")) {
                            Files.setAttribute(f, "dos:readonly", false, LinkOption.NOFOLLOW_LINKS);
                        }
                        Files.deleteIfExists(f);
                    } catch (IOException | RuntimeException ignored) {
                        // swept later
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path f, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) {
                    try {
                        Files.deleteIfExists(d);
                    } catch (IOException ignored) {
                        // swept later
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // a leftover snapshot is removed with the next cleanup of the snapshots directory
        }
    }

    /** The anchors by relative path; the earliest class-path entry holding a path wins, as a class loader resolves it. */
    public Map<String, Anchor> firstByPath() {
        Map<String, Anchor> m = new LinkedHashMap<>();
        for (Anchor a : anchors) m.putIfAbsent(a.path(), a);
        return m;
    }
}
