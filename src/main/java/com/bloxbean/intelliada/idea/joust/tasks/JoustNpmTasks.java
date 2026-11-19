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
                        out.append(event.getText(), outputType.toString());   // per stream (M2-CDX-6)
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
                return new JoustAbiReview.Result(code != null && code == 0, out.toString(), false);
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
     * also records the process's descendants while it runs (M2-CDX-3, round 11): {@code joust test} starts a Node test
     * runner of its own, and a later enumeration may be refused, so the PIDs seen are kept and ended by PID.
     */
    static final class Launch {
        final OSProcessHandler handler;
        private boolean started;
        final java.util.Set<Long> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
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
                for (ProcessHandle d : descendants(p)) seen.add(d.pid());
            }, 0, 250, java.util.concurrent.TimeUnit.MILLISECONDS);
            p.onExit().thenRun(() -> {
                synchronized (Launch.this) {
                    if (sampler != null) sampler.cancel(false);
                }
            });
        }

        /** The recorded descendants still alive. */
        List<ProcessHandle> liveSeen() {
            List<ProcessHandle> out = new ArrayList<>();
            for (long pid : seen) ProcessHandle.of(pid).filter(ProcessHandle::isAlive).ifPresent(out::add);
            return out;
        }

        synchronized void ensureStarted() {
            if (started) return;
            started = true;
            handler.startNotify();
        }
    }

    /** For tests: when true, enumerating descendants is refused, as some sandboxes do. */
    static volatile boolean enumerationDeniedForTests;

    /** The process's descendants, or none when the system refuses to list them. */
    static List<ProcessHandle> descendants(Process p) {
        try {
            if (enumerationDeniedForTests) throw new UnsupportedOperationException("Operation not permitted (sysctl failed)");
            return p.descendants().toList();
        } catch (RuntimeException refused) {
            return List.of();
        }
    }

    /**
     * Ends {@code p} and its whole tree by every means available: the descendants it lists now, the ones recorded while
     * it ran (by PID, no enumeration needed), and IntelliJ's own tree kill (which walks the tree with {@code ps}); then
     * the process itself.
     */
    static void destroyTree(Launch launch, Process p) {
        for (ProcessHandle d : descendants(p)) d.destroyForcibly();
        for (ProcessHandle d : launch.liveSeen()) d.destroyForcibly();
        try {
            com.intellij.execution.process.OSProcessUtil.killProcessTree(p);
        } catch (RuntimeException | Error unavailable) {
            // the other means above still apply
        }
        p.destroyForcibly();
    }

    /** True when the process and every descendant recorded for it have exited. */
    static boolean treeGone(Launch launch) {
        return !launch.handler.getProcess().isAlive() && launch.liveSeen().isEmpty();
    }


    /** Runs {@code release} once the process and its recorded descendants have all exited. */
    static void releaseWhenTreeGone(Launch launch, Runnable release) {
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        var poll = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.ScheduledFuture<?>>();
        poll.set(com.intellij.util.concurrency.AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
            if (treeGone(launch) && done.compareAndSet(false, true)) {
                release.run();
                var f = poll.get();
                if (f != null) f.cancel(false);
            }
        }, 0, 200, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    /** Returns true when the process itself was seen to exit (a detached handler is no proof, M2-CDX-3). */
    static boolean kill(Launch launch) {
        Runnable hook = killHookForTests;
        if (hook != null) hook.run();
        OSProcessHandler handler = launch.handler;
        Process p = handler.getProcess();
        launch.ensureStarted();
        // The whole tree first (a joust test runner is a child of the CLI), then the handler's own destruction.
        destroyTree(launch, p);
        handler.destroyProcess();
        boolean interrupted = false;
        try {
            for (int attempt = 0; attempt < 6 && !treeGone(launch); attempt++) {
                destroyTree(launch, p);
                try {
                    p.onExit().get(10, java.util.concurrent.TimeUnit.SECONDS);   // destroyForcibly returns before the exit
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
