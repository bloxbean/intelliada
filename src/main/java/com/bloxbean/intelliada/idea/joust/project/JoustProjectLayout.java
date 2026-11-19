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
public record JoustProjectLayout(Path base, List<GradleModule> gradleModules, List<NpmRoot> npmRoots) {

    public static final String PLUGIN_ID = "org.julclang.joust";
    static final int MAX_DEPTH = 5;
    static final int MAX_DIRS = 5_000;
    static final long TEXT_CAP = 1024 * 1024;
    static final Set<String> SKIP = Set.of(".git", ".gradle", ".idea", "build", "node_modules", "out", "target", "dist", ".joust");
    private static final Pattern VERSION = Pattern.compile(
            "org\\.julclang\\.joust['\"]\\s*\\)?\\s*version\\s*\\(?\\s*['\"]([A-Za-z0-9._+-]{1,40})['\"]");

    /** A Gradle project that applies {@code org.julclang.joust}. */
    public record GradleModule(Path dir, String pluginVersion) {
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
        String settingsVersion = null;
        for (String settings : new String[]{"settings.gradle", "settings.gradle.kts"}) {
            String text = readText(base.resolve(settings));
            if (text != null && text.contains(PLUGIN_ID)) settingsVersion = version(text);
        }
        List<Path> dirs = new ArrayList<>();
        collect(base, 0, dirs);
        for (Path dir : dirs) {
            for (String build : new String[]{"build.gradle", "build.gradle.kts"}) {
                String text = readText(dir.resolve(build));
                if (text != null && text.contains(PLUGIN_ID)) {
                    String v = version(text);
                    gradle.add(new GradleModule(dir, v != null ? v : settingsVersion));
                    break;
                }
            }
            NpmRoot n = npmRoot(dir, base, flavor);
            if (n != null) npm.add(n);
        }
        gradle.sort(Comparator.comparing(m -> m.dir().toString()));
        npm.sort(Comparator.comparing(r -> r.dir().toString()));
        return new JoustProjectLayout(base, List.copyOf(gradle), List.copyOf(npm));
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

    private static void collect(Path dir, int depth, List<Path> out) {
        if (out.size() >= MAX_DIRS) return;
        out.add(dir);
        if (depth >= MAX_DEPTH) return;
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path c : ds) {
                if (Files.isDirectory(c, LinkOption.NOFOLLOW_LINKS) && !SKIP.contains(c.getFileName().toString())) children.add(c);
            }
        } catch (IOException ignored) {
            return;
        }
        children.sort(null);
        for (Path c : children) collect(c, depth + 1, out);
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
