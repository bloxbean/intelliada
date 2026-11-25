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
import java.util.zip.ZipInputStream;

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
                t.interrupt();
                throw new HostSpec.Invalid("copying the launch set did not finish in time");
            } catch (ExecutionException e) {
                Throwable c = e.getCause();
                if (c instanceof HostSpec.Invalid i) throw i;
                if (c instanceof IOException io) throw io;
                throw new IOException(c);
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
            if (e.jar()) {
                Path dst = root.resolve(o + ".jar");
                String h = copyFile(e.path(), dst, total);
                if (!h.equals(e.sha256())) throw new HostSpec.Invalid("a class-path jar changed since joustHostSpec ran (ordinal " + o + ")");
                lines.append(h).append(' ').append(o).append(" jar \n");
                jarAnchors(dst, o, anchors, total);
                cp.add(dst);
            } else {
                Path dir = root.resolve(String.valueOf(o));
                createPrivateDir(dir);
                List<String> rels = new ArrayList<>(e.files().keySet());
                rels.sort(HostSnapshot::compareUtf8);
                for (String rel : rels) {
                    Path src = e.path().resolve(rel);
                    Path dst = dir.resolve(rel);
                    if (!dst.normalize().startsWith(dir)) throw new HostSpec.Invalid("a relative path leaves its entry");
                    Files.createDirectories(dst.getParent());
                    String h = copyFile(src, dst, total);
                    if (!h.equals(e.files().get(rel))) throw new HostSpec.Invalid("a class-path file changed since joustHostSpec ran: " + rel);
                    lines.append(h).append(' ').append(o).append(" dir ").append(rel).append('\n');
                    if (isAnchor(rel)) anchors.add(new Anchor(o, rel, readCapped(dst, total)));
                }
                cp.add(dir);
            }
        }
        String digest = sha256Hex(lines.toString().getBytes(StandardCharsets.UTF_8));
        if (!digest.equals(spec.digest())) throw new HostSpec.Invalid("the launch set's digest does not match the launch spec");
        return new HostSnapshot(root, List.copyOf(cp), digest, Collections.unmodifiableList(anchors));
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
        MessageDigest md = sha256();
        try (InputStream in = Files.newInputStream(src, LinkOption.NOFOLLOW_LINKS);
             OutputStream out = Files.newOutputStream(dst, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
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

    private static void jarAnchors(Path jar, int ordinal, List<Anchor> anchors, long[] total) throws HostSpec.Invalid, IOException {
        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry ze;
            int entries = 0;
            while ((ze = z.getNextEntry()) != null) {
                if (++entries > 200_000) throw new HostSpec.Invalid("a class-path jar has too many entries");
                String name = ze.getName();
                if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                    byte[] mf = z.readNBytes(MAX_ANCHOR_BYTES + 1);
                    if (mf.length > MAX_ANCHOR_BYTES) throw new HostSpec.Invalid("a class-path jar's manifest is too large");
                    String cp = new java.util.jar.Manifest(new java.io.ByteArrayInputStream(mf)).getMainAttributes()
                            .getValue(java.util.jar.Attributes.Name.CLASS_PATH);
                    if (cp != null && !cp.isBlank()) {
                        throw new HostSpec.Invalid("a class-path jar's manifest adds a Class-Path, which the dev host refuses (ordinal " + ordinal + ")");
                    }
                    continue;
                }
                if (ze.isDirectory() || !isAnchor(name) || !HostSpec.safeRelative(name)) continue;
                byte[] b = z.readNBytes(MAX_ANCHOR_BYTES + 1);
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
                    d.toFile().setWritable(true, true);
                    d.toFile().setExecutable(true, true);
                    d.toFile().setReadable(true, true);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(f);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // a leftover snapshot is removed with the next cleanup of the snapshots directory
        }
    }

    /** For tests and the status line: the anchors by relative path (last class-path entry wins only if earlier is absent). */
    public Map<String, Anchor> firstByPath() {
        Map<String, Anchor> m = new LinkedHashMap<>();
        for (Anchor a : anchors) m.putIfAbsent(a.path(), a);
        return m;
    }
}
