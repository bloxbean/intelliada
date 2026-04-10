package com.bloxbean.intelliada.idea.julc.configuration;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads julc CLI from GitHub releases.
 * Platform-aware: selects the correct zip for macOS/Linux/Windows.
 */
public class JulcDownloader {
    private static final Logger LOG = Logger.getInstance(JulcDownloader.class);
    private static final String GITHUB_API_URL = "https://api.github.com/repos/bloxbean/julc/releases/latest";

    private final Path installDir;
    private Runnable onComplete;

    public JulcDownloader(Path installDir) {
        this.installDir = installDir;
    }

    public void setOnComplete(Runnable onComplete) {
        this.onComplete = onComplete;
    }

    public void install() {
        Task.Backgroundable task = new Task.Backgroundable(null, "Downloading julc CLI...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText("Fetching latest julc release...");
                    indicator.setFraction(0.1);

                    String downloadUrl = fetchDownloadUrl();
                    if (downloadUrl == null) {
                        showNotification("julc Install", "No julc release found for this platform.", NotificationType.ERROR);
                        return;
                    }

                    indicator.setText("Downloading julc CLI...");
                    indicator.setFraction(0.3);

                    Files.createDirectories(installDir);
                    Path zipPath = installDir.resolve("julc.zip");
                    downloadFile(downloadUrl, zipPath, indicator);

                    indicator.setText("Extracting julc...");
                    indicator.setFraction(0.8);

                    unzip(zipPath, installDir);
                    Files.deleteIfExists(zipPath);

                    // Set executable permissions on Unix
                    if (!SystemInfo.isWindows) {
                        findAndSetExecutable(installDir);
                    }

                    indicator.setFraction(1.0);
                    showNotification("julc Install", "julc CLI installed at " + installDir, NotificationType.INFORMATION);

                    if (onComplete != null) {
                        onComplete.run();
                    }

                } catch (Exception e) {
                    LOG.error("Failed to install julc CLI", e);
                    showNotification("julc Install", "Failed: " + e.getMessage(), NotificationType.ERROR);
                }
            }
        };

        ProgressManager.getInstance().run(task);
    }

    private String fetchDownloadUrl() throws IOException {
        URL url = new URL(GITHUB_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        if (conn.getResponseCode() != 200) {
            throw new IOException("GitHub API returned " + conn.getResponseCode());
        }

        String body;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            body = sb.toString();
        } finally {
            conn.disconnect();
        }

        JSONObject release = new JSONObject(body);
        JSONArray assets = release.getJSONArray("assets");
        String platformSuffix = getPlatformSuffix();

        LOG.info("Looking for julc asset with platform suffix: " + platformSuffix);

        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.getString("name");
            String assetUrl = asset.getString("browser_download_url");

            LOG.info("  Asset: " + name);

            // Match julc-{version}-{platform}.zip but NOT julc-playground-*
            if (name.startsWith("julc-") && !name.contains("playground")
                    && name.contains(platformSuffix) && name.endsWith(".zip")) {
                LOG.info("  -> Selected: " + assetUrl);
                return assetUrl;
            }
        }

        return null;
    }

    private String getPlatformSuffix() {
        if (SystemInfo.isMac) {
            return "macos-aarch64";
        } else if (SystemInfo.isLinux) {
            return "linux-x86_64";
        } else if (SystemInfo.isWindows) {
            return "windows-x86_64";
        }
        return "linux-x86_64";
    }

    private void downloadFile(String urlStr, Path target, ProgressIndicator indicator) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);

        // GitHub redirects browser_download_url — follow manually if needed
        int status = conn.getResponseCode();
        if (status == 302 || status == 301) {
            String redirect = conn.getHeaderField("Location");
            conn.disconnect();
            conn = (HttpURLConnection) new URL(redirect).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
        }

        long totalSize = conn.getContentLengthLong();
        long downloaded = 0;

        try (InputStream is = conn.getInputStream();
             OutputStream os = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                downloaded += bytesRead;
                if (totalSize > 0) {
                    indicator.setFraction(0.3 + 0.5 * ((double) downloaded / totalSize));
                    indicator.setText2(String.format("%.1f MB / %.1f MB",
                            downloaded / (1024.0 * 1024.0), totalSize / (1024.0 * 1024.0)));
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private void unzip(Path zipPath, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = destDir.resolve(entry.getName()).normalize();
                if (!targetPath.startsWith(destDir)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    try (OutputStream os = Files.newOutputStream(targetPath)) {
                        zis.transferTo(os);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Recursively find and set executable on julc binary.
     */
    private void findAndSetExecutable(Path dir) {
        try {
            Files.walk(dir, 3)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.equals("julc") || name.equals("gradlew") || name.equals("mvnw");
                    })
                    .forEach(p -> p.toFile().setExecutable(true));
        } catch (IOException e) {
            LOG.warn("Could not set executable permissions", e);
        }
    }

    /**
     * Find the julc binary path after extraction.
     * Returns the directory containing the julc executable, or null.
     */
    public static String findJulcBinDir(Path installDir) {
        String julcName = SystemInfo.isWindows ? "julc.exe" : "julc";

        // Direct: installDir/bin/julc
        Path direct = installDir.resolve("bin").resolve(julcName);
        if (Files.exists(direct)) {
            return installDir.resolve("bin").toString();
        }

        // Subdirectory: installDir/julc-*/bin/julc
        try {
            return Files.walk(installDir, 4)
                    .filter(p -> p.getFileName().toString().equals(julcName)
                            && !p.toString().endsWith(".zip")
                            && Files.isRegularFile(p))
                    .map(p -> p.getParent().toString())
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private void showNotification(String title, String content, NotificationType type) {
        Notifications.Bus.notify(new Notification("julc", title, content, type));
    }
}
