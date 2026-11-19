package com.bloxbean.intelliada.idea.joust.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Decides, at every server start, whether the language server may start and with which command line (ADR-004 D3, D4).
 *
 * <p>It re-checks both paths and the Node identity recorded when the user validated Node. It <b>executes nothing</b>:
 * no {@code node --version} at start (CDX-25). A changed identity stops the start and asks the user to validate again.
 */
public final class JoustLaunchPlanner {

    private JoustLaunchPlanner() {
    }

    /**
     * The storage directory's real path, created if needed, or {@code null} (M1-CDX-4). Its destination is resolved
     * through the nearest <b>existing</b> ancestor's real path <b>before</b> anything is created, so a symlinked ancestor
     * into a project is refused without writing there; the created directory's real path is checked again.
     */
    static Path verifiedStorage(Path storageDir, Collection<Path> forbiddenRoots, JoustPaths.Flavor flavor) {
        if (storageDir == null || !JoustPaths.isAbsolute(storageDir.toString(), flavor)) return null;
        Path p = storageDir.toAbsolutePath().normalize();
        Path existing = p;
        while (existing != null && !Files.exists(existing)) existing = existing.getParent();
        if (existing == null) return null;
        Path destination;
        try {
            destination = existing.toRealPath().resolve(existing.relativize(p)).normalize();
        } catch (IOException e) {
            return null;
        }
        if (insideAny(destination, forbiddenRoots, flavor)) return null;
        try {
            Files.createDirectories(destination);
            Path real = destination.toRealPath();
            if (!Files.isDirectory(real) || insideAny(real, forbiddenRoots, flavor)) return null;
            return real;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean insideAny(Path p, Collection<Path> roots, JoustPaths.Flavor flavor) {
        for (Path root : roots) {
            if (JoustPaths.isInside(p.toString(), root.toString(), flavor)) return true;
        }
        return false;
    }

    /** What the configured settings say. */
    public record Settings(String nodePath, String joustLspPath, NodeIdentity validatedNode) {
    }

    /** Either a launch or the reason there is none. */
    public record Plan(JoustLaunch launch, String error, boolean needsRevalidation) {
        public boolean ok() {
            return launch != null;
        }
    }

    public static Plan plan(Settings settings, Collection<Path> forbiddenRoots, Path storageDir, JoustPaths.Flavor flavor) {
        JoustToolValidator.Result node = JoustToolValidator.checkNode(settings.nodePath(), forbiddenRoots, flavor);
        if (!node.ok()) return new Plan(null, node.error(), false);
        JoustToolValidator.Result lsp = JoustToolValidator.resolveJoustLsp(settings.joustLspPath(), forbiddenRoots, flavor);
        if (!lsp.ok()) return new Plan(null, lsp.error(), false);

        NodeIdentity expected = settings.validatedNode();
        if (expected == null) {
            return new Plan(null, "Node has not been validated. Open Settings | Tools | JOUST and save, or use Detect…", true);
        }
        NodeIdentity actual;
        try {
            actual = JoustToolValidator.identityOf(node.realPath());
        } catch (IOException e) {
            return new Plan(null, "Node could not be read: " + e.getMessage(), true);
        }
        if (!actual.equals(expected)) {
            return new Plan(null, "The Node binary changed since it was validated (" + node.realPath()
                    + "). Validate it again in Settings | Tools | JOUST.", true);
        }

        Path storage = verifiedStorage(storageDir, forbiddenRoots, flavor);
        if (storage == null) {
            return new Plan(null, "joust-lsp's storage directory must be a directory outside every project: " + storageDir, false);
        }
        return new Plan(JoustLaunch.of(node.realPath(), lsp.realPath(), storage), null, false);
    }
}
