package com.bloxbean.intelliada.idea.joust.settings;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ADR-004 D4: the roots a tool path must stay out of: every open project's base directory and content roots, as real
 * paths where they resolve (and as given otherwise, so an unresolvable root is still refused).
 */
public final class JoustForbiddenRoots {

    private JoustForbiddenRoots() {
    }

    public static List<Path> ofOpenProjects() {
        Set<Path> out = new LinkedHashSet<>();
        for (Project p : ProjectManager.getInstance().getOpenProjects()) {
            if (!p.isDisposed()) out.addAll(of(p));
        }
        return new ArrayList<>(out);
    }

    public static List<Path> of(Project project) {
        Set<Path> out = new LinkedHashSet<>();
        add(out, project.getBasePath());
        VirtualFile[] roots = ReadAction.compute(() -> project.isDisposed() ? VirtualFile.EMPTY_ARRAY
                : ProjectRootManager.getInstance(project).getContentRoots());
        for (VirtualFile r : roots) {
            if (r.isInLocalFileSystem()) add(out, r.getPath());
        }
        return new ArrayList<>(out);
    }

    /** The project's base directory as a real path, or {@code null}. */
    public static Path baseReal(Project project) {
        String base = project.getBasePath();
        if (base == null) return null;
        try {
            return Path.of(base).toRealPath();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void add(Set<Path> out, String path) {
        if (path == null) return;
        try {
            Path p = Path.of(path).toAbsolutePath().normalize();
            out.add(p);
            try {
                out.add(p.toRealPath());
            } catch (IOException ignored) {
                // keep the given form
            }
        } catch (RuntimeException ignored) {
            // not a local path
        }
    }
}
