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

        if (storageDir == null || !JoustPaths.isAbsolute(storageDir.toString(), flavor)) {
            return new Plan(null, "No storage directory for joust-lsp.", false);
        }
        for (Path root : forbiddenRoots) {
            if (JoustPaths.isInside(storageDir.toString(), root.toString(), flavor)) {
                return new Plan(null, "joust-lsp's storage directory must be outside every project: " + storageDir, false);
            }
        }
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            return new Plan(null, "joust-lsp's storage directory could not be created: " + e.getMessage(), false);
        }
        return new Plan(JoustLaunch.of(node.realPath(), lsp.realPath(), storageDir), null, false);
    }
}
