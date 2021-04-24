package com.bloxbean.intelliada.idea.core.util;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class CLIProviderUtil {
    private final static Logger LOG = Logger.getInstance(CLIProviderUtil.class);

    public static String getVersionString(String cliFolder) throws Exception {
        if (cliFolder == null) return null;

        String cardanoCLICmd = getCardanoCLICommand();

        File file = new File(cliFolder);
        VirtualFile home = LocalFileSystem.getInstance().findFileByIoFile(file);
        if (home != null) {
            try {
                String result = CLIProviderUtil.runProcessAndExit(file.getAbsolutePath() + File.separator + cardanoCLICmd, "--version");
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

    public static String getCardanoCLICommand() {
        String cardanoCLICmd = "cardano-cli";
        if(SystemInfo.isWindows)
            cardanoCLICmd = "cardano-cli.exe";

        return cardanoCLICmd;
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
                    String[] tokens = line.split(" ");
                    if(tokens.length > 1)
                        version = tokens[1];
                    else
                        version = line;
                }
                lineCount++;
            }

            int exitVal = process.waitFor();
            if (exitVal == 0) {
                LOG.debug("Getting Cardano Node version. Success!");
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

    public static String getSuggestedCLIFolder(String home) {
        if(StringUtil.isEmpty(home))
            return home;

        if(SystemInfo.isMac) {
            if(isMacDaedalusHome(home)) {
                String suggestedPath = home + ".app" + File.separator + "Contents/MacOS";
                if(cardanoCLIExists(suggestedPath))
                    return suggestedPath;
                else
                    return home;
            } else
                return home;
        } else {
            return home;
        }
    }

    public static boolean isMacDaedalusHome(String home) {
        if(home == null)
            return false;
        if(home.matches("/Applications/Daedalus [a-zA-Z0-9]*[/]?")) {
            return true;
        } else
            return false;
    }

    private static boolean cardanoCLIExists(String path) {
        File cli = new File(path, getCardanoCLICommand());
        return cli.exists();
    }

}
