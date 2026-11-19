package com.bloxbean.intelliada.idea.joust.project;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADR-004 D7: a project's JOUST layout, found by <b>reading files as text, never by running a build</b>.
 *
 * <ul>
 *   <li><b>Gradle:</b> a directory whose {@code build.gradle(.kts)} names the plugin {@code org.julclang.joust}. Its
 *   {@code .jsc} sources are under {@code src/main/jsc}, and {@code generateJsc} writes to
 *   {@code build/generated/sources/jsc/java/main} (F19). The plugin's version literal is read for display.</li>
 *   <li><b>npm:</b> a directory with {@code joust.config.json}, or a {@code package.json} that depends on
 *   {@code @julclang/joust} (F31). From {@code joust.config.json} only {@code contracts} is read (D7), never
 *   {@code networks}, URLs or {@code out}. Whether the project's CLI is installed is tracked separately.</li>
 * </ul>
 * Every path here is a real path inside the project's base directory; anything that resolves outside is ignored.
 */
public record JoustProjectLayout(Path base, List<GradleModule> gradleModules, List<NpmRoot> npmRoots, boolean complete) {

    /** A layout whose scan reached every directory it looks at. */
    public JoustProjectLayout(Path base, List<GradleModule> gradleModules, List<NpmRoot> npmRoots) {
        this(base, gradleModules, npmRoots, true);
    }

    public static final String PLUGIN_ID = "org.julclang.joust";
    static final int MAX_DEPTH = 8;
    static final int MAX_DIRS = 5_000;
    static final long TEXT_CAP = 1024 * 1024;
    // Never a project root: build output, dependencies and IDE metadata.
    static final Set<String> SKIP = Set.of(".git", ".gradle", ".idea", "build", "node_modules", "out", "target", "dist", ".joust");
    /** Gradle source sets under {@code src/}: never a project root; anything else under {@code src/} (an app) is scanned. */
    static final Set<String> SOURCE_SETS = Set.of("main", "test", "testFixtures", "integrationTest");
    private static final Pattern VERSION = Pattern.compile(
            "org\\.julclang\\.joust['\"]\\s*\\)?\\s*version\\s*\\(?\\s*['\"]([A-Za-z0-9._+-]{1,40})['\"]");

    /** A Gradle project that applies {@code org.julclang.joust}. */
    public record GradleModule(Path dir, String pluginVersion) {
        /** True when the module has {@code src/main/jsc}: only then does the plugin register the .jsc tasks (JoustPlugin). */
        public boolean hasJsc() {
            return Files.isDirectory(jscRoot());
        }

        public Path jscRoot() {
            return dir.resolve("src").resolve("main").resolve("jsc");
        }

        public Path generatedRoot() {
            return dir.resolve("build").resolve("generated").resolve("sources").resolve("jsc").resolve("java").resolve("main");
        }
    }

    /** An npm JOUST root; {@code cli} is the real path of the project's own {@code joust.js}, or null if not installed. */
    public record NpmRoot(Path dir, Path contractsDir, Path cli) {
        public boolean cliInstalled() {
            return cli != null;
        }
    }

    /**
     * Where a kind of JOUST project keeps its {@code .jsc} sources and writes generated Java: a Gradle module
     * ({@code src/main/jsc}, {@code build/generated/sources/jsc/java/main}) or an npm root (its {@code contracts},
     * {@code build/joust/generated}; {@code out} is never read, D7). Exactly one of {@code gradle} and {@code npm} is set.
     */
    public record SourceRoot(Path ownerDir, Path contracts, Path generated, GradleModule gradle, NpmRoot npm) {
        /**
         * The real contracts directory, verified to belong to its owner (M2-CDX-9): for Gradle exactly
         * {@code <module>/src/main/jsc} with no link below the module; for npm inside the npm root; and inside
         * {@code base}. Null otherwise.
         */
        public Path verifiedContracts(Path base) {
            return verified(gradle != null ? ownerDir.relativize(contracts) : null, contracts, base);
        }

        /** The real generated-sources directory, verified the same way (exactly its spelled path under the owner). */
        public Path verifiedGenerated(Path base) {
            return verified(ownerDir.relativize(generated), generated, base);
        }

        private Path verified(Path exactRel, Path p, Path base) {
            try {
                Path ownerReal = ownerDir.toRealPath();
                Path real = p.toRealPath();
                if (!com.bloxbean.intelliada.idea.joust.core.JoustRealPaths.isInside(ownerReal, base)) return null;
                // Exact spelling, not Path.equals (which folds case on Windows), M2-CDX-9.
                if (exactRel != null) return real.toString().equals(ownerReal.resolve(exactRel).toString()) ? real : null;
                return com.bloxbean.intelliada.idea.joust.core.JoustRealPaths.isInside(real, ownerReal) ? real : null;
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }
    }

