package com.bloxbean.intelliada.idea.joust.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * ADR-004 §12 E12 for the processes IntelliAda starts outside a project ({@code joust version}, the wizard's
 * {@code joust new}): their working and temporary directory is a <b>private</b> directory that IntelliAda creates under
 * the IDE's own temporary directory, and only after the destination, resolved through its nearest existing ancestor's
 * real path, is checked against every open project, by spelling and by file identity (M2-CDX-11).
 */
public final class JoustPrivateTemp {

    private JoustPrivateTemp() {
    }

    /**
     * Why {@code dir} must not be used as (or created as) a directory of IntelliAda's own, or {@code null}. Creates
     * nothing: the nearest existing ancestor is resolved to its real path, the rest of {@code dir} is appended, and both
     * the spelled and the resolved destination must lie outside every root (by spelling, {@link JoustPaths#isInside}),
     * and the real ancestor must not be any root or lie under one (by identity, {@link JoustRealPaths#isInside}).
     */
    public static String destinationProblem(Path dir, Collection<Path> forbiddenRoots, JoustPaths.Flavor flavor) {
        if (dir == null || !JoustPaths.isAbsolute(dir.toString(), flavor)) return "it is not an absolute path: " + dir;
        Path p = dir.toAbsolutePath().normalize();
        Path existing = p;
        while (existing != null && !Files.exists(existing)) existing = existing.getParent();
        if (existing == null) return "no part of it exists: " + p;
        Path real;
        try {
            real = existing.toRealPath();
        } catch (IOException | RuntimeException e) {
            return "its real path could not be resolved: " + p;
        }
        Path destination = real.resolve(existing.relativize(p)).normalize();
        for (Path root : forbiddenRoots) {
            for (Path candidate : new Path[]{p, destination}) {
                if (JoustPaths.isInside(candidate.toString(), root.toString(), flavor)) {
                    return "it is inside an open project (" + root + "): " + candidate;
                }
            }
            Path rootReal;
            try {
                rootReal = root.toRealPath();
            } catch (IOException | RuntimeException e) {
                continue; // a root that does not exist has no file identity; its spelling was checked above
            }
            if (JoustRealPaths.isInside(real, rootReal)) {
                return "it resolves into an open project (" + rootReal + "): " + destination;
            }
        }
        return null;
    }

    /**
     * Creates a new, owner-only directory under {@code base}: {@code base} is checked before anything is created, then
     * created when missing, and both its real path and the new directory's are checked again after creation (a swap in
     * between is caught, and the new directory is removed).
     *
     * @return the new directory's real path
     */
    public static Path create(Path base, String prefix, Collection<Path> forbiddenRoots, JoustPaths.Flavor flavor) throws IOException {
        String problem = destinationProblem(base, forbiddenRoots, flavor);
        if (problem != null) throw new IOException("The temporary directory " + base + " cannot be used: " + problem);
        Files.createDirectories(base);
        Path baseReal = base.toRealPath();
        if (!Files.isDirectory(baseReal)) throw new IOException("The temporary directory is not a directory: " + baseReal);
        problem = destinationProblem(baseReal, forbiddenRoots, flavor);
        if (problem != null) throw new IOException("The temporary directory " + base + " cannot be used: " + problem);
        Path dir = Files.createTempDirectory(baseReal, prefix).toRealPath();
        problem = destinationProblem(dir, forbiddenRoots, flavor);
        if (problem != null) {
            deleteTree(dir);
            throw new IOException("The temporary directory " + dir + " cannot be used: " + problem);
        }
        return dir;
    }

    /** Removes a directory IntelliAda created, best effort, without following links out of it. */
    public static void deleteTree(Path dir) {
        if (dir == null) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException | RuntimeException ignored) {
            // already gone
        }
    }
}
