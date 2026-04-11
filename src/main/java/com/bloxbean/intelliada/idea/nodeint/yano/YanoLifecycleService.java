package com.bloxbean.intelliada.idea.nodeint.yano;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Application-level service managing Yano node lifecycle per project.
 */
public class YanoLifecycleService implements StartupActivity {
    private static final Logger LOG = Logger.getInstance(YanoLifecycleService.class);

    private final ConcurrentMap<String, YanoProcessManager> processManagers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, YanoStatusMonitor> statusMonitors = new ConcurrentHashMap<>();

    public static YanoLifecycleService getInstance() {
        return ApplicationManager.getApplication().getService(YanoLifecycleService.class);
    }

    @Override
    public void runActivity(@NotNull Project project) {
        project.getMessageBus().connect().subscribe(
                ProjectManager.TOPIC,
                new ProjectManagerListener() {
                    @Override
                    public void projectClosed(@NotNull Project closedProject) {
                        cleanupProject(closedProject);
                    }
                }
        );
    }

    public CompletableFuture<Boolean> startYano(@NotNull Project project, @NotNull String yanoHome) {
        return startYano(project, yanoHome, 7070);
    }

    public CompletableFuture<Boolean> startYano(@NotNull Project project, @NotNull String yanoHome, int port) {
        String projectKey = getProjectKey(project);

        YanoProcessManager processManager = processManagers.computeIfAbsent(projectKey,
                k -> new YanoProcessManager(yanoHome, port));

        YanoStatusMonitor statusMonitor = statusMonitors.computeIfAbsent(projectKey,
                k -> new YanoStatusMonitor(processManager, project));
        statusMonitor.startMonitoring();

        return processManager.startYano();
    }

    public CompletableFuture<Boolean> stopYano(@NotNull Project project) {
        String projectKey = getProjectKey(project);
        YanoProcessManager processManager = processManagers.get(projectKey);
        if (processManager != null) {
            return processManager.stopYano();
        }
        return CompletableFuture.completedFuture(true);
    }

    public boolean isYanoRunning(@NotNull Project project) {
        String projectKey = getProjectKey(project);
        YanoProcessManager pm = processManagers.get(projectKey);
        return pm != null && pm.getStatus() == YanoProcessManager.YanoStatus.RUNNING;
    }

    public YanoProcessManager getProcessManager(@NotNull Project project) {
        return processManagers.get(getProjectKey(project));
    }

    public YanoStatusMonitor getStatusMonitor(@NotNull Project project) {
        return statusMonitors.get(getProjectKey(project));
    }

    public void cleanupProject(@NotNull Project project) {
        String projectKey = getProjectKey(project);

        YanoStatusMonitor monitor = statusMonitors.remove(projectKey);
        if (monitor != null) {
            monitor.stopMonitoring();
        }

        YanoProcessManager manager = processManagers.remove(projectKey);
        if (manager != null) {
            manager.stopYano();
        }
    }

    private String getProjectKey(@NotNull Project project) {
        return project.getLocationHash();
    }
}