    /** Every source root: the Gradle modules', then the npm roots'. */
    public List<SourceRoot> sourceRoots() {
        List<SourceRoot> out = new ArrayList<>();
        for (GradleModule m : gradleModules) out.add(new SourceRoot(m.dir(), m.jscRoot(), m.generatedRoot(), m, null));
        for (NpmRoot r : npmRoots) {
            out.add(new SourceRoot(r.dir(), r.contractsDir(), r.dir().resolve("build").resolve("joust").resolve("generated"), null, r));
        }
        return out;
    }

    /** The source root whose generated directory contains {@code realFile} (by spelled path; verify before use), or null. */
    public SourceRoot rootGenerating(Path realFile, JoustPaths.Flavor flavor) {
        for (SourceRoot r : sourceRoots()) {
            if (JoustPaths.isInside(realFile.toString(), r.generated().toString(), flavor)) return r;
        }
        return null;
    }

    public boolean isJoust() {
        return !gradleModules.isEmpty() || !npmRoots.isEmpty();
    }

    /** The Gradle module whose {@code src/main/jsc} contains {@code realFile}, or null. */
    public GradleModule gradleModuleOwning(Path realFile, JoustPaths.Flavor flavor) {
        GradleModule best = null;
        for (GradleModule m : gradleModules) {
            if (JoustPaths.isInside(realFile.toString(), m.jscRoot().toString(), flavor)
                    && (best == null || m.dir().getNameCount() > best.dir().getNameCount())) {
                best = m;
            }
        }
        return best;
    }

    /** The Gradle module whose generated-sources root contains {@code realFile}, or null. */
    public GradleModule gradleModuleGenerating(Path realFile, JoustPaths.Flavor flavor) {
        for (GradleModule m : gradleModules) {
            if (JoustPaths.isInside(realFile.toString(), m.generatedRoot().toString(), flavor)) return m;
        }
        return null;
    }

    /** The npm root whose contracts directory contains {@code realFile}, or null. */
    public NpmRoot npmRootOwning(Path realFile, JoustPaths.Flavor flavor) {
        NpmRoot best = null;
        for (NpmRoot r : npmRoots) {
            if (JoustPaths.isInside(realFile.toString(), r.contractsDir().toString(), flavor)
                    && (best == null || r.dir().getNameCount() > best.dir().getNameCount())) {
                best = r;
            }
        }
        return best;
    }

    /** Scans {@code base} (a real path), bounded in depth and directory count, reading files as text only. */
    public static JoustProjectLayout scan(Path base, JoustPaths.Flavor flavor) {
        List<GradleModule> gradle = new ArrayList<>();
        List<NpmRoot> npm = new ArrayList<>();
        List<Path> dirs = new ArrayList<>();
        boolean[] truncated = new boolean[1];
        collect(base, 0, dirs, truncated);
        // A build's settings file may name the plugin (and its version) for the whole build (D7, F31).
        java.util.Map<Path, String> settingsVersion = new java.util.HashMap<>();
        java.util.Set<Path> settingsNamesPlugin = new java.util.HashSet<>();
        for (Path dir : dirs) {
            // A detection file that exists but cannot be read within the budget leaves the scan incomplete (M2-CDX-7).
            for (String f : new String[]{"settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts", "package.json",
                    "joust.config.json"}) {
                if (unreadable(dir.resolve(f))) truncated[0] = true;
            }
            for (String settings : new String[]{"settings.gradle", "settings.gradle.kts"}) {
                String text = readText(dir.resolve(settings));
                if (text != null && text.contains(PLUGIN_ID)) {
                    settingsNamesPlugin.add(dir);
                    settingsVersion.put(dir, version(text));
                }
            }
        }
        for (Path dir : dirs) {
            String inherited = null;
            for (Path d = dir; d != null && d.startsWith(base); d = d.getParent()) {
                if (settingsVersion.containsKey(d)) {
                    inherited = settingsVersion.get(d);
                    break;
                }
            }
            boolean added = false;
            for (String build : new String[]{"build.gradle", "build.gradle.kts"}) {
                String text = readText(dir.resolve(build));
                if (text != null && text.contains(PLUGIN_ID)) {
                    String v = version(text);
                    gradle.add(new GradleModule(dir, v != null ? v : inherited));
                    added = true;
                    break;
                }
            }
            // Settings-only: the build's root directory is a JOUST Gradle module (D7: "or settings.gradle(.kts)").
            if (!added && settingsNamesPlugin.contains(dir)) gradle.add(new GradleModule(dir, inherited));
            NpmRoot n = npmRoot(dir, base, flavor);
            if (n != null) npm.add(n);
        }
        gradle.sort(Comparator.comparing(m -> m.dir().toString()));
        npm.sort(Comparator.comparing(r -> r.dir().toString()));
        return new JoustProjectLayout(base, List.copyOf(gradle), List.copyOf(npm), !truncated[0]);
    }

    /** What owns a file: the nearest npm root or JOUST Gradle module walking up from it; npm wins in one directory. */
    public record Owner(GradleModule gradle, NpmRoot npm) {
    }

