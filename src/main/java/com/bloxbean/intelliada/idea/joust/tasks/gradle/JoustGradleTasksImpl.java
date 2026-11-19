package com.bloxbean.intelliada.idea.joust.tasks.gradle;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks;
import com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemProcessHandler;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link JoustGradleTasks} on IntelliJ's Gradle integration. Each run builds a fresh execution environment and
 * <b>clears what IntelliJ copies from the project's Gradle run-configuration template</b> (before-launch tasks, run
 * target, debug and run-as-test flags; M2-CDX-4, M2-SEC-6): only the requested tasks run, as freshly built settings. The run is registered before it starts, and a cancellation that
 * arrives before Gradle reports the task id cancels it as soon as the id arrives (M2-CDX-3).
 */
public final class JoustGradleTasksImpl implements JoustGradleTasks {

    static final int OUTPUT_CAP = 4 * 1024 * 1024;
    static final long TIMEOUT_MINUTES = 120;

    @Override
    public Linked linked(Project project, Path moduleDir) {
        return ReadAction.compute(() -> {
            for (Module m : ModuleManager.getInstance(project).getModules()) {
                String path = ExternalSystemApiUtil.getExternalProjectPath(m);
                String root = ExternalSystemApiUtil.getExternalRootProjectPath(m);
                if (path == null || root == null || !ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, m)) continue;
                try {
                    Path p = Path.of(path).toRealPath();
                    if (!p.toString().equals(moduleDir.toString())) continue;
                    String gradlePath = org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getGradleIdentityPathOrNull(m);
                    if (gradlePath != null) return new Linked(Path.of(root).toRealPath(), gradlePath);
                } catch (java.io.IOException | RuntimeException ignored) {
                    // not this module
                }
            }
            return null;
        });
    }

    @Override
    public Path linkedRootOf(Project project, Path path) {
        Path p;
        try {
            p = path.toRealPath();
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
        // An imported module whose directory is exactly this path first: a module may live beside its build's root
        // (projectDir = file("../contracts")), so ancestry alone would miss it (M2-CDX-13).
        Path exact = ReadAction.compute(() -> {
            for (Module m : ModuleManager.getInstance(project).getModules()) {
                if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, m)) continue;
                String mp = ExternalSystemApiUtil.getExternalProjectPath(m);
                String root = ExternalSystemApiUtil.getExternalRootProjectPath(m);
                if (mp == null || root == null) continue;
                try {
                    if (Path.of(mp).toRealPath().toString().equals(p.toString())) return Path.of(root).toRealPath();
                } catch (java.io.IOException | RuntimeException stale) {
                    // not this module
                }
            }
            return null;
        });
        if (exact != null) return exact;
        Path best = null;
        for (var ps : org.jetbrains.plugins.gradle.settings.GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
            try {
                Path root = Path.of(ps.getExternalProjectPath()).toRealPath();
                if (p.startsWith(root) && (best == null || root.getNameCount() > best.getNameCount())) best = root;
            } catch (java.io.IOException | RuntimeException stale) {
                // a stale link is skipped on its own; it never discards a valid match (M2-CDX-13)
            }
        }
        return best;
    }

    @Override
    public Path selectedModuleDir(AnActionEvent e) {
        if (!GradleConstants.SYSTEM_ID.equals(e.getData(com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID))) return null;
        var nodes = e.getData(com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys.SELECTED_NODES);
        if (nodes == null || nodes.size() != 1) return null;
        Object data = nodes.get(0).getData();
        if (!(data instanceof com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware a) || a.getLinkedExternalProjectPath() == null) {
            return null;
        }
        try {
            return Path.of(a.getLinkedExternalProjectPath());   // as text; resolved off the EDT with the context (M2-CDX-17)
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * The execution environment for {@code settings}, with no before-launch tasks. Package-visible for the test that
     * plants a before-launch task in the template.
     */
    static ExecutionEnvironment environment(Project project, ExternalSystemTaskExecutionSettings settings) {
        ExecutionEnvironment env = ExternalSystemUtil.createExecutionEnvironment(project, GradleConstants.SYSTEM_ID, settings,
                DefaultRunExecutor.EXECUTOR_ID);
        if (env == null) return null;
        var rcs = env.getRunnerAndConfigurationSettings();
        if (rcs == null) return null;
        var config = rcs.getConfiguration();
        // Nothing from the project's template survives (D7, M2-CDX-4, M2-SEC-6): no before-launch work, no run target,
        // no debug or run-as-test flags. setFrom already replaced the task, script, VM and environment settings.
        config.setBeforeRunTasks(List.of());
        if (config instanceof com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration es) {
            es.setDebugServerProcess(false);
            es.setReattachDebugProcess(false);
        }
        if (config instanceof org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration g) {
            g.setDebugAllEnabled(false);
            g.setRunAsTest(false);
            g.setDefaultTargetName(null);
        }
        return env;
    }

    static ExternalSystemTaskExecutionSettings settings(Path rootDir, List<String> args, String title) {
        ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
        settings.setExternalProjectPath(rootDir.toString());
        settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
        settings.setTaskNames(new ArrayList<>(args));
        settings.setExecutionName(PlainText.title(title, 120));
        return settings;
    }

    /**
     * Waits for the task: its result, or null on timeout. A timeout or an interrupted wait <b>cancels the run</b> (and so
     * the Gradle task, now or when its id arrives) before returning; an interrupt is then rethrown (M2-CDX-3).
     */
    static Boolean await(JoustTaskTracker.Run run, CompletableFuture<Boolean> done, long timeout, TimeUnit unit) throws InterruptedException {
        try {
            return Boolean.TRUE.equals(done.get(timeout, unit));
        } catch (java.util.concurrent.CancellationException e) {
            return false;
        } catch (java.util.concurrent.ExecutionException e) {
            return false;
        } catch (java.util.concurrent.TimeoutException e) {
            run.cancel();
            return null;
        } catch (InterruptedException e) {
            run.cancel();
            throw e;
        }
    }

    /**
     * IntelliAda's view of one submitted Gradle run, from public execution events (M2-DES-7). It binds the run's process
     * handler from {@code processStarting} or, when IntelliJ skips that because the handler was already start-notified,
     * from {@code processStarted} (M2-CDX-22); it stays subscribed until the handler arrives or the run did not start,
     * even after IntelliAda stopped waiting, so a cancellation always reaches a submitted task (M2-CDX-3).
     */
    static final class Binding {
        final JoustTaskTracker.Run run;
        final JoustOutputCapture out;
        final CompletableFuture<Boolean> done = new CompletableFuture<>();
        private final AtomicReference<ProcessHandler> handler = new AtomicReference<>();
        private final CompletableFuture<Void> settled = new CompletableFuture<>();
        /** The run's work has ended: its handler terminated, or it did not start (M2-CDX-3). */
        final CompletableFuture<Void> ended = new CompletableFuture<>();
        private final MessageBusConnection bus;
        private final ExecutionEnvironment env;

        Binding(Project project, ExecutionEnvironment env, JoustTaskTracker.Run run) {
            this(project, env, run, null);
        }

        Binding(Project project, ExecutionEnvironment env, JoustTaskTracker.Run run, java.util.function.Predicate<String> watch) {
            this.out = new JoustOutputCapture(OUTPUT_CAP, watch);
            this.run = run;
            this.env = env;
            run.attach(() -> done.cancel(false));
            bus = project.getMessageBus().connect();
            bus.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
                @Override
                public void processStarting(@NotNull String executorId, @NotNull ExecutionEnvironment e, @NotNull ProcessHandler h) {
                    if (e == env) bind(h);
                }

                @Override
                public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment e, @NotNull ProcessHandler h) {
                    if (e == env) bind(h);
                }

                @Override
                public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment e) {
                    if (e == env) {
                        done.complete(false);
                        ended.complete(null);
                        settle();
                    }
                }
            });
        }

        /** Binds the handler once: output, exit code, and cancellation; an already-terminated handler completes now. */
        void bind(ProcessHandler h) {
            if (!handler.compareAndSet(null, h)) return;
            h.addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                    out.append(event.getText(), JoustOutputCapture.baseStream(outputType));   // per base stream (M2-CDX-6, M2-CDX-15)
                }

                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
                    done.complete(event.getExitCode() == 0);
                    ended.complete(null);
                }
            });
            if (h.isProcessTerminated()) {
                done.complete(Integer.valueOf(0).equals(h.getExitCode()));
                ended.complete(null);
            }
            run.attach(() -> {
                if (h instanceof ExternalSystemProcessHandler es) es.getTask().cancel();
                h.destroyProcess();
            });
            settle();
        }

        private void settle() {
            if (settled.complete(null)) bus.disconnect();
        }

        /**
         * Called when IntelliAda stops waiting. A submitted run that has not delivered its handler keeps listening, with
         * no time limit, until the handler arrives (and is cancelled at once if the run was), the run reports that it did
         * not start, or the project closes and its message bus with it (M2-CDX-3). One never submitted lets go now.
         */
        void release(boolean submitted) {
            if (!submitted) {
                ended.complete(null);
                settle();
            }
        }

        /** Unregisters the run from {@code tracker} once its work has ended, not before (M2-CDX-3). */
        void releaseWhenEnded(JoustTaskTracker tracker) {
            ended.thenRun(() -> tracker.done(run));
        }

        boolean listening() {
            return !settled.isDone();
        }

        boolean bound() {
            return handler.get() != null;
        }

        ExecutionEnvironment env() {
            return env;
        }
    }


    @Override
    public JoustAbiReview.Result run(JoustRunGuard guard, Path rootDir, List<String> args, String title) throws InterruptedException {
        return run(guard, rootDir, args, title, null);
    }

    @Override
    public JoustAbiReview.Result run(JoustRunGuard guard, Path rootDir, List<String> args, String title,
                                     java.util.function.Predicate<String> watch) throws InterruptedException {
        Project project = guard.project();
        JoustTaskTracker tracker = JoustTaskTracker.getInstance(project);
        JoustTaskTracker.Run run = tracker.start(guard);
        if (run == null) return new JoustAbiReview.Result(false, "", true);
        Binding binding = null;
        boolean submitted = false;
        try {
            ExecutionEnvironment env = environment(project, settings(rootDir, args, title));
            if (env == null) return new JoustAbiReview.Result(false, "IntelliJ cannot run Gradle tasks here.", false);
            binding = new Binding(project, env, run, watch);
            if (run.cancelled() || !guard.live()) return new JoustAbiReview.Result(false, "", true);
            AtomicReference<Exception> failed = new AtomicReference<>();
            java.util.concurrent.atomic.AtomicBoolean executed = new java.util.concurrent.atomic.AtomicBoolean();
            Binding b = binding;
            ApplicationManager.getApplication().invokeAndWait(() -> {
                try {
                    if (!run.cancelled() && guard.live()) {
                        executed.set(true);
                        env.getRunner().execute(env);
                    } else {
                        b.done.cancel(false);
                    }
                } catch (Exception e) {
                    failed.set(e);
                }
            });
            submitted = executed.get() && failed.get() == null;
            if (failed.get() != null) return new JoustAbiReview.Result(false, String.valueOf(failed.get().getMessage()), false);
            Boolean ok = await(run, binding.done, TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (ok == null) return new JoustAbiReview.Result(false, "the Gradle task did not finish within " + TIMEOUT_MINUTES + " minutes", false);
            if (run.cancelled() || !guard.live()) return new JoustAbiReview.Result(false, "", true);
            return new JoustAbiReview.Result(ok, binding.out.toString(), false, watch == null ? null : binding.out.records());
        } finally {
            // Never let go of work that may still run (M2-CDX-3): cancel what did not finish, and keep listening for a
            // handler that has not arrived yet, so the cancellation reaches it.
            if (binding == null || !binding.done.isDone() || binding.done.isCancelled()) run.cancel();
            if (binding != null) {
                binding.release(submitted);
                binding.releaseWhenEnded(tracker);   // a submitted run stays tracked until its handler ends or it did not start
            } else {
                tracker.done(run);
            }
        }
    }
}
