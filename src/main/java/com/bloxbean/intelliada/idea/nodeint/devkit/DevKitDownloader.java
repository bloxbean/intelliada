package com.bloxbean.intelliada.idea.nodeint.devkit;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DevKitDownloader {

    private final Path installDir;

    public DevKitDownloader(Path installDir) {
        this.installDir = installDir;
    }

    public void installSDK() {
        new Task.Backgroundable(null, "Installing SDK...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    // Fetch latest release info
                    String zipUrl = fetchLatestReleaseZipUrl();
                    if (zipUrl == null)
                        throw new IllegalStateException("No download URL found for the latest release.");

                    // Define paths for download and extraction
                    Path downloadPath = installDir.resolve("yaci-devkit.zip");

                    // Download the zip file
                    downloadZip(zipUrl, installDir.toFile().getAbsolutePath());

                    // Extract the zip file
                    unzip(downloadPath, installDir);
//                    extractZip(downloadPath.toFile().getAbsolutePath(), installDir.toFile().getAbsolutePath());

                    // Notify user of success
                    Notifications.Bus.notify(new Notification("Install SDK", "Success", "SDK installed successfully.", NotificationType.INFORMATION));
                } catch (Exception e) {
                    // Notify user of error
                    e.printStackTrace();
                    Notifications.Bus.notify(new Notification("Install SDK", "Error", "Failed to install SDK: " + e.getMessage(), NotificationType.ERROR));
                }
            }
        }.queue();
    }

    private String fetchLatestReleaseZipUrl() throws IOException, InterruptedException {
        String apiUrl = "https://api.github.com/repos/bloxbean/yaci-devkit/releases/latest";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        // Parse JSON to extract download URL
        JSONObject jsonObject = new JSONObject(responseBody);
        JSONArray assets = jsonObject.getJSONArray("assets");
        if (assets.length() > 0) {
            String downloadUrl = null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.getString("name");
                if (name != null &&
                        name.startsWith("yaci-devkit") && name.endsWith(".zip")) {
                    downloadUrl = asset.getString("browser_download_url");
                }
            }
            return downloadUrl;
        } else {
            throw new IllegalStateException("No assets found for the latest release.");
        }
    }

    private Path downloadZip(String downloadUrl, String targetDir) {

        String fileUrl = downloadUrl; // Replace with your file URL

        try {
            URL url = new URL(fileUrl);
            HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
            long fileSize = httpConn.getContentLengthLong();

            //Get file name from url
            String targetFileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1, fileUrl.length());

            Path targetPath = Paths.get(targetDir, targetFileName);
            Files.createDirectories(targetPath.getParent());

            try (InputStream inputStream = httpConn.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(targetPath.toFile())) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesRead = 0;
                int percentCompleted;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    percentCompleted = (int) ((totalBytesRead * 100) / fileSize);
                    //write("\rDownloading: " + percentCompleted + "%");
                }
                System.out.println();
                //writeLn(success("Download complete for %s!", component));
            } catch (Exception e) {
                e.printStackTrace();
            }

            return targetPath;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void extractZip(String filePath, String extractDir) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipArchiveInputStream zipIn = new ZipArchiveInputStream(bis)) {

            ArchiveEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                Path entryDestination = Paths.get(extractDir, entry.getName());
                Files.createDirectories(entryDestination.getParent());

                try (OutputStream out = new FileOutputStream(entryDestination.toFile())) {
                    byte[] buffer = new byte[4096];
                    int len;

                    long bytesRead = 0;

                   // writeLn(infoLabel("Extracting", "Extracting %s", clusterConfig.getYaciCliHome() + File.separator + entry.getName()));
                    while ((len = zipIn.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                        bytesRead += len;
                    }
                }
            }
            System.out.println(); // Move to next line after extraction completes
        }
    }


    private void unzip(Path zipFilePath, Path destDir) throws IOException {
        byte[] buffer = new byte[1024];
        File destDirFile = destDir.toFile();
        if (!destDirFile.exists()) {
            destDirFile.mkdirs();
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath.toFile()))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = new File(destDirFile, zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    // Create parent directories if needed
                    new File(newFile.getParent()).mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }

        //rename the extracted folder
        String fileName = zipFilePath.toFile().getName();
        String folderName = fileName.substring(0, fileName.lastIndexOf("."));

        FileUtil.moveDirWithContent(new File(destDirFile, folderName), new File(destDirFile, "yaci-devkit"));
    }
}
