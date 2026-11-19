package com.bloxbean.intelliada.idea.joust.core;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;

/**
 * ADR-004 D4: validation of the two application-level tool paths, the Node binary and {@code joust-lsp}.
 *
 * <p>Nothing here executes anything. The single method that runs a process, {@link #probeNodeVersion}, lives in
 * {@link NodeVersionProbe} and is called only from the explicit configuration actions (Save, Detect…), never at server
 * start (CDX-25, I1).
 */
public final class JoustToolValidator {

    /** The package.json beside joust-lsp.js is read as text, capped. */
    static final int PACKAGE_JSON_CAP = 256 * 1024;
    public static final String PACKAGE_NAME = "@julclang/joust";
    public static final int MIN_NODE_MAJOR = 22;
    /** The file names a Node binary has, selected and real (M1-CDX-5): this refuses npx, npm and script shims. */
    static final java.util.Set<String> NODE_NAMES = java.util.Set.of("node", "node.exe", "nodejs");
    /** Selected names that are never Node, whatever they resolve to. */
    static final java.util.Set<String> NOT_NODE = java.util.Set.of("npx", "npm", "corepack", "pnpm", "yarn", "bun");

    private JoustToolValidator() {
    }

    /** A validation outcome: either a real path, or a message for the user. */
    public record Result(Path selected, Path realPath, String error) {
        public boolean ok() {
            return error == null;
        }

        static Result fail(Path selected, String error) {
            return new Result(selected, null, error);
        }
    }

    /**
     * Validates the configured Node binary: an absolute path, not a script shim, an existing executable regular file,
     * and neither the selected path nor its real target inside a forbidden root or under {@code node_modules/.bin}.
     */
    public static Result checkNode(String configured, Collection<Path> forbiddenRoots, JoustPaths.Flavor flavor) {
        Result r = checkExecutable(configured, "Node", forbiddenRoots, flavor);
        if (!r.ok()) return r;
        if (isScript(r.realPath())) {
            return Result.fail(r.selected(), "Node resolves to a script, not the node binary: " + r.realPath()
                    + " (a version-manager or npm shim chooses what it runs; select the installed binary).");
        }
        // M1-CDX-5: the real target must be a node binary; npx, npm and their kin are refused whatever they resolve to.
        String selectedName = r.selected().getFileName().toString().toLowerCase(java.util.Locale.ROOT).replaceAll("\\.(exe|cmd|bat|ps1|js)$", "");
        if (!NODE_NAMES.contains(r.realPath().getFileName().toString().toLowerCase(java.util.Locale.ROOT)) || NOT_NODE.contains(selectedName)) {
            return Result.fail(r.selected(), "Node must be the node binary itself (named node, node.exe or nodejs), not "
                    + r.selected().getFileName() + " -> " + r.realPath().getFileName() + ": no npx, npm or version-manager shim.");
        }
        return r;
    }

