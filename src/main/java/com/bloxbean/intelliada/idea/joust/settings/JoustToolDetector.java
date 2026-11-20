package com.bloxbean.intelliada.idea.joust.settings;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ADR-004 D4's Detect…: candidate tool paths found by <b>reading directories</b>, never by running {@code npm} or a
 * shell. Candidates come from absolute {@code PATH} entries and the usual global layouts (nvm, Volta, asdf, fnm,
 * Homebrew, {@code /usr/local}, {@code %APPDATA%\npm}).
 *
 * <p>Version-manager <b>shims</b> (Volta's and asdf's, mise's, nodenv's) are skipped: a shim picks the Node it runs from
 * the directory it is started in. The installed binaries those managers keep are offered instead.
 */
public final class JoustToolDetector {

    private JoustToolDetector() {
    }

    public static List<Path> nodeCandidates(Map<String, String> env, Path home, JoustPaths.Flavor flavor) {
        String exe = flavor == JoustPaths.Flavor.WINDOWS ? "node.exe" : "node";
        Set<Path> out = new LinkedHashSet<>();
        for (Path dir : searchDirs(env, home, flavor)) addIfFile(out, dir.resolve(exe));
        if (home != null) {
            for (Path v : children(home.resolve(".nvm").resolve("versions").resolve("node"))) addIfFile(out, v.resolve("bin").resolve(exe));
            for (Path v : children(home.resolve(".volta").resolve("tools").resolve("image").resolve("node"))) {
                addIfFile(out, v.resolve("bin").resolve(exe));
                addIfFile(out, v.resolve(exe));
            }
            for (Path v : children(home.resolve(".asdf").resolve("installs").resolve("nodejs"))) addIfFile(out, v.resolve("bin").resolve(exe));
            for (Path fnm : new Path[]{home.resolve(".local").resolve("share").resolve("fnm"),
                    home.resolve("Library").resolve("Application Support").resolve("fnm"), home.resolve(".fnm")}) {
                for (Path v : children(fnm.resolve("node-versions"))) addIfFile(out, v.resolve("installation").resolve("bin").resolve(exe));
            }
        }
        if (flavor == JoustPaths.Flavor.WINDOWS) {
            String pf = env.get("ProgramFiles");
            if (pf != null) addIfFile(out, Path.of(pf, "nodejs", exe));
        } else {
            addIfFile(out, Path.of("/opt/homebrew/bin/node"));
            addIfFile(out, Path.of("/usr/local/bin/node"));
            addIfFile(out, Path.of("/usr/bin/node"));
        }
        return new ArrayList<>(out);
    }

    public static List<Path> joustLspCandidates(Map<String, String> env, Path home, JoustPaths.Flavor flavor) {
        Set<Path> out = new LinkedHashSet<>();
        String shim = "joust-lsp";
        for (Path dir : searchDirs(env, home, flavor)) {
            addIfFile(out, dir.resolve(shim));
            if (flavor == JoustPaths.Flavor.WINDOWS) {
                addIfFile(out, dir.resolve("node_modules").resolve("@julclang").resolve("joust").resolve("bin").resolve("joust-lsp.js"));
            }
        }
        if (home != null) {
            for (Path v : children(home.resolve(".nvm").resolve("versions").resolve("node"))) addIfFile(out, v.resolve("bin").resolve(shim));
            for (Path fnm : new Path[]{home.resolve(".local").resolve("share").resolve("fnm"),
                    home.resolve("Library").resolve("Application Support").resolve("fnm"), home.resolve(".fnm")}) {
                for (Path v : children(fnm.resolve("node-versions"))) addIfFile(out, v.resolve("installation").resolve("bin").resolve(shim));
            }
        }
        if (flavor == JoustPaths.Flavor.WINDOWS) {
            String appData = env.get("APPDATA");
            if (appData != null) addIfFile(out, Path.of(appData, "npm", shim));
        } else {
            addIfFile(out, Path.of("/opt/homebrew/bin/joust-lsp"));
            addIfFile(out, Path.of("/usr/local/bin/joust-lsp"));
        }
        return new ArrayList<>(out);
    }

    /** Absolute PATH entries, without version-manager shim directories. */
    static List<Path> searchDirs(Map<String, String> env, Path home, JoustPaths.Flavor flavor) {
        List<Path> dirs = new ArrayList<>();
        String path = env.getOrDefault("PATH", env.getOrDefault("Path", ""));
        for (String entry : path.split(File.pathSeparator)) {
            String e = entry.trim();
            if (e.isEmpty() || !JoustPaths.isAbsolute(e, flavor) || isShimDir(e, flavor)) continue;
            try {
                dirs.add(Path.of(e));
            } catch (RuntimeException ignored) {
                // not a path on this platform
            }
        }
        return dirs;
    }

    static boolean isShimDir(String dir, JoustPaths.Flavor flavor) {
        List<String> s = JoustPaths.segments(dir, flavor);
        if (s == null) return false;
        String joined = String.join("/", s).toLowerCase();
        return joined.endsWith("/.volta/bin") || joined.endsWith("/.asdf/shims") || joined.endsWith("/.nodenv/shims")
                || joined.endsWith("/mise/shims") || joined.endsWith("/.rtx/shims") || joined.contains("/node_modules/.bin");
    }

    private static List<Path> children(Path dir) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (Files.isDirectory(p)) out.add(p);
            }
        } catch (IOException ignored) {
            // unreadable: no candidates
        }
        out.sort(null);
        return out;
    }

    private static void addIfFile(Set<Path> out, Path p) {
        if (Files.isRegularFile(p)) out.add(p);
    }
}
