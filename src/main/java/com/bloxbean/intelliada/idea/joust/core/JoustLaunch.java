package com.bloxbean.intelliada.idea.joust.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * The language server's command line, exactly I3: {@code <node> <joust-lsp.js> --stdio --storage <dir>}.
 *
 * <p>M1 passes no engine flag of either kind (I4, I26, L19), never {@code --enable-edits} (D6), and nothing taken from
 * project files. The working directory is the directory of the real {@code joust-lsp.js}, outside every project.
 */
public record JoustLaunch(List<String> command, Path workingDirectory) {

    /** Flags IntelliAda never passes (I3, I26). */
    public static final Set<String> NEVER = Set.of("--project-engine", "--enable-edits", "--engine-dir");

    /**
     * Environment variables removed from the IDE's environment for the child: joust-lsp reads its test hooks from these
     * (bin/joust-lsp.js), and a test hook is never IntelliAda's choice.
     */
    public static final Set<String> REMOVED_ENVIRONMENT = Set.of("JOUST_LSP_TEST_HOOKS", "JOUST_LSP_HOST_OPTIONS");

    public static JoustLaunch of(Path realNode, Path realJoustLspJs, Path storageDir) {
        List<String> cmd = List.of(realNode.toString(), realJoustLspJs.toString(), "--stdio", "--storage", storageDir.toString());
        Path wd = realJoustLspJs.getParent();
        return new JoustLaunch(cmd, wd);
    }
}
