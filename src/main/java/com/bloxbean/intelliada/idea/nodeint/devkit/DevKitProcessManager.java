package com.bloxbean.intelliada.idea.nodeint.devkit;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DevKitProcessManager {
    private static final Logger LOG = Logger.getInstance(DevKitProcessManager.class);
    
    public enum DevKitStatus {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING,
        ERROR
    }
    
    private final Path devKitHomePath;
    private final String baseUrl;
    private final int port;
    private ProcessHandler processHandler;
    private DevKitStatus currentStatus = DevKitStatus.STOPPED;
    private final Object statusLock = new Object();
    
    public DevKitProcessManager(String devKitHome) {
        this(devKitHome, "http://localhost:8080", 8080);
    }
    
    public DevKitProcessManager(String devKitHome, String baseUrl, int port) {
        this.devKitHomePath = Paths.get(devKitHome);
        this.baseUrl = baseUrl;
        this.port = port;
    }
    
    public CompletableFuture<Boolean> startDevKit() {
        return startDevKit(false);
    }
    
    public CompletableFuture<Boolean> startDevKit(boolean interactive) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        synchronized (statusLock) {
            if (currentStatus == DevKitStatus.RUNNING) {
                future.complete(true);
                return future;
            }
            
            if (currentStatus == DevKitStatus.STARTING) {
                future.complete(false);
                return future;
            }
            
            currentStatus = DevKitStatus.STARTING;
        }
        
        try {
            GeneralCommandLine commandLine = createStartCommand(interactive);
            processHandler = new OSProcessHandler(commandLine);
            
            processHandler.addProcessListener(new ProcessAdapter() {
                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                    String text = event.getText().trim();
                    LOG.info("DevKit output: " + text);
                    
                    if (text.contains("Started YaciDevKitApplication")) {
                        synchronized (statusLock) {
                            currentStatus = DevKitStatus.RUNNING;
                        }
                        notifyUser("DevKit Started", "Yaci DevKit is now running on port " + port, NotificationType.INFORMATION);
                        future.complete(true);
                    }
                }
                
                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
                    synchronized (statusLock) {
                        currentStatus = DevKitStatus.STOPPED;
                    }
                    LOG.info("DevKit process terminated with exit code: " + event.getExitCode());
                    if (!future.isDone()) {
                        future.complete(false);
                    }
                }
            });
            
            processHandler.startNotify();
            
            // Timeout after 60 seconds if not started
            CompletableFuture.delayedExecutor(60, TimeUnit.SECONDS).execute(() -> {
                if (!future.isDone()) {
                    synchronized (statusLock) {
                        currentStatus = DevKitStatus.ERROR;
                    }
                    notifyUser("DevKit Start Failed", "DevKit failed to start within 60 seconds", NotificationType.ERROR);
                    future.complete(false);
                }
            });
            
        } catch (ExecutionException e) {
            LOG.error("Failed to start DevKit", e);
            synchronized (statusLock) {
                currentStatus = DevKitStatus.ERROR;
            }
            notifyUser("DevKit Start Failed", "Failed to start DevKit: " + e.getMessage(), NotificationType.ERROR);
            future.complete(false);
        }
        
        return future;
    }
    
    public CompletableFuture<Boolean> stopDevKit() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        synchronized (statusLock) {
            if (currentStatus == DevKitStatus.STOPPED) {
                future.complete(true);
                return future;
            }
            
            if (processHandler == null || processHandler.isProcessTerminated()) {
                currentStatus = DevKitStatus.STOPPED;
                future.complete(true);
                return future;
            }
            
            currentStatus = DevKitStatus.STOPPING;
        }
        
        try {
            // First try graceful shutdown
            processHandler.destroyProcess();
            
            // Wait for process to terminate
            CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> {
                if (processHandler != null && !processHandler.isProcessTerminated()) {
                    // Force kill if not terminated
                    processHandler.destroyProcess();
                }
                
                synchronized (statusLock) {
                    currentStatus = DevKitStatus.STOPPED;
                }
                notifyUser("DevKit Stopped", "Yaci DevKit has been stopped", NotificationType.INFORMATION);
                future.complete(true);
            });
            
        } catch (Exception e) {
            LOG.error("Failed to stop DevKit", e);
            synchronized (statusLock) {
                currentStatus = DevKitStatus.ERROR;
            }
            notifyUser("DevKit Stop Failed", "Failed to stop DevKit: " + e.getMessage(), NotificationType.ERROR);
            future.complete(false);
        }
        
        return future;
    }
    
    public DevKitStatus getStatus() {
        synchronized (statusLock) {
            return currentStatus;
        }
    }
    
    public boolean isRunning() {
        return getStatus() == DevKitStatus.RUNNING && isHealthy();
    }
    
    public boolean isHealthy() {
        try {
            URL url = new URL(baseUrl + "/api/v1/health");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (IOException e) {
            LOG.debug("Health check failed", e);
            return false;
        }
    }
    
    private GeneralCommandLine createStartCommand(boolean interactive) {
        GeneralCommandLine commandLine = new GeneralCommandLine();
        
        String scriptName = getDevKitScriptName();
        File scriptFile = devKitHomePath.resolve("yaci-devkit").resolve(scriptName).toFile();
        
        if (!scriptFile.exists()) {
            throw new IllegalStateException("DevKit script not found: " + scriptFile.getAbsolutePath());
        }
        
        commandLine.setExePath(scriptFile.getAbsolutePath());
        commandLine.setWorkDirectory(devKitHomePath.resolve("yaci-devkit").toFile());
        
        if (interactive) {
            commandLine.addParameter("up");
            commandLine.addParameter("--interactive");
        } else {
            commandLine.addParameter("up");
        }
        
        return commandLine;
    }
    
    private String getDevKitScriptName() {
        return SystemInfo.isWindows ? "devkit.bat" : "devkit.sh";
    }
    
    private void notifyUser(String title, String message, NotificationType type) {
        Notifications.Bus.notify(new Notification("DevKit", title, message, type));
    }
    
    @Nullable
    public ProcessHandler getProcessHandler() {
        return processHandler;
    }
}