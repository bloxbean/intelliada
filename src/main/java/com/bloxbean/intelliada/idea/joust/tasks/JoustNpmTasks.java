package com.bloxbean.intelliada.idea.joust.tasks;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustRealPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots;
import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.project.Project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ADR-004 D7: the npm project's own CLI, {@code <node> node_modules/@julclang/joust/bin/joust.js <args>}, run only
 * under D1 (the caller checks trust and a user action). Node is the user's validated Node, re-checked (path and
 * identity) without executing it; the CLI's real path must lie inside the project; the working directory is the npm
 * root. Registered with {@link JoustTaskTracker}.
 */
public final class JoustNpmTasks {

    static final int TIMEOUT_MS = 15 * 60 * 1000;

    private JoustNpmTasks() {
    }

    /** The command line, or an exception naming why it may not run. */
    public static List<String> command(Project project, JoustProjectLayout.NpmRoot root, List<String> args) throws Exception {
        if (!root.cliInstalled()) throw new Exception("The project's @julclang/joust is not installed (npm install).");
        Path base = JoustForbiddenRoots.baseReal(project);
        if (!JoustRealPaths.isInside(root.cli().toRealPath(), base)) throw new Exception("The project's CLI is not inside the project.");
        JoustToolsState state = JoustToolsState.getInstance();
        var node = JoustToolValidator.checkNode(state.getState().nodePath, JoustForbiddenRoots.ofOpenProjects(), JoustPaths.Flavor.current());
        if (!node.ok()) throw new Exception(node.error());
        var expected = state.validatedNode();
        if (expected == null || !expected.equals(JoustToolValidator.identityOf(node.realPath()))) {
            throw new Exception("Node changed since it was validated: validate it again in Settings | Tools | JOUST.");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(node.realPath().toString());
        cmd.add(root.cli().toString());
        cmd.addAll(args);
        return cmd;
    }

    /** Runs the CLI, blocking; output captured and capped. */
    public static JoustAbiReview.Result run(Project project, JoustProjectLayout.NpmRoot root, List<String> args) throws Exception {
        List<String> cmd = command(project, root, args);
        GeneralCommandLine cl = new GeneralCommandLine(cmd).withWorkDirectory(root.dir().toFile())
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
        CapturingProcessHandler handler = new CapturingProcessHandler(cl);
        JoustTaskTracker tracker = JoustTaskTracker.getInstance(project);
        int generation = tracker.generation();
        JoustTaskTracker.Handle h = handler::destroyProcess;
        tracker.register(h);
        try {
            ProcessOutput out = handler.runProcess(TIMEOUT_MS);
            if (out.isCancelled() || !tracker.current(generation)) return new JoustAbiReview.Result(false, "", true);
            String text = out.getStdout() + out.getStderr();
            if (text.length() > 4 * 1024 * 1024) text = text.substring(0, 4 * 1024 * 1024);
            return new JoustAbiReview.Result(out.getExitCode() == 0 && !out.isTimeout(), text, false);
        } finally {
            tracker.done(h);
        }
    }
}
