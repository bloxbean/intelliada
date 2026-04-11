package com.bloxbean.intelliada.idea.julc.util;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class JulcSdkUtil {
    private static final Logger LOG = Logger.getInstance(JulcSdkUtil.class);

    public static String getVersionString(String julcBinFolder) throws Exception {
        if (julcBinFolder == null) return null;

        String julcCmd = getJulcExecutable();

        File file = new File(julcBinFolder);
        VirtualFile home = LocalFileSystem.getInstance().findFileByIoFile(file);
        if (home != null) {
            try {
                String result = runAndGetVersion(file.getAbsolutePath() + File.separator + julcCmd, "--version");
                LOG.debug(result);
                return result;
            } catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.error(e);
                }
                throw e;
            }
        }
        return null;
    }

    private static String runAndGetVersion(String program, String command) throws InterruptedException, ExecutionException, IOException {
        GeneralCommandLine commandLine = new GeneralCommandLine(program, command);
        Process process = commandLine.createProcess();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            String version = null;
            int lineCount = 1;
            while ((line = reader.readLine()) != null) {
                if (lineCount == 1) {
                    // Expected format: "julc <version>" or just "<version>"
                    String[] parts = line.split(" ");
                    version = parts.length > 1 ? parts[1] : parts[0];
                }
                lineCount++;
            }

            int exitVal = process.waitFor();
            if (exitVal == 0) {
                LOG.debug("Getting julc SDK version. Success!");
                return version;
            }
        }
        return null;
    }

    public static String getJulcExecutable() {
        String julcCmd = "julc";
        if (SystemInfo.isWindows)
            julcCmd = "julc.exe";
        return julcCmd;
    }
}
