package com.bloxbean.intelliada.idea.joust.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ADR-004 D3 and I25: the workspace folders IntelliAda sends are the top-level roots whose real paths lie inside the
 * project's base directory. A module or {@code includeBuild} outside the project is never a workspace folder.
 */
public final class JoustWorkspaceFolders {

    private JoustWorkspaceFolders() {
    }

    /**
     * @param candidateRealRoots the real paths of the project's content roots (and its base directory)
     * @return the in-base roots, without any root nested in another, sorted; the base itself when none remain
     */
    public static List<Path> confine(List<Path> candidateRealRoots, Path baseReal, JoustPaths.Flavor flavor) {
        List<Path> inside = new ArrayList<>();
        for (Path r : candidateRealRoots) {
            if (r != null && JoustPaths.isInside(r.toString(), baseReal.toString(), flavor)) inside.add(r);
        }
        inside.sort(Comparator.comparingInt((Path p) -> p.toString().length()).thenComparing(Path::toString));
        List<Path> top = new ArrayList<>();
        for (Path r : inside) {
            boolean nested = false;
            for (Path t : top) {
                if (JoustPaths.isInside(r.toString(), t.toString(), flavor)) {
                    nested = true;
                    break;
                }
            }
            if (!nested) top.add(r);
        }
        if (top.isEmpty()) top.add(baseReal);
        top.sort(Comparator.comparing(Path::toString));
        return top;
    }
}
