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
        return run(guard, root, args, title, null);
    }

    /** As {@link #run(JoustRunGuard, JoustProjectLayout.NpmRoot, List, String)}, keeping {@code watch}ed lines apart. */
    public static JoustAbiReview.Result run(JoustRunGuard guard, JoustProjectLayout.NpmRoot root, List<String> args, String title,
                                            java.util.function.Predicate<String> watch) throws Exception {
        Project project = guard.project();
        JoustTaskTracker tracker = JoustTaskTracker.getInstance(project);
        JoustTaskTracker.Run run = tracker.start(guard);
        if (run == null) return new JoustAbiReview.Result(false, "", true);
        Process proc = null;
        Launch launch = null;
        try {
            List<String> cmd = command(project, root, args);
            // The CLI reads the files on disk: save the editors first, then check again, then start (M2-CDX-21).
            ApplicationManager.getApplication().invokeAndWait(() -> com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveAllDocuments());
            if (run.cancelled() || !guard.live()) return new JoustAbiReview.Result(false, "", true);
            GeneralCommandLine cl = new GeneralCommandLine(cmd).withWorkDirectory(root.dir().toFile())
                    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
            OSProcessHandler handler = new OSProcessHandler(cl);   // the process runs from here
            proc = handler.getProcess();
            launch = new Launch(handler);
            launch.sample();
            boolean ended = false;
            try {
                // A cancellation (possibly on the EDT) only queues the kill on a pooled thread; the kill itself start-notifies
                // through the one owner and waits for the end there (M2-CDX-3).
                Launch owner = launch;
                run.attach(() -> com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService().execute(() -> kill(owner)));
                JoustOutputCapture out = new JoustOutputCapture(OUTPUT_CAP, watch);
                handler.addProcessListener(new ProcessListener() {
                    @Override
                    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                        out.append(event.getText(), JoustOutputCapture.baseStream(outputType));   // per base stream (M2-CDX-6, M2-CDX-15)
                    }
                });
                var opener = consoleOpener;
                if (opener != null) {
                    opener.accept(handler);
                } else if (showsConsole()) {
                    ApplicationManager.getApplication().invokeAndWait(() -> openConsole(project, root, handler, PlainText.title(title, 120)));
                }
                launch.ensureStarted();
                if (!handler.waitFor(TIMEOUT_MS)) {
                    return new JoustAbiReview.Result(false, "joust did not finish within " + TIMEOUT_MS / 60000 + " minutes", false);
                }
                // The handler may have let go of a live child (detached): only the process's own exit counts (M2-CDX-3). That
                // is a failure the user did not ask for, not a cancellation.
                if (handler.getProcess().isAlive()) {
                    return new JoustAbiReview.Result(false, "joust was detached from its output while still running and has been ended.", false);
                }
                ended = true;
                if (run.cancelled() || !guard.live()) return new JoustAbiReview.Result(false, "", true);
                Integer code = handler.getExitCode();
                return new JoustAbiReview.Result(code != null && code == 0, out.toString(), false, watch == null ? null : out.records());
            } finally {
                // Whatever happened after the process started (a console that failed to open, a timeout, an exception),
                // it is ended before the tracker lets go of it (M2-CDX-3).
                if (!ended) {
                    try {
                        kill(launch);
                    } catch (RuntimeException | Error cleanupFailed) {
                        // whatever cleanup threw, release still follows the process's own exit below
                    }
                }
            }
        } finally {
            // Release follows the process itself (M2-CDX-3): now if it has exited (or never started), else when it does,
            // whatever the cleanup above did or threw; until then a later cancellation still reaches it.
            if (proc == null || treeGone(launch)) {
                tracker.done(run);
            } else {
                Launch l = launch;
                releaseWhenTreeGone(l, () -> tracker.done(run));
            }
        }
    }

    /**
     * The filters of an npm Run console (M2-CDX-14): every other provider's filters, through the console-aware overload
     * where the provider has one (IntelliJ's standard filters stay), and the JOUST filter scoped to this npm root; never
     * the project-wide JOUST filter.
     */
    public static List<com.intellij.execution.filters.Filter> consoleFilters(Project project, JoustProjectLayout.NpmRoot root,
                                                                            com.intellij.execution.ui.ConsoleView console) {
        List<com.intellij.execution.filters.Filter> filters = new ArrayList<>();
        var scope = com.intellij.psi.search.GlobalSearchScope.allScope(project);
        for (var provider : com.intellij.execution.filters.ConsoleFilterProvider.FILTER_PROVIDERS.getExtensionList()) {
            if (provider instanceof com.bloxbean.intelliada.idea.joust.project.JoustConsoleFilterProvider) continue;
            com.intellij.execution.filters.Filter[] f;
            if (provider instanceof com.intellij.execution.filters.ConsoleDependentFilterProvider cd && console != null) {
                f = cd.getDefaultFilters(console, project, scope);
            } else if (provider instanceof com.intellij.execution.filters.ConsoleFilterProviderEx ex) {
                f = ex.getDefaultFilters(project, scope);
            } else {
                f = provider.getDefaultFilters(project);
            }
            filters.addAll(List.of(f));
        }
        filters.add(new com.bloxbean.intelliada.idea.joust.project.JoustConsoleFilterProvider.JoustDiagnosticFilter(project, List.of(root.dir())));
        return filters;
    }

    public static List<com.intellij.execution.filters.Filter> consoleFilters(Project project, JoustProjectLayout.NpmRoot root) {
        return consoleFilters(project, root, null);
    }

    /**
     * The npm Run tab: a console with {@link #consoleFilters} (no predefined filters), attached to the process, in a run
     * descriptor shown under the Run executor and disposed with it. On the EDT; the caller starts the process after.
     */
    public static com.intellij.execution.ui.RunContentDescriptor openConsole(Project project, JoustProjectLayout.NpmRoot root,
                                                                             com.intellij.execution.process.ProcessHandler handler, String title) {
        var builder = new com.intellij.execution.filters.TextConsoleBuilderImpl(project);
        builder.setUsePredefinedMessageFilter(false);
        com.intellij.execution.ui.ConsoleView console = builder.getConsole();
        for (var f : consoleFilters(project, root, console)) console.addMessageFilter(f);
        console.attachToProcess(handler);
        var descriptor = new com.intellij.execution.ui.RunContentDescriptor(console, handler, console.getComponent(), title);
        descriptor.setActivateToolWindowWhenAdded(true);
        com.intellij.openapi.util.Disposer.register(descriptor, console);
        com.intellij.execution.ui.RunContentManager.getInstance(project)
                .showRunContent(com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance(), descriptor);
        return descriptor;
    }

    /** For tests: runs at the start of every kill (it may throw, as a refused cleanup would). */
    static volatile Runnable killHookForTests;

    /** For tests: replaces opening the Run console (it may throw); null for the normal console. */
    static volatile java.util.function.Consumer<com.intellij.execution.process.ProcessHandler> consoleOpener;

    public static void setConsoleOpenerForTests(java.util.function.Consumer<com.intellij.execution.process.ProcessHandler> opener) {
        consoleOpener = opener;
    }

    /**
     * The one owner of a launched handler's start notification (M2-CDX-3): {@code startNotify()} runs exactly once. It
     * also records the process's descendants while it runs: {@code joust test} starts a Node test runner of its own, and
     * a later enumeration may be refused. Descendants are kept as the {@link ProcessHandle}s observed, never re-created
     * from a bare PID, so a PID reused by an unrelated process is never touched (M2-CDX-32, M2-DES-13).
     */
    static final class Launch {
        final OSProcessHandler handler;
        private boolean started;
        /** Observed descendants (handles carry their start time); dropped once they have exited. */
        final java.util.Set<ProcessHandle> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
        /** Whether listing descendants ever succeeded while the process lived: the only way to know its tree. */
        volatile boolean enumerated;
        /** Whether IntelliJ's own tree kill reported success. */
        volatile boolean treeKilled;
        /** Whether IntelliAda had to end it (cancellation, failure): only then must the tree be shown gone. */
        volatile boolean killed;
        private java.util.concurrent.ScheduledFuture<?> sampler;

        Launch(OSProcessHandler handler) {
            this.handler = handler;
        }

        /** Records the descendants every 250 ms while the process lives. */
        synchronized void sample() {
            if (sampler != null) return;
            Process p = handler.getProcess();
            sampler = com.intellij.util.concurrency.AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
                if (!p.isAlive()) return;
                List<ProcessHandle> now = descendants(p);
                if (now != null) {
                    enumerated = true;
                    seen.addAll(now);
                }
                seen.removeIf(h -> !h.isAlive());
            }, 0, 250, java.util.concurrent.TimeUnit.MILLISECONDS);
            p.onExit().thenRun(() -> {
                synchronized (Launch.this) {
                    if (sampler != null) sampler.cancel(false);
                }
            });
        }

        /** The observed descendants still alive (each handle checks its own process, not whoever has its PID now). */
        List<ProcessHandle> liveSeen() {
            seen.removeIf(h -> !h.isAlive());
            return List.copyOf(seen);
        }

        synchronized void ensureStarted() {
            if (started) return;
            started = true;
            handler.startNotify();
        }
    }

    /** For tests: when true, enumerating descendants is refused, as some sandboxes do. */
    static volatile boolean enumerationDeniedForTests;
    /** For tests: when true, IntelliJ's tree kill reports failure. */
    static volatile boolean treeKillFailsForTests;

    /** The process's descendants, or null when the system refuses to list them. */
    static List<ProcessHandle> descendants(Process p) {
        try {
            if (enumerationDeniedForTests) throw new UnsupportedOperationException("Operation not permitted (sysctl failed)");
            return p.descendants().toList();
        } catch (RuntimeException refused) {
            return null;
        }
    }

    /**
     * Ends {@code p} and its whole tree by every means available: the descendants it lists now and the ones observed
     * while it ran (their own handles), IntelliJ's tree kill (which walks the tree with {@code ps}), then the process.
     */
    static void destroyTree(Launch launch, Process p) {
        List<ProcessHandle> now = descendants(p);
        if (now != null) {
            launch.enumerated = true;
            launch.seen.addAll(now);
        }
        for (ProcessHandle d : launch.liveSeen()) d.destroyForcibly();
        try {
            boolean killed = !treeKillFailsForTests && com.intellij.execution.process.OSProcessUtil.killProcessTree(p);
            if (killed) launch.treeKilled = true;
        } catch (RuntimeException | Error unavailable) {
            // the other means still apply
        }
        p.destroyForcibly();
    }

    /**
     * True only when the tree is <b>known</b> to be gone (M2-CDX-3): the process has exited, every observed descendant
     * has exited, and, for a run IntelliAda had to end, the tree was actually known, by a listing that succeeded while it
     * lived or by IntelliJ's tree kill reporting success. An empty sample from a refused listing proves nothing.
     */
    static boolean treeGone(Launch launch) {
        boolean known = !launch.killed || launch.enumerated || launch.treeKilled;
        return !launch.handler.getProcess().isAlive() && launch.liveSeen().isEmpty() && known;
    }

    /**
     * Runs {@code release} once {@link #treeGone} holds: a one-shot check that reschedules itself only while work remains
     * (M2-CDX-33). A tree that can never be shown gone keeps its run tracked, so a later cancellation still applies.
     */
    static void releaseWhenTreeGone(Launch launch, Runnable release) {
        releaseWhenTreeGone(launch, release, 0);
    }

    /** Retries of the tree kill for a tree never known, with back-off (M2-DES-14); then only a slow re-check. */
    static final int MAX_TREE_KILL_RETRIES = 5;

    private static void releaseWhenTreeGone(Launch launch, Runnable release, int attempt) {
        if (treeGone(launch)) {
            release.run();
            return;
        }
        boolean unknown = launch.killed && !launch.handler.getProcess().isAlive() && !launch.enumerated && !launch.treeKilled;
        if (unknown && attempt < MAX_TREE_KILL_RETRIES) {
            // The parent is gone and its tree was never known: retry the tree kill a few times (each may start a ps).
            destroyTree(launch, launch.handler.getProcess());
        }
        long delay = attempt < MAX_TREE_KILL_RETRIES ? 200L << attempt : 30_000L;
        com.intellij.util.concurrency.AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(() -> releaseWhenTreeGone(launch, release, attempt + 1), delay, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Returns true only when the tree is known to be gone ({@link #treeGone}); a detached handler is no proof. */
    static boolean kill(Launch launch) {
        Runnable hook = killHookForTests;
        if (hook != null) hook.run();
        OSProcessHandler handler = launch.handler;
        Process p = handler.getProcess();
        launch.killed = true;
        launch.ensureStarted();
        destroyTree(launch, p);   // the whole tree first (a joust test runner is a child of the CLI)
        handler.destroyProcess();
        boolean interrupted = false;
        try {
            for (int attempt = 0; attempt < 6 && (p.isAlive() || !launch.liveSeen().isEmpty()); attempt++) {
                destroyTree(launch, p);
                try {
                    p.onExit().get(10, java.util.concurrent.TimeUnit.SECONDS);
                    for (ProcessHandle d : launch.liveSeen()) d.onExit().get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;   // keep waiting: releasing a live process is not an option
                } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ignored) {
                    // try again
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
        return treeGone(launch);
    }

    /** A Run console in the IDE; tests without one read the captured output only. */
    static boolean showsConsole() {
        var app = ApplicationManager.getApplication();
        return !app.isUnitTestMode() && !app.isHeadlessEnvironment();
    }
}
