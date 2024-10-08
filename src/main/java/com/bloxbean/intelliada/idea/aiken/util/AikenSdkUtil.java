package com.bloxbean.intelliada.idea.aiken.util;

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

public class AikenSdkUtil {
    private final static Logger LOG = Logger.getInstance(AikenSdkUtil.class);

    public static String getVersionString(String aikenBinFolder) throws Exception {
        if (aikenBinFolder == null) return null;

        String goalCmd = "aiken";
        if(SystemInfo.isWindows)
            goalCmd = "aiken.exe";

        File file = new File(aikenBinFolder);
        VirtualFile home = LocalFileSystem.getInstance().findFileByIoFile(file);
        if (home != null) {
            try {
                String result = AikenSdkUtil.runProcessAndExit(file.getAbsolutePath() + File.separator + goalCmd, "--version");
                result.split(" ");
                LOG.debug(result);
                return result;
            } catch (Exception e) {
                if(LOG.isDebugEnabled()) {
                    LOG.error(e);
                }
                throw e;
            }
        }
        return null;
    }

    public static String runProcessAndExit(String program, String command) throws InterruptedException, ExecutionException, IOException {
        GeneralCommandLine commandLine = new GeneralCommandLine(program, command);
        try {

            Process process = commandLine.createProcess();
            StringBuilder output = new StringBuilder();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            int lineCount = 1;
            String version = null;
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
                if(lineCount == 1) {
                    version = line.split(" ")[1];
                }
                lineCount++;
            }

            int exitVal = process.waitFor();
            if (exitVal == 0) {
                LOG.debug("Getting Aiken SDK version. Success!");
                LOG.debug(output.toString());
                return version;
            } else {
                //abnormal...
            }

        } catch (Exception e) {
            if(LOG.isDebugEnabled()) {
                LOG.error(e);
            }
            throw e;
        }

        return null;
    }
}
