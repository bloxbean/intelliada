package com.bloxbean.intelliada.idea.nodeint.yano;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically monitors Yano health and chain tip.
 * Notifies listeners on status and chain tip changes.
 */
public class YanoStatusMonitor {
    private static final Logger LOG = Logger.getInstance(YanoStatusMonitor.class);
    private static final int MONITORING_INTERVAL_MS = 3000;

    private final YanoProcessManager processManager;
    private final Alarm alarm;
    private final AtomicBoolean isMonitoring = new AtomicBoolean(false);
    private final List<StatusChangeListener> listeners = new CopyOnWriteArrayList<>();

    private YanoProcessManager.YanoStatus lastStatus;
    private boolean lastHealthy;
    private long lastSlot = -1;
    private long lastBlockNumber = -1;

    public interface StatusChangeListener {
        void onStatusChanged(YanoProcessManager.YanoStatus oldStatus, YanoProcessManager.YanoStatus newStatus);
        void onHealthChanged(boolean healthy);
        void onChainTipChanged(long slot, long blockNumber);
    }

    public YanoStatusMonitor(YanoProcessManager processManager, Disposable parentDisposable) {
        this.processManager = processManager;
        this.alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable);
        this.lastStatus = processManager.getStatus();
        this.lastHealthy = false;
    }

    public void startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            scheduleNextCheck();
        }
    }

    public void stopMonitoring() {
        isMonitoring.set(false);
        alarm.cancelAllRequests();
    }

    public void addStatusChangeListener(StatusChangeListener listener) {
        listeners.add(listener);
    }

    public void removeStatusChangeListener(StatusChangeListener listener) {
        listeners.remove(listener);
    }

    public long getLastSlot() {
        return lastSlot;
    }

    public long getLastBlockNumber() {
        return lastBlockNumber;
    }

    private void scheduleNextCheck() {
        if (isMonitoring.get()) {
            alarm.addRequest(this::performStatusCheck, MONITORING_INTERVAL_MS);
        }
    }

    private void performStatusCheck() {
        try {
            YanoProcessManager.YanoStatus currentStatus = processManager.getStatus();
            boolean currentHealthy = processManager.isHealthy();

            if (currentStatus != lastStatus) {
                YanoProcessManager.YanoStatus old = lastStatus;
                lastStatus = currentStatus;
                for (StatusChangeListener listener : listeners) {
                    try {
                        listener.onStatusChanged(old, currentStatus);
                    } catch (Exception e) {
                        LOG.warn("Error notifying status change listener", e);
                    }
                }
            }

            if (currentHealthy != lastHealthy) {
                lastHealthy = currentHealthy;
                for (StatusChangeListener listener : listeners) {
                    try {
                        listener.onHealthChanged(currentHealthy);
                    } catch (Exception e) {
                        LOG.warn("Error notifying health change listener", e);
                    }
                }
            }

            // Fetch chain tip if healthy
            if (currentHealthy && currentStatus == YanoProcessManager.YanoStatus.RUNNING) {
                fetchChainTip();
            }
        } finally {
            scheduleNextCheck();
        }
    }

    private void fetchChainTip() {
        try {
            String json = httpGet(processManager.getBaseUrl() + "/api/v1/blocks/latest");
            if (json != null) {
                JSONObject block = new JSONObject(json);
                long slot = block.optLong("slot", -1);
                long blockNumber = block.optLong("height", block.optLong("block_number", -1));

                if (slot != lastSlot || blockNumber != lastBlockNumber) {
                    lastSlot = slot;
                    lastBlockNumber = blockNumber;
                    for (StatusChangeListener listener : listeners) {
                        try {
                            listener.onChainTipChanged(slot, blockNumber);
                        } catch (Exception e) {
                            LOG.warn("Error notifying chain tip listener", e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not fetch chain tip: " + e.getMessage());
        }
    }

    private String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    return sb.toString();
                }
            }
            conn.disconnect();
        } catch (IOException e) {
            // Expected when node is not running
        }
        return null;
    }
}
