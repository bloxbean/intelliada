package com.bloxbean.intelliada.idea.joust.tasks;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustRealPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots;
import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ADR-004 D7: the npm project's own CLI, {@code <node> node_modules/@julclang/joust/bin/joust.js <args>}, run only
 * under D1 for a live {@link JoustRunGuard}. Node is the user's validated Node, re-checked (path and identity) without
 * executing it; the CLI's real path must lie inside the project; the working directory is the npm root. The run is
 * registered with {@link JoustTaskTracker} before the process starts (I20); its output goes to a Run console (where
 * JOUST diagnostics become links) and is captured up to a cap as it arrives (M2-CDX-14, M2-CDX-15).
 */
public final class JoustNpmTasks {

    static final int TIMEOUT_MS = 15 * 60 * 1000;
    public static final int OUTPUT_CAP = 4 * 1024 * 1024;

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

    /** Runs the CLI for a live guard, blocking. Cancelled (and nothing started) when the guard is not live. */
    public static JoustAbiReview.Result run(JoustRunGuard guard, JoustProjectLayout.NpmRoot root, List<String> args, String title)
            throws Exception {
        Project project = guard.project();
        JoustTaskTracker tracker = JoustTaskTracker.getInstance(project);
        JoustTaskTracker.Run run = tracker.start(guard);
        if (run == null) return new JoustAbiReview.Result(false, "", true);
        try {
            List<String> cmd = command(project, root, args);
            // The CLI reads the files on disk: save the editors first, then check again, then start (M2-CDX-21).
            ApplicationManager.getApplication().invokeAndWait(() -> com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveAllDocuments());
            if (run.cancelled() || !guard.live()) return new JoustAbiReview.Result(false, "", true);
            GeneralCommandLine cl = new GeneralCommandLine(cmd).withWorkDirectory(root.dir().toFile())
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
            OSProcessHandler handler = new OSProcessHandler(cl);
            run.attach(handler::destroyProcess);   // runs at once if cancelled while starting
            JoustOutputCapture out = new JoustOutputCapture(OUTPUT_CAP);
            handler.addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                    out.append(event.getText());
                }
            });
            if (showsConsole()) {
                ApplicationManager.getApplication().invokeAndWait(() -> new com.intellij.execution.RunContentExecutor(project, handler)
                        .withConsole(console(project, root)).withTitle(PlainText.title(title, 120)).withActivateToolWindow(true).run());
            } else {
                handler.startNotify();
            }
            if (!handler.waitFor(TIMEOUT_MS)) {
                handler.destroyProcess();
                return new JoustAbiReview.Result(false, "joust did not finish within " + TIMEOUT_MS / 60000 + " minutes", false);
            }
            if (run.cancelled() || !guard.live()) return new JoustAbiReview.Result(false, "", true);
            Integer code = handler.getExitCode();
            return new JoustAbiReview.Result(code != null && code == 0, out.toString(), false);
        } finally {
            tracker.done(run);
        }
    }

    /**
     * The filters of an npm Run console (M2-CDX-14): IntelliJ's standard filters from every other provider, and the
     * JOUST filter scoped to this npm root; never the project-wide JOUST filter.
     */
    public static List<com.intellij.execution.filters.Filter> consoleFilters(Project project, JoustProjectLayout.NpmRoot root) {
        List<com.intellij.execution.filters.Filter> filters = new ArrayList<>();
        for (var provider : com.intellij.execution.filters.ConsoleFilterProvider.FILTER_PROVIDERS.getExtensionList()) {
            if (provider instanceof com.bloxbean.intelliada.idea.joust.project.JoustConsoleFilterProvider) continue;
            filters.addAll(List.of(provider.getDefaultFilters(project)));
        }
        filters.add(new com.bloxbean.intelliada.idea.joust.project.JoustConsoleFilterProvider.JoustDiagnosticFilter(project, List.of(root.dir())));
        return filters;
    }

    static com.intellij.execution.ui.ConsoleView console(Project project, JoustProjectLayout.NpmRoot root) {
        var builder = new com.intellij.execution.filters.TextConsoleBuilderImpl(project);
        builder.setUsePredefinedMessageFilter(false);   // the providers are added above, without the project-wide JOUST one
        builder.filters(consoleFilters(project, root));
        return builder.getConsole();
    }

    /** A Run console in the IDE; tests without one read the captured output only. */
    static boolean showsConsole() {
        var app = ApplicationManager.getApplication();
        return !app.isUnitTestMode() && !app.isHeadlessEnvironment();
    }
}
