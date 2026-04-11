package com.bloxbean.intelliada.idea.nodeint.yano;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the Yano devnet node process lifecycle.
 * Starts/stops the yano-node JAR or native binary.
 */
public class YanoProcessManager {
    private static final Logger LOG = Logger.getInstance(YanoProcessManager.class);

    private static final int HEALTH_POLL_INTERVAL_MS = 500;
    private static final int JVM_STARTUP_TIMEOUT_SEC = 60;
    private static final int NATIVE_STARTUP_TIMEOUT_SEC = 30;
    private static final int SHUTDOWN_TIMEOUT_SEC = 10;

    public enum YanoStatus {
        STOPPED, STARTING, RUNNING, STOPPING, ERROR
    }

    private final String yanoHome;
    private int port = 7070;

    private volatile YanoStatus currentStatus = YanoStatus.STOPPED;
    private final Object statusLock = new Object();
    private OSProcessHandler processHandler;

    public YanoProcessManager(String yanoHome) {
        this.yanoHome = yanoHome;
    }

    public YanoProcessManager(String yanoHome, int port) {
        this.yanoHome = yanoHome;
        this.port = port;
    }

    public YanoStatus getStatus() {
        return currentStatus;
    }

    public int getPort() {
        return port;
    }

    public String getBaseUrl() {
        return "http://localhost:" + port;
    }

    public CompletableFuture<Boolean> startYano() {
        synchronized (statusLock) {
            if (currentStatus == YanoStatus.RUNNING) {
                return CompletableFuture.completedFuture(true);
            }
            if (currentStatus == YanoStatus.STARTING) {
                return CompletableFuture.completedFuture(false);
            }
            currentStatus = YanoStatus.STARTING;
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            GeneralCommandLine commandLine = createStartCommand();
            processHandler = new OSProcessHandler(commandLine);

            processHandler.addProcessListener(new ProcessAdapter() {
                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                    LOG.debug("Yano: " + event.getText().trim());
                }

                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
                    synchronized (statusLock) {
                        if (currentStatus != YanoStatus.STOPPING) {
                            currentStatus = YanoStatus.ERROR;
                        } else {
                            currentStatus = YanoStatus.STOPPED;
                        }
                    }
                    if (!future.isDone()) {
                        future.complete(false);
                    }
                }
            });

            processHandler.startNotify();

            // Poll health endpoint for startup detection
            pollForStartup(future);

        } catch (ExecutionException e) {
            LOG.error("Failed to start Yano", e);
            synchronized (statusLock) {
                currentStatus = YanoStatus.ERROR;
            }
            future.complete(false);
        }

        return future;
    }

    public CompletableFuture<Boolean> stopYano() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        synchronized (statusLock) {
            if (currentStatus == YanoStatus.STOPPED) {
                return CompletableFuture.completedFuture(true);
            }
            currentStatus = YanoStatus.STOPPING;
        }

        if (processHandler != null && !processHandler.isProcessTerminated()) {
            processHandler.destroyProcess();

            // Force kill after timeout
            CompletableFuture.delayedExecutor(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS).execute(() -> {
                if (processHandler != null && !processHandler.isProcessTerminated()) {
                    processHandler.destroyProcess();
                }
                synchronized (statusLock) {
                    currentStatus = YanoStatus.STOPPED;
                }
                if (!future.isDone()) {
                    future.complete(true);
                }
            });

            // Check if already terminated
            if (processHandler.isProcessTerminated()) {
                synchronized (statusLock) {
                    currentStatus = YanoStatus.STOPPED;
                }
                future.complete(true);
            }
        } else {
            synchronized (statusLock) {
                currentStatus = YanoStatus.STOPPED;
            }
            future.complete(true);
        }

        return future;
    }

    public boolean isHealthy() {
        try {
            URL url = new URL(getBaseUrl() + "/q/health/ready");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode == 200;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isProcessAlive() {
        return processHandler != null && !processHandler.isProcessTerminated();
    }

    private GeneralCommandLine createStartCommand() {
        // Resolve actual home (zip may extract into a subdirectory)
        String resolvedHome = YanoDownloader.findYanoHome(java.nio.file.Path.of(yanoHome));
        if (resolvedHome == null) resolvedHome = yanoHome;

        File homeDir = new File(resolvedHome);
        // Binary names vary: "yano" or "yano-node" for native, "yano.sh" or "yano-node.sh" for script
        File nativeBinary = findFile(resolvedHome, "yano", "yano-node");
        File shellScript = findFile(resolvedHome, "yano.sh", "yano-node.sh");
        File jarFile = findFile(resolvedHome, "yano-node.jar", "yano.jar");

        GeneralCommandLine commandLine;
        if (nativeBinary.exists() && nativeBinary.canExecute()) {
            commandLine = new GeneralCommandLine(nativeBinary.getAbsolutePath());
            commandLine.addParameter("-Dquarkus.profile=devnet");
            commandLine.addParameter("-Dquarkus.http.port=" + port);
        } else if (shellScript.exists() && shellScript.canExecute()) {
            commandLine = new GeneralCommandLine(shellScript.getAbsolutePath(), "--devnet");
            commandLine.withEnvironment("JAVA_OPTS", "-Dquarkus.http.port=" + port);
        } else if (jarFile.exists()) {
            commandLine = new GeneralCommandLine("java");
            commandLine.addParameter("-Dquarkus.profile=devnet");
            commandLine.addParameter("-Dquarkus.http.port=" + port);
            commandLine.addParameter("-jar");
            commandLine.addParameter(jarFile.getAbsolutePath());
        } else {
            throw new IllegalStateException("No yano-node binary, script, or jar found in " + resolvedHome
                    + " (searched from " + yanoHome + ")");
        }

        commandLine.setWorkDirectory(resolvedHome);
        commandLine.setRedirectErrorStream(true);
        LOG.info("Yano start command: " + commandLine.getCommandLineString());
        return commandLine;
    }

    private void pollForStartup(CompletableFuture<Boolean> future) {
        boolean isNative = new File(yanoHome, isWindows() ? "yano-node.exe" : "yano-node").exists();
        int timeoutSec = isNative ? NATIVE_STARTUP_TIMEOUT_SEC : JVM_STARTUP_TIMEOUT_SEC;

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "yano-startup-poll");
            t.setDaemon(true);
            return t;
        });

        long startTime = System.currentTimeMillis();
        scheduler.scheduleAtFixedRate(() -> {
            if (future.isDone()) {
                scheduler.shutdown();
                return;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > timeoutSec * 1000L) {
                LOG.warn("Yano startup timed out after " + timeoutSec + "s");
                synchronized (statusLock) {
                    currentStatus = YanoStatus.ERROR;
                }
                future.complete(false);
                scheduler.shutdown();
                return;
            }

            if (isHealthy()) {
                synchronized (statusLock) {
                    currentStatus = YanoStatus.RUNNING;
                }
                LOG.info("Yano devnet started successfully on port " + port);
                future.complete(true);
                scheduler.shutdown();
            }
        }, HEALTH_POLL_INTERVAL_MS, HEALTH_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Find the first existing file matching any of the given names in the directory.
     */
    private static File findFile(String dir, String... names) {
        for (String name : names) {
            File f = new File(dir, name);
            if (f.exists()) return f;
        }
        return new File(dir, names[0]); // Return first as default (for error messages)
    }

    private static boolean isWindows() {
        return SystemInfo.isWindows;
    }
}
