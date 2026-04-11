package com.bloxbean.intelliada.idea.nodeint.devkit;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public final class DevKitLifecycleService implements StartupActivity {
    private static final Logger LOG = Logger.getInstance(DevKitLifecycleService.class);
    
    private final ConcurrentMap<String, DevKitProcessManager> processManagers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DevKitStatusMonitor> statusMonitors = new ConcurrentHashMap<>();
    
    public static DevKitLifecycleService getInstance() {
        return ApplicationManager.getApplication().getService(DevKitLifecycleService.class);
    }
    
    @Override
    public void runActivity(@NotNull Project project) {
        // Initialize project-specific DevKit management
        LOG.info("Initializing DevKit lifecycle service for project: " + project.getName());
        
        // Register project close listener
        project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectClosed(@NotNull Project closedProject) {
                if (closedProject.equals(project)) {
                    cleanupProject(project);
                }
            }
        });
    }
    
    public CompletableFuture<Boolean> startDevKit(@NotNull Project project, @NotNull String devKitHome) {
        return startDevKit(project, devKitHome, false);
    }
    
    public CompletableFuture<Boolean> startDevKit(@NotNull Project project, @NotNull String devKitHome, boolean interactive) {
        String projectKey = getProjectKey(project);
        
        DevKitProcessManager processManager = processManagers.computeIfAbsent(projectKey, 
            k -> new DevKitProcessManager(devKitHome));
        
        // Start status monitoring
        DevKitStatusMonitor statusMonitor = statusMonitors.computeIfAbsent(projectKey,
            k -> new DevKitStatusMonitor(processManager));
        statusMonitor.startMonitoring();
        
        LOG.info("Starting DevKit for project: " + project.getName() + " at: " + devKitHome);
        return processManager.startDevKit(interactive);
    }
    
    public CompletableFuture<Boolean> stopDevKit(@NotNull Project project) {
        String projectKey = getProjectKey(project);
        DevKitProcessManager processManager = processManagers.get(projectKey);
        
        if (processManager != null) {
            LOG.info("Stopping DevKit for project: " + project.getName());
            
            // Stop status monitoring
            DevKitStatusMonitor statusMonitor = statusMonitors.get(projectKey);
            if (statusMonitor != null) {
                statusMonitor.stopMonitoring();
            }
            
            return processManager.stopDevKit();
        }
        
        return CompletableFuture.completedFuture(true);
    }
    
    public DevKitProcessManager.DevKitStatus getDevKitStatus(@NotNull Project project) {
        String projectKey = getProjectKey(project);
        DevKitProcessManager processManager = processManagers.get(projectKey);
        
        if (processManager != null) {
            return processManager.getStatus();
        }
        
        return DevKitProcessManager.DevKitStatus.STOPPED;
    }
    
    public boolean isDevKitRunning(@NotNull Project project) {
        String projectKey = getProjectKey(project);
        DevKitProcessManager processManager = processManagers.get(projectKey);
        
        return processManager != null && processManager.isRunning();
    }
    
    public boolean isDevKitHealthy(@NotNull Project project) {
        String projectKey = getProjectKey(project);
        DevKitProcessManager processManager = processManagers.get(projectKey);
        
        return processManager != null && processManager.isHealthy();
    }
    
    @Nullable
    public DevKitProcessManager getProcessManager(@NotNull Project project) {
        String projectKey = getProjectKey(project);
        return processManagers.get(projectKey);
    }
    
    @Nullable
    public DevKitStatusMonitor getStatusMonitor(@NotNull Project project) {
        String projectKey = getProjectKey(project);
        return statusMonitors.get(projectKey);
    }
    
    public CompletableFuture<Boolean> restartDevKit(@NotNull Project project) {
        LOG.info("Restarting DevKit for project: " + project.getName());
        
        return stopDevKit(project).thenCompose(stopped -> {
            if (stopped) {
                String projectKey = getProjectKey(project);
                DevKitProcessManager processManager = processManagers.get(projectKey);
                if (processManager != null) {
                    return processManager.startDevKit();
                }
            }
            return CompletableFuture.completedFuture(false);
        });
    }
    
    public void cleanupProject(@NotNull Project project) {
        String projectKey = getProjectKey(project);
        LOG.info("Cleaning up DevKit resources for project: " + project.getName());
        
        // Stop status monitoring
        DevKitStatusMonitor statusMonitor = statusMonitors.remove(projectKey);
        if (statusMonitor != null) {
            statusMonitor.stopMonitoring();
        }
        
        // Stop DevKit process
        DevKitProcessManager processManager = processManagers.remove(projectKey);
        if (processManager != null) {
            processManager.stopDevKit();
        }
    }
    
    public void cleanupAll() {
        LOG.info("Cleaning up all DevKit resources");
        
        // Stop all status monitors
        statusMonitors.values().forEach(DevKitStatusMonitor::stopMonitoring);
        statusMonitors.clear();
        
        // Stop all DevKit processes
        processManagers.values().forEach(DevKitProcessManager::stopDevKit);
        processManagers.clear();
    }
    
    private String getProjectKey(@NotNull Project project) {
        return project.getLocationHash();
    }
}