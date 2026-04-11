package com.bloxbean.intelliada.idea.nodeint.yano;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.SystemInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads Yano releases from GitHub.
 * Supports version selection and installs to ~/.intelliada/yano/{version}/.
 * Prefers native binary for the platform, falls back to JVM JAR.
 */
public class YanoDownloader {
    private static final Logger LOG = Logger.getInstance(YanoDownloader.class);
    private static final String GITHUB_RELEASES_URL = "https://api.github.com/repos/bloxbean/yano/releases";
    public static final String DEFAULT_INSTALL_BASE = System.getProperty("user.home")
            + File.separator + ".intelliada" + File.separator + "yano";

    private final Path installDir;
    private Runnable onComplete;

    public YanoDownloader(Path installDir) {
        this.installDir = installDir;
    }

    public void setOnComplete(Runnable onComplete) {
        this.onComplete = onComplete;
    }

    /**
     * Fetch available Yano versions from GitHub releases.
     */
    public static List<ReleaseInfo> fetchAvailableVersions() throws IOException {
        String body = httpGetString(GITHUB_RELEASES_URL);
        JSONArray releases = new JSONArray(body);
        List<ReleaseInfo> versions = new ArrayList<>();

        for (int i = 0; i < releases.length(); i++) {
            JSONObject rel = releases.getJSONObject(i);
            String tag = rel.getString("tag_name");
            boolean prerelease = rel.optBoolean("prerelease", false);

            // Find platform-specific asset
            JSONArray assets = rel.getJSONArray("assets");
            String nativeUrl = null;
            String jvmUrl = null;
            String platformSuffix = getPlatformSuffix();

            for (int j = 0; j < assets.length(); j++) {
                JSONObject asset = assets.getJSONObject(j);
                String name = asset.getString("name");
                String url = asset.getString("browser_download_url");
                long size = asset.getLong("size");

                if (name.contains("native") && name.contains(platformSuffix) && name.endsWith(".zip")) {
                    nativeUrl = url;
                } else if (!name.contains("native") && name.endsWith(".zip")) {
                    jvmUrl = url;
                }
            }

            String downloadUrl = nativeUrl != null ? nativeUrl : jvmUrl;
            boolean isNative = nativeUrl != null;
            if (downloadUrl != null) {
                versions.add(new ReleaseInfo(tag, prerelease, downloadUrl, isNative));
            }
        }
        return versions;
    }

    /**
     * Install Yano in a background task with progress.
     */
    public void install(String downloadUrl) {
        Task.Backgroundable task = new Task.Backgroundable(null, "Downloading Yano...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText("Downloading Yano...");
                    indicator.setFraction(0.1);

                    Files.createDirectories(installDir);
                    Path zipPath = installDir.resolve("yano.zip");
                    downloadFile(downloadUrl, zipPath, indicator);

                    indicator.setText("Extracting Yano...");
                    indicator.setFraction(0.8);

                    unzip(zipPath, installDir);
                    Files.deleteIfExists(zipPath);

                    if (!SystemInfo.isWindows) {
                        setExecutableRecursive(installDir);
                    }

                    indicator.setFraction(1.0);
                    showNotification("Yano installed at " + installDir, NotificationType.INFORMATION);

                    if (onComplete != null) {
                        onComplete.run();
                    }
                } catch (Exception e) {
                    LOG.error("Failed to install Yano", e);
                    showNotification("Failed: " + e.getMessage(), NotificationType.ERROR);
                }
            }
        };
        ProgressManager.getInstance().run(task);
    }

    /**
     * Find the Yano home directory (containing yano-node.jar or yano-node binary).
     */
    public static String findYanoHome(Path installDir) {
        // Direct: installDir/yano-node.jar or installDir/yano-node
        if (hasYanoBinary(installDir)) {
            return installDir.toString();
        }

        // Subdirectory: installDir/yano-*/yano-node.jar
        try {
            return Files.walk(installDir, 3)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.equals("yano-node.jar") || name.equals("yano-node.sh")
                                || name.equals("yano.sh") || name.equals("yano.jar")
                                || (name.equals("yano") && Files.isExecutable(p) && !Files.isDirectory(p));
                    })
                    .map(p -> p.getParent().toString())
                    .findFirst()
                    .orElse(installDir.toString());
        } catch (IOException e) {
            return installDir.toString();
        }
    }

    /**
     * Get the default install path for a specific version.
     */
    public static Path getVersionInstallDir(String version) {
        return Path.of(DEFAULT_INSTALL_BASE, version);
    }

    /**
     * Check if a Yano installation exists at the given path.
     */
    public static boolean isInstalled(Path dir) {
        return hasYanoBinary(dir) || findYanoHome(dir) != null;
    }

    private static boolean hasYanoBinary(Path dir) {
        return Files.exists(dir.resolve("yano-node.jar"))
                || Files.exists(dir.resolve("yano-node"))
                || Files.exists(dir.resolve("yano-node.sh"))
                || Files.exists(dir.resolve("yano"))
                || Files.exists(dir.resolve("yano.sh"))
                || Files.exists(dir.resolve("yano.jar"));
    }

    private static String getPlatformSuffix() {
        if (SystemInfo.isMac) return "macos-arm64";
        if (SystemInfo.isLinux) return "linux-x64";
        if (SystemInfo.isWindows) return "windows-x64";
        return "linux-x64";
    }

    private void downloadFile(String urlStr, Path target, ProgressIndicator indicator) throws IOException {
        HttpURLConnection conn = openConnection(urlStr);
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
                    indicator.setFraction(0.1 + 0.7 * ((double) downloaded / totalSize));
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

    private void setExecutableRecursive(Path dir) {
        try {
            Files.walk(dir, 4)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.equals("yano-node") || name.equals("yano-node.sh")
                                || name.equals("yano") || name.equals("yano.sh");
                    })
                    .forEach(p -> p.toFile().setExecutable(true));
        } catch (IOException e) {
            LOG.warn("Could not set executable permissions", e);
        }
    }

    private static HttpURLConnection openConnection(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);

        // Follow GitHub redirects
        int status = conn.getResponseCode();
        if (status == 302 || status == 301) {
            String redirect = conn.getHeaderField("Location");
            conn.disconnect();
            conn = (HttpURLConnection) new URL(redirect).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
        }
        return conn;
    }

    private static String httpGetString(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        if (conn.getResponseCode() != 200) {
            throw new IOException("GitHub API returned " + conn.getResponseCode());
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private void showNotification(String content, NotificationType type) {
        Notifications.Bus.notify(new Notification("Yano", "Yano Install", content, type));
    }

    /**
     * Release info from GitHub.
     */
    public static class ReleaseInfo {
        public final String tag;
        public final boolean prerelease;
        public final String downloadUrl;
        public final boolean isNative;

        public ReleaseInfo(String tag, boolean prerelease, String downloadUrl, boolean isNative) {
            this.tag = tag;
            this.prerelease = prerelease;
            this.downloadUrl = downloadUrl;
            this.isNative = isNative;
        }

        @Override
        public String toString() {
            return tag + (isNative ? " (native)" : " (JVM)") + (prerelease ? " [pre-release]" : "");
        }
    }
}