    /**
     * D4's rule for any user-installed executable named by absolute path (the Node binary; the native {@code joust} of
     * D9): an absolute path, not a {@code .cmd/.bat/.ps1} shim (selected or real), an existing executable regular file,
     * and neither the selected path nor its real target inside a forbidden root or under {@code node_modules/.bin}.
     * Executes nothing.
     */
    public static Result checkExecutable(String configured, String what, Collection<Path> forbiddenRoots, JoustPaths.Flavor flavor) {
        Result base = checkAbsolute(configured, flavor, what);
        if (!base.ok()) return base;
        Path selected = base.selected();
        if (!Files.exists(selected)) return Result.fail(selected, what + " was not found at " + selected + ".");
        Path real;
        try {
            real = selected.toRealPath();
        } catch (IOException e) {
            return Result.fail(selected, what + "'s real path could not be resolved: " + e.getMessage());
        }
        // A regular, executable file first: the native-format check below opens the file, and opening a FIFO or a
        // device can block (M2 review).
        if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) return Result.fail(selected, what + " must be a file: " + real);
        if (!Files.isExecutable(real)) return Result.fail(selected, what + " is not executable: " + real);
        // Every tool IntelliAda runs by path must be a native executable for this system (M1-CDX-5): a script or a
        // foreign or truncated file is refused, so the JDK never falls back to /bin/sh.
        if (JoustPaths.hasRefusedExtension(real.toString()) || !isNativeExecutable(real)) {
            return Result.fail(selected, what + " resolves to a script or to a file that is not a native executable for this system,"
                    + " which is never run: " + real);
        }
        String where = locationProblem(selected, real, forbiddenRoots, flavor);
        if (where != null) return Result.fail(selected, what + " " + where);
        return new Result(selected, real, null);
    }

    /**
     * Resolves and validates the configured {@code joust-lsp} to its real {@code joust-lsp.js}.
     *
     * <ul>
     *   <li><b>Unix:</b> the selected file ({@code <prefix>/bin/joust-lsp}, a symlink, or the {@code .js} itself) is
     *   resolved through symlinks.</li>
     *   <li><b>Windows:</b> npm's global commands are {@code .cmd} shims, not links. A selected extensionless
     *   {@code <prefix>\joust-lsp} is mapped by package layout to
     *   {@code <prefix>\node_modules\@julclang\joust\bin\joust-lsp.js}; a {@code .cmd} is refused.</li>
     * </ul>
     * Either way the result must be {@code .../@julclang/joust/bin/joust-lsp.js}, whose package.json names
     * {@code @julclang/joust}, outside every forbidden root and every {@code node_modules/.bin}.
     */
    public static Result resolveJoustLsp(String configured, Collection<Path> forbiddenRoots, JoustPaths.Flavor flavor) {
        return resolveJoustLsp(configured, forbiddenRoots, flavor, flavor == JoustPaths.Flavor.WINDOWS);
    }

    /**
     * As {@link #resolveJoustLsp(String, Collection, JoustPaths.Flavor)}, with the Windows package-layout lookup chosen
     * explicitly, so it can be tested on any platform.
     */
    public static Result resolveJoustLsp(String configured, Collection<Path> forbiddenRoots, JoustPaths.Flavor flavor,
                                         boolean windowsLayout) {
        Result base = checkAbsolute(configured, flavor, "joust-lsp");
        if (!base.ok()) return base;
        Path selected = base.selected();
        if (!Files.exists(selected)) return Result.fail(selected, "joust-lsp was not found at " + selected + ".");
        Path candidate = selected;
        if (windowsLayout && JoustPaths.fileName(selected.toString()).equalsIgnoreCase("joust-lsp")) {
            Path parent = selected.getParent();
            if (parent == null) return Result.fail(selected, "joust-lsp has no npm prefix directory.");
            candidate = parent.resolve("node_modules").resolve("@julclang").resolve("joust").resolve("bin").resolve("joust-lsp.js");
        }
        Path real;
        try {
            real = candidate.toRealPath();
        } catch (IOException e) {
            return Result.fail(selected, "joust-lsp's package file was not found: " + candidate);
        }
        if (JoustPaths.hasRefusedExtension(real.toString())) {
            return Result.fail(selected, "joust-lsp resolves to a script shim, which is never run: " + real);
        }
        if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS) || !real.getFileName().toString().equals("joust-lsp.js")) {
            return Result.fail(selected, "joust-lsp must resolve to @julclang/joust's bin/joust-lsp.js, not " + real + ".");
        }
        Path bin = real.getParent();
        Path pkg = bin == null ? null : bin.getParent();
        // D4: the global npm layout, <prefix>/lib/node_modules/@julclang/joust/bin/joust-lsp.js (Windows:
        // <prefix>\node_modules\@julclang\joust\bin\joust-lsp.js), not any checkout that happens to look alike (M1-CDX-30).
        Path scope = pkg == null ? null : pkg.getParent();
        Path nodeModules = scope == null ? null : scope.getParent();
        // Names are compared without dereferencing a root's null file name (M1-CDX-30).
        if (!named(bin, "bin") || !named(pkg, "joust") || !named(scope, "@julclang") || !named(nodeModules, "node_modules")
                || (flavor != JoustPaths.Flavor.WINDOWS && !windowsLayout && !named(nodeModules.getParent(), "lib"))) {
            return Result.fail(selected, "joust-lsp.js is not in npm's global layout (" + (flavor == JoustPaths.Flavor.WINDOWS || windowsLayout
                    ? "<prefix>\\node_modules\\@julclang\\joust\\bin\\joust-lsp.js" : "<prefix>/lib/node_modules/@julclang/joust/bin/joust-lsp.js")
                    + "): install it with npm install -g @julclang/joust (pnpm and Yarn global installs are not supported): " + real);
        }
        String name = readPackageField(pkg.resolve("package.json"), "name");
        if (!PACKAGE_NAME.equals(name)) {
            return Result.fail(selected, "joust-lsp.js is not part of the " + PACKAGE_NAME + " package: " + real);
        }
        String where = locationProblem(selected, real, forbiddenRoots, flavor);
        if (where != null) return Result.fail(selected, "joust-lsp " + where);
        return new Result(selected, real, null);
    }

    /**
     * True only for an executable in <b>this host's</b> native format, structurally checked (M1-CDX-5): every offset is
     * read as unsigned, bounds are computed in {@code long}, and tables must lie inside the file.
     * <ul>
     *   <li><b>macOS:</b> a thin Mach-O of type {@code MH_EXECUTE} with a bounded, non-empty load-command table whose
     *   first command fits; or a universal file (1–8 slices, each inside the file) whose every slice is such a Mach-O.</li>
     *   <li><b>Linux:</b> ELF, 32- or 64-bit, of type {@code ET_EXEC} or {@code ET_DYN}, with a machine, and a non-empty
     *   program-header table of the right entry size inside the file.</li>
     *   <li><b>Windows:</b> {@code MZ}, then {@code PE\0\0} at {@code e_lfanew} inside the file, a machine, at least one
     *   section, an optional header, and the executable-image characteristic.</li>
     * </ul>
     * Anything else, truncated or foreign, is refused: the JDK would otherwise run some files through {@code /bin/sh}.
     */
    public static boolean named(Path p, String name) {
        Path f = p == null ? null : p.getFileName();
        return f != null && f.toString().equals(name);
    }

    static boolean isNativeExecutable(Path real) {
        return isNativeExecutable(real, JoustPaths.Flavor.current());
    }

    public static boolean isNativeExecutable(Path real, JoustPaths.Flavor host) {
        try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(real, java.nio.file.StandardOpenOption.READ)) {
            long size = ch.size();
            return switch (host) {
                case MAC -> macho(ch, size);
                case UNIX -> elf(ch, size);
                case WINDOWS -> pe(ch, size);
            };
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static java.nio.ByteBuffer read(java.nio.channels.FileChannel ch, long size, long at, int n) throws IOException {
        if (at < 0 || n < 0 || at + n > size) return null;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(n);
        while (b.hasRemaining()) {
            if (ch.read(b, at + b.position()) < 0) return null;
        }
        return b.flip();
    }

    private static boolean macho(java.nio.channels.FileChannel ch, long size) throws IOException {
        java.nio.ByteBuffer h = read(ch, size, 0, 8);
        if (h == null) return false;
        int magic = h.getInt(0);
        if (magic == 0xCAFEBABE || magic == 0xCAFEBABF) {
            boolean wide = magic == 0xCAFEBABF;
            long n = Integer.toUnsignedLong(h.getInt(4));
            if (n < 1 || n > 8) return false;   // a Java class file has its version here (M1-DES-19)
            int entry = wide ? 32 : 20;
            java.nio.ByteBuffer t = read(ch, size, 8, (int) n * entry);
            if (t == null) return false;
            for (int k = 0; k < n; k++) {
                long off = wide ? t.getLong(k * entry + 8) : Integer.toUnsignedLong(t.getInt(k * entry + 8));
                long len = wide ? t.getLong(k * entry + 16) : Integer.toUnsignedLong(t.getInt(k * entry + 12));
                if (off < 8 + n * entry || len < 32 || off > size || len > size - off) return false;
                if (!thinMacho(ch, off, off + len)) return false;
            }
            return true;
        }
        return thinMacho(ch, 0, size);
    }

    private static boolean thinMacho(java.nio.channels.FileChannel ch, long start, long end) throws IOException {
        java.nio.ByteBuffer h = read(ch, end, start, 32);
        if (h == null) return false;
        int magic = h.getInt(0);
        boolean is64;
        if (magic == 0xFEEDFACE || magic == 0xFEEDFACF) {
            h.order(java.nio.ByteOrder.BIG_ENDIAN);
        } else if (magic == 0xCEFAEDFE || magic == 0xCFFAEDFE) {
            h.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        } else {
            return false;
        }
        is64 = magic == 0xFEEDFACF || magic == 0xCFFAEDFE;
        int headerSize = is64 ? 32 : 28;
        int fileType = h.getInt(12);
        long ncmds = Integer.toUnsignedLong(h.getInt(16));
        long sizeofcmds = Integer.toUnsignedLong(h.getInt(20));
        if (fileType != 2 /* MH_EXECUTE */ || ncmds < 1 || ncmds > 10_000 || sizeofcmds < 8 * ncmds
                || sizeofcmds > end - start - headerSize) {
            return false;
        }
        java.nio.ByteBuffer cmd = read(ch, end, start + headerSize, 8);
        if (cmd == null) return false;
        cmd.order(h.order());
        long cmdsize = Integer.toUnsignedLong(cmd.getInt(4));
        return cmdsize >= 8 && cmdsize <= sizeofcmds;
    }

    private static boolean elf(java.nio.channels.FileChannel ch, long size) throws IOException {
        java.nio.ByteBuffer h = read(ch, size, 0, 64);
        if (h == null || h.getInt(0) != 0x7F454C46) return false;
        int cls = h.get(4);
        int data = h.get(5);
        if ((cls != 1 && cls != 2) || (data != 1 && data != 2) || h.get(6) != 1) return false;
        h.order(data == 1 ? java.nio.ByteOrder.LITTLE_ENDIAN : java.nio.ByteOrder.BIG_ENDIAN);
        int type = Short.toUnsignedInt(h.getShort(16));
        int machine = Short.toUnsignedInt(h.getShort(18));
        long phoff;
        int phentsize;
        int phnum;
        if (cls == 2) {
            phoff = h.getLong(32);
            phentsize = Short.toUnsignedInt(h.getShort(54));
            phnum = Short.toUnsignedInt(h.getShort(56));
            if (phentsize != 56) return false;
        } else {
            phoff = Integer.toUnsignedLong(h.getInt(28));
            phentsize = Short.toUnsignedInt(h.getShort(42));
            phnum = Short.toUnsignedInt(h.getShort(44));
            if (phentsize != 32) return false;
        }
        return (type == 2 || type == 3) && machine != 0 && phnum >= 1 && phoff >= (cls == 2 ? 64 : 52) && phoff <= size
                && (long) phnum * phentsize <= size - phoff;
    }

    private static boolean pe(java.nio.channels.FileChannel ch, long size) throws IOException {
        java.nio.ByteBuffer h = read(ch, size, 0, 64);
        if (h == null || h.get(0) != 'M' || h.get(1) != 'Z') return false;
        h.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        long off = Integer.toUnsignedLong(h.getInt(0x3C));
        if (off < 0x40) return false;
        java.nio.ByteBuffer p = read(ch, size, off, 24);
        if (p == null || p.get(0) != 'P' || p.get(1) != 'E' || p.get(2) != 0 || p.get(3) != 0) return false;
        p.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int machine = Short.toUnsignedInt(p.getShort(4));
        int sections = Short.toUnsignedInt(p.getShort(6));
        int optional = Short.toUnsignedInt(p.getShort(20));
        int characteristics = Short.toUnsignedInt(p.getShort(22));
        if (machine == 0 || sections < 1 || optional < 2 || (characteristics & 0x2) == 0) return false;
        // The optional header is PE32 (0x10b) or PE32+ (0x20b), and the whole section table (40 bytes per section)
        // follows it inside the file (M1-CDX-5).
        java.nio.ByteBuffer m = read(ch, size, off + 24, 2);
        if (m == null) return false;
        int format = Short.toUnsignedInt(m.order(java.nio.ByteOrder.LITTLE_ENDIAN).getShort(0));
        if (format != 0x10b && format != 0x20b) return false;
        long tableEnd = off + 24 + optional + 40L * sections;
        return tableEnd <= size;
    }

    /** True when the file is not a native executable (kept for callers that ask the opposite question). */
    static boolean isScript(Path real) {
        return !isNativeExecutable(real);
    }

    /** The {@code @julclang/joust} version, read as text from the package.json beside the package; never by running it. */
    public static String readJoustVersion(Path realJoustLspJs) {
        Path bin = realJoustLspJs.getParent();
        Path pkg = bin == null ? null : bin.getParent();
        if (pkg == null) return null;
        return readPackageField(pkg.resolve("package.json"), "version");
    }

    /** The major version of a {@code node --version} answer such as {@code v22.12.0}, or -1. */
    public static int nodeMajor(String version) {
        if (version == null) return -1;
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        int dot = v.indexOf('.');
        String major = dot < 0 ? v : v.substring(0, dot);
        try {
            return Integer.parseInt(major);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The identity recorded when the user validates Node: real path, size, modification time and SHA-256. */
    public static NodeIdentity identityOf(Path real) throws IOException {
        long size = Files.size(real);
        long mtime = Files.getLastModifiedTime(real).toMillis();
        return new NodeIdentity(real.toString(), size, mtime, sha256(real));
    }

    public static String sha256(Path file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Why a selected path or its real target is in a forbidden place, or {@code null}. The checks run on both: a symlink
     * from outside into a project and one from a project out are both refused.
     */
    public static String locationProblem(Path selected, Path real, Collection<Path> forbiddenRoots, JoustPaths.Flavor flavor) {
        for (Path p : new Path[]{selected, real}) {
            String s = p.toString();
            if (JoustPaths.isUnderNodeModulesBin(s, flavor)) {
                return "must not be under a node_modules/.bin directory: " + s;
            }
            for (Path root : forbiddenRoots) {
                if (JoustPaths.isInside(s, root.toString(), flavor)) {
                    return "must not be inside an open project (" + root + "): " + s;
                }
            }
        }
        return null;
    }

    private static Result checkAbsolute(String configured, JoustPaths.Flavor flavor, String what) {
        if (configured == null || configured.isBlank()) return Result.fail(null, what + " is not configured.");
        String s = configured.trim();
        if (!JoustPaths.isAbsolute(s, flavor)) {
            return Result.fail(null, what + " must be an absolute path, not \"" + s + "\" (no bare names, relative paths or npx).");
        }
        if (JoustPaths.hasRefusedExtension(s)) {
            return Result.fail(null, what + " must not be a .cmd, .bat or .ps1 script: " + s);
        }
        try {
            return new Result(Path.of(s), null, null);
        } catch (RuntimeException e) {
            return Result.fail(null, what + " is not a valid path: " + s);
        }
    }

    static String readPackageField(Path packageJson, String field) {
        try {
            if (!Files.isRegularFile(packageJson) || Files.size(packageJson) > PACKAGE_JSON_CAP) return null;
            String text = Files.readString(packageJson, StandardCharsets.UTF_8);
            Object v = new JSONObject(text).opt(field);
            return v instanceof String s ? s : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
