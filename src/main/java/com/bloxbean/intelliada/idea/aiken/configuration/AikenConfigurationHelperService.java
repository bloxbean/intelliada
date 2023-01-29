package com.bloxbean.intelliada.idea.aiken.configuration;

import com.bloxbean.intelliada.idea.aiken.configuration.service.AikenProjectState;
import com.bloxbean.intelliada.idea.aiken.configuration.service.AikenSDKState;
import com.bloxbean.intelliada.idea.aiken.configuration.ui.AikenSDKDialog;
import com.bloxbean.intelliada.idea.aiken.messaging.AikenProjectNodeConfigChangeNotifier;
import com.bloxbean.intelliada.idea.aiken.messaging.AikenSDKChangeNotifier;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class AikenConfigurationHelperService {

    public static AikenSDK getCompilerLocalSDK(Project project) {
        AikenProjectState projectState = AikenProjectState.getInstance(project);
        AikenProjectState.ConfigType compilerType = projectState.getState().getSdkType();
        String compilerId = projectState.getState().getSdkId();

        if(AikenProjectState.ConfigType.local_sdk == compilerType
                &&!StringUtil.isEmpty(compilerId)) {
            List<AikenSDK> sdks = AikenSDKState.getInstance().getSdks();
            for(AikenSDK sdk: sdks) {
                if(compilerId.equals(sdk.getId())) {
                    return sdk;
                }
            }
        }

        //Check if aiken exec is there in .cargo folder
        String aikenPath = System.getProperty("user.home") + File.separator + ".cargo" + File.separator + "bin";
        if (Path.of(aikenPath, getAikenCommand()).toFile().exists()) {
            return new AikenSDK("some id", "some aiken name", aikenPath, "0.0");
        }

        return null;
    }


    public static AikenSDK createOrUpdateLocalSDKConfiguration(Project project, AikenSDK existingLocalSdk) {
        AikenSDKState stateService = AikenSDKState.getInstance();
        AikenSDKDialog localSDKDialog = new AikenSDKDialog(project, existingLocalSdk);
        boolean ok = localSDKDialog.showAndGet();
        if (ok) {
            //save and return
            AikenSDK sdk = new AikenSDK();

            if (existingLocalSdk == null) {
                sdk.setId(UUID.randomUUID().toString());
            } else {
                sdk.setId(existingLocalSdk.getId());
            }

            sdk.setPath(localSDKDialog.getPath());
            sdk.setName(localSDKDialog.getName());
            sdk.setVersion(localSDKDialog.getVersion());

            if (existingLocalSdk == null) {
                stateService.addLocalSdk(sdk);

                AikenSDKChangeNotifier sdkChangeNotifier = ApplicationManager.getApplication().getMessageBus().syncPublisher(AikenSDKChangeNotifier.CHANGE_AIKEN_SDK_TOPIC);
                sdkChangeNotifier.sdkAdded(sdk);
            } else {
                stateService.updateLocalSdk(sdk);

                AikenSDKChangeNotifier sdkChangeNotifier = ApplicationManager.getApplication().getMessageBus().syncPublisher(AikenSDKChangeNotifier.CHANGE_AIKEN_SDK_TOPIC);
                sdkChangeNotifier.sdkUpdated(sdk);
            }

            return sdk;
        } else {
            return null;
        }
    }

    public static void notifyProjectNodeConfigChange(Project project) {
        AikenProjectNodeConfigChangeNotifier aikenProjectNodeConfigChangeNotifier
                = ApplicationManager.getApplication().getMessageBus().syncPublisher(AikenProjectNodeConfigChangeNotifier.CHANGE_AIKEN_PROJECT_NODES_CONFIG_TOPIC);
        aikenProjectNodeConfigChangeNotifier.configUpdated(project);
    }

    private static String getAikenCommand() {
        String aikenCmd = "aiken";
        if(SystemInfo.isWindows)
            aikenCmd = "aiken.exe";

        return aikenCmd;
    }
}
