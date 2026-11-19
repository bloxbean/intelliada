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
        Result base = checkAbsolute(configured, flavor, "Node");
        if (!base.ok()) return base;
        Path selected = base.selected();
        if (!Files.exists(selected)) return Result.fail(selected, "Node was not found at " + selected + ".");
        Path real;
        try {
            real = selected.toRealPath();
        } catch (IOException e) {
            return Result.fail(selected, "Node's real path could not be resolved: " + e.getMessage());
        }
        if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) return Result.fail(selected, "Node must be a file: " + real);
        if (!Files.isExecutable(real)) return Result.fail(selected, "Node is not executable: " + real);
        if (JoustPaths.hasRefusedExtension(real.toString()) || !isNativeExecutable(real)) {
            return Result.fail(selected, "Node resolves to a script, not the node binary: " + real
                    + " (a version-manager or npm shim chooses what it runs; select the installed binary).");
        }
        String selectedName = selected.getFileName().toString().toLowerCase(java.util.Locale.ROOT).replaceAll("\\.(exe|cmd|bat|ps1|js)$", "");
        if (!NODE_NAMES.contains(real.getFileName().toString().toLowerCase(java.util.Locale.ROOT))
                || NOT_NODE.contains(selectedName)) {
            return Result.fail(selected, "Node must be the node binary itself (named node, node.exe or nodejs), not "
                    + selected.getFileName() + " -> " + real.getFileName() + ": no npx, npm or version-manager shim.");
        }
        String where = locationProblem(selected, real, forbiddenRoots, flavor);
        if (where != null) return Result.fail(selected, "Node " + where);
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
        if (bin == null || !bin.getFileName().toString().equals("bin") || pkg == null || !pkg.getFileName().toString().equals("joust")
                || scope == null || !scope.getFileName().toString().equals("@julclang") || nodeModules == null
                || !nodeModules.getFileName().toString().equals("node_modules")
                || (flavor != JoustPaths.Flavor.WINDOWS && !windowsLayout && (nodeModules.getParent() == null
                        || !nodeModules.getParent().getFileName().toString().equals("lib")))) {
            return Result.fail(selected, "joust-lsp.js is not in a global npm installation of @julclang/joust: " + real);
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
     * True only for an executable in <b>this host's</b> native format, structurally checked (M1-CDX-5): Mach-O on macOS
     * (thin, or universal with a plausible architecture count), ELF on Linux (a 32- or 64-bit executable or shared object),
     * PE on Windows ({@code MZ} and a {@code PE\0\0} signature where its header says). A truncated or foreign file is
     * refused; the JDK would otherwise run some through {@code /bin/sh}.
     */
    static boolean isNativeExecutable(Path real) {
        return isNativeExecutable(real, JoustPaths.Flavor.current());
    }

    public static boolean isNativeExecutable(Path real, JoustPaths.Flavor host) {
        byte[] h;
        try (InputStream in = Files.newInputStream(real)) {
            h = in.readNBytes(4096);
        } catch (IOException e) {
            return false;
        }
        if (h.length < 64) return false;
        int be = u32be(h, 0);
        return switch (host) {
            case MAC -> be == 0xFEEDFACE || be == 0xFEEDFACF || be == 0xCEFAEDFE || be == 0xCFFAEDFE
                    || ((be == 0xCAFEBABE || be == 0xCAFEBABF) && u32be(h, 4) >= 1 && u32be(h, 4) <= 8);
            case UNIX -> be == 0x7F454C46 && (h[4] == 1 || h[4] == 2) && (h[5] == 1 || h[5] == 2)
                    && (elfType(h) == 2 || elfType(h) == 3);
            case WINDOWS -> h[0] == 'M' && h[1] == 'Z' && pe(h);
        };
    }

    private static int u32be(byte[] h, int o) {
        return ((h[o] & 0xff) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
    }

    private static int elfType(byte[] h) {
        return h[5] == 1 ? (h[16] & 0xff) | ((h[17] & 0xff) << 8) : ((h[16] & 0xff) << 8) | (h[17] & 0xff);
    }

    private static boolean pe(byte[] h) {
        int off = (h[0x3C] & 0xff) | ((h[0x3D] & 0xff) << 8) | ((h[0x3E] & 0xff) << 16) | ((h[0x3F] & 0xff) << 24);
        return off >= 0x40 && off + 4 <= h.length && h[off] == 'P' && h[off + 1] == 'E' && h[off + 2] == 0 && h[off + 3] == 0;
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
