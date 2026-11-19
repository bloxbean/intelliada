package com.bloxbean.intelliada.idea.joust.tasks.gradle;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks;
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

    @Override
    public JoustAbiReview.Result run(JoustRunGuard guard, Path rootDir, List<String> args, String title) throws InterruptedException {
        Project project = guard.project();
        JoustTaskTracker tracker = JoustTaskTracker.getInstance(project);
        JoustTaskTracker.Run run = tracker.start(guard);
        if (run == null) return new JoustAbiReview.Result(false, "", true);
        StringBuilder out = new StringBuilder();
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        AtomicReference<ExecutionEnvironment> envRef = new AtomicReference<>();
        run.attach(() -> done.cancel(false));
        MessageBusConnection bus = project.getMessageBus().connect();
        try {
            ExecutionEnvironment env = environment(project, settings(rootDir, args, title));
            if (env == null) return new JoustAbiReview.Result(false, "IntelliJ cannot run Gradle tasks here.", false);
            envRef.set(env);
            // Public APIs only (M2-DES-7): the run's process handler, from the execution events, gives its output, its
            // exit code and, through its external-system task, cancellation.
            bus.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
                @Override
                public void processStarting(@NotNull String executorId, @NotNull ExecutionEnvironment e, @NotNull ProcessHandler handler) {
                    if (e != envRef.get()) return;
                    handler.addProcessListener(new ProcessListener() {
                        @Override
                        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                            String t = event.getText();
                            synchronized (out) {
                                if (t != null && out.length() < OUTPUT_CAP) out.append(t, 0, Math.min(t.length(), OUTPUT_CAP - out.length()));
                            }
                        }

                        @Override
                        public void processTerminated(@NotNull ProcessEvent event) {
                            done.complete(event.getExitCode() == 0);
                        }
                    });
                    run.attach(() -> {
                        if (handler instanceof ExternalSystemProcessHandler es) es.getTask().cancel();
                        handler.destroyProcess();
                    });
                }

                @Override
                public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment e) {
                    if (e == envRef.get()) done.complete(false);
                }
            });
            if (run.cancelled() || !guard.live()) return new JoustAbiReview.Result(false, "", true);
            AtomicReference<Exception> failed = new AtomicReference<>();
            ApplicationManager.getApplication().invokeAndWait(() -> {
                try {
                    if (!run.cancelled() && guard.live()) env.getRunner().execute(env);
                    else done.cancel(false);
                } catch (Exception e) {
                    failed.set(e);
                }
            });
            if (failed.get() != null) return new JoustAbiReview.Result(false, String.valueOf(failed.get().getMessage()), false);
            Boolean ok = await(run, done, TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (ok == null) return new JoustAbiReview.Result(false, "the Gradle task did not finish within " + TIMEOUT_MINUTES + " minutes", false);
            if (run.cancelled() || !guard.live()) return new JoustAbiReview.Result(false, "", true);
            synchronized (out) {
                return new JoustAbiReview.Result(ok, out.toString(), false);
            }
        } finally {
            // Never unregister work that may still run (M2-CDX-3): whatever did not finish is cancelled first.
            if (!done.isDone()) run.cancel();
            bus.disconnect();
            tracker.done(run);
        }
    }
}
