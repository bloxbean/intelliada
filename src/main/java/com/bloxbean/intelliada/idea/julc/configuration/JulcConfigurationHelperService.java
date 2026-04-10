package com.bloxbean.intelliada.idea.julc.configuration;

import com.bloxbean.intelliada.idea.julc.configuration.service.JulcProjectState;
import com.bloxbean.intelliada.idea.julc.configuration.service.JulcSDKState;
import com.bloxbean.intelliada.idea.julc.configuration.ui.JulcSDKDialog;
import com.bloxbean.intelliada.idea.julc.messaging.JulcProjectConfigChangeNotifier;
import com.bloxbean.intelliada.idea.julc.messaging.JulcSDKChangeNotifier;
import com.bloxbean.intelliada.idea.julc.util.JulcSdkUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class JulcConfigurationHelperService {
    private static final Logger LOG = Logger.getInstance(JulcConfigurationHelperService.class);

    public static JulcSDK getCompilerLocalSDK(Project project) {
        JulcProjectState projectState = JulcProjectState.getInstance(project);
        JulcProjectState.ConfigType compilerType = projectState.getState().getSdkType();
        String compilerId = projectState.getState().getSdkId();

        // 1. Check explicitly configured SDK
        if (JulcProjectState.ConfigType.local_sdk == compilerType
                && !StringUtil.isEmpty(compilerId)) {
            List<JulcSDK> sdks = JulcSDKState.getInstance().getSdks();
            for (JulcSDK sdk : sdks) {
                if (compilerId.equals(sdk.getId())) {
                    return sdk;
                }
            }
        }

        // 2. Check ~/.julc/bin/julc
        String julcExe = JulcSdkUtil.getJulcExecutable();
        String julcHome = System.getProperty("user.home") + File.separator + ".julc" + File.separator + "bin";
        if (Path.of(julcHome, julcExe).toFile().exists()) {
            return new JulcSDK("default", "julc", julcHome, "0.0");
        }

        // 3. Check ~/.julc/ (download may extract with subdirectories)
        String julcBaseDir = System.getProperty("user.home") + File.separator + ".julc";
        String foundBinDir = JulcDownloader.findJulcBinDir(Path.of(julcBaseDir));
        if (foundBinDir != null) {
            return new JulcSDK("default", "julc", foundBinDir, "0.0");
        }

        // 4. Check system PATH
        String pathResult = findOnSystemPath(julcExe);
        if (pathResult != null) {
            return new JulcSDK("system", "julc (system)", pathResult, "0.0");
        }

        return null;
    }

    /**
     * Check if julc is available on the system PATH.
     * Returns the directory containing the executable, or null.
     */
    private static String findOnSystemPath(String executable) {
        try {
            String cmd = SystemInfo.isWindows ? "where" : "which";
            Process process = new ProcessBuilder(cmd, executable)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                int exitCode = process.waitFor();
                if (exitCode == 0 && line != null && !line.isBlank()) {
                    // "which julc" returns full path like /usr/local/bin/julc
                    return new File(line.trim()).getParent();
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not check system PATH for " + executable, e);
        }
        return null;
    }

    public static JulcSDK createOrUpdateSDKConfiguration(Project project, JulcSDK existingSdk) {
        JulcSDKState stateService = JulcSDKState.getInstance();
        JulcSDKDialog sdkDialog = new JulcSDKDialog(project, existingSdk);
        boolean ok = sdkDialog.showAndGet();
        if (ok) {
            JulcSDK sdk = new JulcSDK();

            if (existingSdk == null) {
                sdk.setId(UUID.randomUUID().toString());
            } else {
                sdk.setId(existingSdk.getId());
            }

            sdk.setPath(sdkDialog.getPath());
            sdk.setName(sdkDialog.getName());
            sdk.setVersion(sdkDialog.getVersion());

            if (existingSdk == null) {
                stateService.addSdk(sdk);
                ApplicationManager.getApplication().getMessageBus()
                        .syncPublisher(JulcSDKChangeNotifier.CHANGE_JULC_SDK_TOPIC)
                        .sdkAdded(sdk);
            } else {
                stateService.updateSdk(sdk);
                ApplicationManager.getApplication().getMessageBus()
                        .syncPublisher(JulcSDKChangeNotifier.CHANGE_JULC_SDK_TOPIC)
                        .sdkUpdated(sdk);
            }

            return sdk;
        }
        return null;
    }

    public static void notifyProjectConfigChange(Project project) {
        ApplicationManager.getApplication().getMessageBus()
                .syncPublisher(JulcProjectConfigChangeNotifier.CHANGE_JULC_PROJECT_CONFIG_TOPIC)
                .configUpdated(project);
    }
}