    /**
     * The owner of {@code realFile}, as JOUST's own {@code project.js} decides (ADR-0017 D4): walking up from the file,
     * the first directory that is an npm root or part of a Gradle build decides, and npm wins in one directory. A
     * Gradle build that does not apply the plugin owns the file too, so the result is then null. Reads only file
     * existence; off the EDT.
     */
    public Owner owner(Path realFile) {
        for (Path d = Files.isDirectory(realFile) ? realFile : realFile.getParent(); d != null && d.startsWith(base); d = d.getParent()) {
            for (NpmRoot r : npmRoots) {
                if (r.dir().equals(d)) return new Owner(null, r);
            }
            // An npm root the bounded scan did not reach still decides, as in project.js (M2-CDX-12).
            NpmRoot live = npmRoot(d, base, JoustPaths.Flavor.current());
            if (live != null) return new Owner(null, live);
            for (GradleModule m : gradleModules) {
                if (m.dir().equals(d)) return new Owner(m, null);
            }
            for (String f : new String[]{"settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts"}) {
                if (Files.isRegularFile(d.resolve(f), LinkOption.NOFOLLOW_LINKS)) return null;
            }
        }
        return null;
    }

    static String version(String text) {
        Matcher m = VERSION.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static NpmRoot npmRoot(Path dir, Path base, JoustPaths.Flavor flavor) {
        Path config = dir.resolve("joust.config.json");
        boolean hasConfig = Files.isRegularFile(config, LinkOption.NOFOLLOW_LINKS);
        boolean depends = false;
        String pkg = readText(dir.resolve("package.json"));
        if (pkg != null) {
            try {
                JSONObject o = new JSONObject(pkg);
                for (String section : new String[]{"dependencies", "devDependencies"}) {
                    JSONObject deps = o.optJSONObject(section);
                    if (deps != null && deps.has("@julclang/joust")) depends = true;
                }
            } catch (RuntimeException ignored) {
                // not JSON: not a JOUST root by its package.json
            }
        }
        if (!hasConfig && !depends) return null;
        String contracts = "contracts";
        if (hasConfig) {
            String text = readText(config);
            if (text != null) {
                try {
                    Object c = new JSONObject(text).opt("contracts");
                    if (c instanceof String s && !s.isBlank()) contracts = s;
                } catch (RuntimeException ignored) {
                    // unreadable config: the default directory
                }
            }
        }
        Path contractsDir = confined(dir, contracts, base, flavor);
        if (contractsDir == null) return null;
        Path cli = null;
        Path candidate = dir.resolve("node_modules").resolve("@julclang").resolve("joust").resolve("bin").resolve("joust.js");
        try {
            Path real = candidate.toRealPath();
            if (Files.isRegularFile(real) && JoustPaths.isInside(real.toString(), base.toString(), flavor)) cli = real;
        } catch (IOException ignored) {
            // not installed
        }
        return new NpmRoot(dir, contractsDir, cli);
    }

    /** {@code dir/rel} if {@code rel} is relative, has no {@code ..}, and its real path (or its normalised path, if absent) is inside {@code base}. */
    static Path confined(Path dir, String rel, Path base, JoustPaths.Flavor flavor) {
        if (rel.isEmpty() || rel.indexOf('\0') >= 0 || JoustPaths.isAbsolute(rel, flavor) || rel.startsWith("/") || rel.startsWith("\\")
                || rel.matches("^[A-Za-z]:.*")) {
            return null;
        }
        for (String seg : rel.split("[/\\\\]")) {
            if (seg.equals("..")) return null;
        }
        Path p = dir.resolve(rel).normalize();
        Path real;
        try {
            real = p.toRealPath();
        } catch (IOException e) {
            real = p;
        }
        return JoustPaths.isInside(real.toString(), base.toString(), flavor) ? real : null;
    }

    /** Directories to look at; {@code truncated[0]} is set when a limit stopped the scan before it saw everything. */
    private static void collect(Path dir, int depth, List<Path> out, boolean[] truncated) {
        if (out.size() >= MAX_DIRS) {
            truncated[0] = true;
            return;
        }
        out.add(dir);
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path c : ds) {
                String name = c.getFileName().toString();
                boolean sourceSet = "src".equals(String.valueOf(dir.getFileName())) && SOURCE_SETS.contains(name);
                if (Files.isDirectory(c, LinkOption.NOFOLLOW_LINKS) && !SKIP.contains(name) && !sourceSet) children.add(c);
            }
        } catch (IOException ignored) {
            truncated[0] = true;
            return;
        }
        if (depth >= MAX_DEPTH) {
            if (!children.isEmpty()) truncated[0] = true;
            return;
        }
        children.sort(null);
        for (Path c : children) collect(c, depth + 1, out, truncated);
    }

    /** True when {@code p} exists but is not a regular file within {@link #TEXT_CAP}, or cannot be read. */
    static boolean unreadable(Path p) {
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return false;
        try {
            return !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || Files.size(p) > TEXT_CAP || !Files.isReadable(p);
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }

    static String readText(Path p) {
        try {
            if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) || Files.size(p) > TEXT_CAP) return null;
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
