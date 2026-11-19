package com.bloxbean.intelliada.idea.joust.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ADR-004 D3 and I25: LSP4IJ's workspace folders are the project's top-level content roots (F30). IntelliAda keeps
 * those whose real paths lie inside the project's base directory (by identity, {@link JoustRealPaths}), and sends
 * nothing else: a module or {@code includeBuild} outside the project is never a workspace folder, and the base itself
 * is not added when it is not a content root (M1-CDX-12).
 */
public final class JoustWorkspaceFolders {

    private JoustWorkspaceFolders() {
    }

    /**
     * @param contentRootsReal the real paths of the project's content roots
     * @return the top-level ones (no root nested in another) that lie inside {@code baseReal}, sorted
     */
    public static List<Path> confine(List<Path> contentRootsReal, Path baseReal) {
        List<Path> inside = new ArrayList<>();
        for (Path r : contentRootsReal) {
            // Deduplicated by exact spelling: on Windows Path.equals folds case (M1-CDX-12).
            if (r != null && inside.stream().noneMatch(x -> x.toString().equals(r.toString())) && JoustRealPaths.isInside(r, baseReal)) inside.add(r);
        }
        List<Path> top = new ArrayList<>();
        for (Path r : inside) {
            boolean nested = false;
            for (Path o : inside) {
                if (!o.toString().equals(r.toString()) && JoustRealPaths.isInside(r, o)) {
                    nested = true;
                    break;
                }
            }
            if (!nested) top.add(r);
        }
        top.sort(Comparator.comparing(Path::toString));
        return top;
    }
}
