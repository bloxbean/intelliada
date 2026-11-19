package com.bloxbean.intelliada.idea.joust.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Containment for decisions that <b>allow</b> something (a navigation target, a workspace folder, a document to open):
 * by file-system identity, not by spelling. Walking up from the target's real path, some ancestor must be the same
 * file as the project's real base directory. Case-distinct siblings on a case-sensitive volume (macOS, or a
 * case-sensitive Windows directory) are therefore never confused (M1-CDX-3).
 */
public final class JoustRealPaths {

    private JoustRealPaths() {
    }

    /** True when {@code realChild} is {@code realBase} or lies under it. Both must be real paths. */
    public static boolean isInside(Path realChild, Path realBase) {
        return isInside(realChild, realBase, JoustPaths.Flavor.current() == JoustPaths.Flavor.WINDOWS);
    }

    /**
     * On Windows {@code Path.equals} and {@code Files.isSameFile} fold case, and file keys are unavailable, so in a
     * case-sensitive directory {@code Repo} and {@code repo} would compare equal (M1-CDX-3). There an ancestor must spell
     * the base exactly: both are real paths, which Windows returns in their on-disk case. Elsewhere identity decides.
     */
    static boolean isInside(Path realChild, Path realBase, boolean windows) {
        if (realChild == null || realBase == null) return false;
        String base = realBase.toString();
        for (Path p = realChild; p != null; p = p.getParent()) {
            if (windows) {
                if (p.toString().equals(base)) return true;
                continue;
            }
            if (p.toString().equals(base)) return true;
            try {
                if (p.getNameCount() == realBase.getNameCount() && Files.isSameFile(p, realBase)) return true;
            } catch (IOException | SecurityException ignored) {
                // an unreadable ancestor is not the base
            }
        }
        return false;
    }

    /** For tests: the Windows rule on this platform's paths. */
    public static boolean isInsideWindowsRule(Path realChild, Path realBase) {
        return isInside(realChild, realBase, true);
    }
}
