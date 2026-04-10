package com.bloxbean.intelliada.idea.nodeint.devkit;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class DevKitStatusMonitor {
    private static final Logger LOG = Logger.getInstance(DevKitStatusMonitor.class);
    private static final int MONITORING_INTERVAL_MS = 5000; // 5 seconds
    
    public interface StatusChangeListener {
        void onStatusChanged(DevKitProcessManager.DevKitStatus oldStatus, DevKitProcessManager.DevKitStatus newStatus);
        void onHealthChanged(boolean healthy);
    }
    
    private final DevKitProcessManager processManager;
    private final Alarm alarm;
    private final AtomicBoolean isMonitoring = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<StatusChangeListener> listeners = new CopyOnWriteArrayList<>();
    
    private DevKitProcessManager.DevKitStatus lastStatus = DevKitProcessManager.DevKitStatus.STOPPED;
    private boolean lastHealthy = false;
    
    public DevKitStatusMonitor(@NotNull DevKitProcessManager processManager) {
        this.processManager = processManager;
        this.alarm = new Alarm();
    }
    
    public void startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            LOG.info("Starting DevKit status monitoring");
            scheduleNextCheck();
        }
    }
    
    public void stopMonitoring() {
        if (isMonitoring.compareAndSet(true, false)) {
            LOG.info("Stopping DevKit status monitoring");
            alarm.cancelAllRequests();
            Disposer.dispose(alarm);
        }
    }
    
    public void addStatusChangeListener(@NotNull StatusChangeListener listener) {
        listeners.add(listener);
    }
    
    public void removeStatusChangeListener(@NotNull StatusChangeListener listener) {
        listeners.remove(listener);
    }
    
    public DevKitProcessManager.DevKitStatus getCurrentStatus() {
        return processManager.getStatus();
    }
    
    public boolean isCurrentlyHealthy() {
        return processManager.isHealthy();
    }
    
    private void scheduleNextCheck() {
        if (isMonitoring.get()) {
            alarm.addRequest(this::performStatusCheck, MONITORING_INTERVAL_MS);
        }
    }
    
    private void performStatusCheck() {
        try {
            DevKitProcessManager.DevKitStatus currentStatus = processManager.getStatus();
            boolean currentHealthy = processManager.isHealthy();
            
            // Check for status changes
            if (currentStatus != lastStatus) {
                LOG.debug("DevKit status changed from " + lastStatus + " to " + currentStatus);
                notifyStatusChange(lastStatus, currentStatus);
                lastStatus = currentStatus;
            }
            
            // Check for health changes
            if (currentHealthy != lastHealthy) {
                LOG.debug("DevKit health changed from " + lastHealthy + " to " + currentHealthy);
                notifyHealthChange(currentHealthy);
                lastHealthy = currentHealthy;
            }
            
            // Handle special cases
            if (currentStatus == DevKitProcessManager.DevKitStatus.RUNNING && !currentHealthy) {
                LOG.warn("DevKit is marked as running but health check failed");
                // Could trigger a restart or status correction here
            }
            
        } catch (Exception e) {
            LOG.error("Error during DevKit status check", e);
        } finally {
            scheduleNextCheck();
        }
    }
    
    private void notifyStatusChange(DevKitProcessManager.DevKitStatus oldStatus, DevKitProcessManager.DevKitStatus newStatus) {
        for (StatusChangeListener listener : listeners) {
            try {
                listener.onStatusChanged(oldStatus, newStatus);
            } catch (Exception e) {
                LOG.error("Error notifying status change listener", e);
            }
        }
    }
    
    private void notifyHealthChange(boolean healthy) {
        for (StatusChangeListener listener : listeners) {
            try {
                listener.onHealthChanged(healthy);
            } catch (Exception e) {
                LOG.error("Error notifying health change listener", e);
            }
        }
    }
    
    public String getStatusDisplayText() {
        DevKitProcessManager.DevKitStatus status = getCurrentStatus();
        boolean healthy = isCurrentlyHealthy();
        
        switch (status) {
            case STOPPED:
                return "Stopped";
            case STARTING:
                return "Starting...";
            case RUNNING:
                return healthy ? "Running" : "Running (Unhealthy)";
            case STOPPING:
                return "Stopping...";
            case ERROR:
                return "Error";
            default:
                return "Unknown";
        }
    }
    
    public boolean isRunningAndHealthy() {
        return getCurrentStatus() == DevKitProcessManager.DevKitStatus.RUNNING && isCurrentlyHealthy();
    }
}