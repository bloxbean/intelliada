package com.bloxbean.intelliada.idea.joust.tasks.gradle;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.core.JoustRealPaths;
import com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks;
import com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.gradle.util.GradleConstants;


import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** {@link JoustGradleTasks} on IntelliJ's Gradle integration ({@code ExternalSystemUtil.runTask}). */
public final class JoustGradleTasksImpl implements JoustGradleTasks {

    static final int OUTPUT_CAP = 4 * 1024 * 1024;

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
    public JoustAbiReview.Result run(Project project, Path rootDir, List<String> args, String title) throws InterruptedException {
        ExternalSystemTaskExecutionSettings settings = new ExternalSystemTaskExecutionSettings();
        settings.setExternalProjectPath(rootDir.toString());
        settings.setExternalSystemIdString(GradleConstants.SYSTEM_ID.getId());
        settings.setTaskNames(new ArrayList<>(args));
        settings.setExecutionName(title);
        StringBuilder out = new StringBuilder();
        AtomicReference<ExternalSystemTaskId> id = new AtomicReference<>();
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        JoustTaskTracker tracker = JoustTaskTracker.getInstance(project);
        int generation = tracker.generation();
        JoustTaskTracker.Handle handle = () -> {
            ExternalSystemTaskId t = id.get();
            var task = t == null ? null : ExternalSystemProcessingManager.getInstance().findTask(t);
            if (task != null) task.cancel();
            done.cancel(false);
        };
        tracker.register(handle);
        ExternalSystemTaskNotificationListener listener = new ExternalSystemTaskNotificationListener() {
            @Override
            public void onStart(ExternalSystemTaskId taskId, String workingDir) {
                id.set(taskId);
            }

            @Override
            public void onTaskOutput(ExternalSystemTaskId taskId, String text, ProcessOutputType type) {
                synchronized (out) {
                    if (out.length() < OUTPUT_CAP) out.append(text, 0, Math.min(text.length(), OUTPUT_CAP - out.length()));
                }
            }
        };
        try {
            TaskExecutionSpec spec = TaskExecutionSpec.create(project, GradleConstants.SYSTEM_ID, DefaultRunExecutor.EXECUTOR_ID, settings)
                    .withListener(listener)
                    .withCallback(done)
                    .withProgressExecutionMode(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
                    .withActivateToolWindowBeforeRun(true)
                    .build();
            ExternalSystemUtil.runTask(spec);
            boolean ok;
            try {
                ok = Boolean.TRUE.equals(done.get());
            } catch (java.util.concurrent.CancellationException e) {
                return new JoustAbiReview.Result(false, "", true);
            } catch (java.util.concurrent.ExecutionException e) {
                ok = false;
            }
            if (!tracker.current(generation)) return new JoustAbiReview.Result(false, "", true);
            synchronized (out) {
                return new JoustAbiReview.Result(ok, out.toString(), false);
            }
        } finally {
            tracker.done(handle);
        }
    }
}
