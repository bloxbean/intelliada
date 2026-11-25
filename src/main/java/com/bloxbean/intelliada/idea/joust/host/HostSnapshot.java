package com.bloxbean.intelliada.idea.joust.host;

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

    private HostSnapshot(Path root, List<Path> classPath, String digest, List<Anchor> anchors) {
        this.root = root;
        this.classPath = classPath;
        this.digest = digest;
        this.anchors = anchors;
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
            return new HostSnapshot(done, List.copyOf(cp), s.digest, s.anchors);
        } finally {
            if (!ok) delete(root);
        }
    }

    private static HostSnapshot copyAll(HostSpec spec, Path root) throws HostSpec.Invalid, IOException {
        StringBuilder lines = new StringBuilder();
        List<Path> cp = new ArrayList<>();
        List<Anchor> anchors = new ArrayList<>();
        long[] total = {0, 0};
        for (HostSpec.Entry e : spec.launch()) {
            int o = e.ordinal();
            if (Thread.currentThread().isInterrupted()) throw new HostSpec.Invalid("copying the launch set was cancelled");
            if (e.jar()) {
                Path dst = root.resolve(o + ".jar");
                String h = copyFile(e.path(), dst, total);
                if (!h.equals(e.sha256())) throw new HostSpec.Invalid("a class-path jar changed since joustHostSpec ran (ordinal " + o + ")");
                lines.append(h).append(' ').append(o).append(" jar \n");
                jarAnchors(dst, o, anchors, total);
                cp.add(dst);
            } else {
                // The declared root must be a real directory (not a link), even when it lists no files (M31A-CDX-12).
                if (!Files.isDirectory(e.path(), LinkOption.NOFOLLOW_LINKS)) {
                    throw new HostSpec.Invalid("a class-path directory is missing or a link; run the build again");
                }
                Path dir = root.resolve(String.valueOf(o));
                createPrivateDir(dir);
                List<String> rels = new ArrayList<>(e.files().keySet());
                rels.sort(HostSnapshot::compareUtf8);
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
                    }
                }
                cp.add(dir);
            }
        }
        String digest = sha256Hex(lines.toString().getBytes(StandardCharsets.UTF_8));
        if (!digest.equals(spec.digest())) throw new HostSpec.Invalid("the launch set's digest does not match the launch spec");
        return new HostSnapshot(root, List.copyOf(cp), digest, Collections.unmodifiableList(anchors));
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
    private static void jarAnchors(Path jar, int ordinal, List<Anchor> anchors, long[] total) throws HostSpec.Invalid, IOException {
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
            local.sort(null);
            central.sort(null);
            if (!local.equals(central)) throw new HostSpec.Invalid("a class-path jar's entries read differently to the JVM (ordinal " + ordinal + ")");
            var en = jf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                String name = ze.getName();
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
