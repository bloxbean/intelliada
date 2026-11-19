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
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager;
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
            return Path.of(a.getLinkedExternalProjectPath()).toRealPath();
        } catch (java.io.IOException | RuntimeException ex) {
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
        ExternalSystemTaskNotificationListener listener = new ExternalSystemTaskNotificationListener() {
            @Override
            public void onStart(@NotNull ExternalSystemTaskId taskId, String workingDir) {
                // A cancellation that came before the id cancels the task now (Run.attach runs it at once).
                run.attach(() -> {
                    var task = ExternalSystemProcessingManager.getInstance().findTask(taskId);
                    if (task != null) task.cancel();
                });
            }

            @Override
            public void onTaskOutput(@NotNull ExternalSystemTaskId taskId, @NotNull String text, @NotNull ProcessOutputType type) {
                synchronized (out) {
                    if (out.length() < OUTPUT_CAP) out.append(text, 0, Math.min(text.length(), OUTPUT_CAP - out.length()));
                }
            }

            @Override
            public void onSuccess(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
                done.complete(true);
            }

            @Override
            public void onFailure(@NotNull String projectPath, @NotNull ExternalSystemTaskId id, @NotNull Exception e) {
                done.complete(false);
            }

            @Override
            public void onCancel(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
                done.cancel(false);
            }

            @Override
            public void onEnd(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
                done.complete(false);
            }
        };
        MessageBusConnection bus = project.getMessageBus().connect();
        try {
            ExecutionEnvironment env = environment(project, settings(rootDir, args, title));
            if (env == null) return new JoustAbiReview.Result(false, "IntelliJ cannot run Gradle tasks here.", false);
            env.putUserData(ExternalSystemRunnableState.TASK_NOTIFICATION_LISTENER_KEY, listener);
            envRef.set(env);
            bus.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
                @Override
                public void processNotStarted(@NotNull String executorId, @NotNull ExecutionEnvironment e) {
                    if (e == envRef.get()) done.complete(false);
                }

                @Override
                public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment e, @NotNull ProcessHandler handler) {
                    if (e == envRef.get()) run.attach(handler::destroyProcess);
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
            boolean ok;
            try {
                ok = Boolean.TRUE.equals(done.get(TIMEOUT_MINUTES, TimeUnit.MINUTES));
            } catch (java.util.concurrent.CancellationException e) {
                return new JoustAbiReview.Result(false, "", true);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                ok = false;
            }
            if (run.cancelled() || !guard.live()) return new JoustAbiReview.Result(false, "", true);
            synchronized (out) {
                return new JoustAbiReview.Result(ok, out.toString(), false);
            }
        } finally {
            bus.disconnect();
            tracker.done(run);
        }
    }
}
