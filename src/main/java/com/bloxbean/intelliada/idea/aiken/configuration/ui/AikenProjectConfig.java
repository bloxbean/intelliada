package com.bloxbean.intelliada.idea.aiken.configuration.ui;

import com.bloxbean.intelliada.idea.aiken.configuration.AikenConfigurationHelperService;
import com.bloxbean.intelliada.idea.aiken.configuration.AikenSDK;
import com.bloxbean.intelliada.idea.aiken.configuration.service.AikenProjectState;
import com.bloxbean.intelliada.idea.aiken.configuration.service.AikenSDKState;
import com.bloxbean.intelliada.idea.common.Tuple;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.twelvemonkeys.lang.StringUtil;

import javax.swing.*;
import java.util.List;

public class AikenProjectConfig {
    private JPanel mainPanel;
    private JComboBox localSDKCB;
    private JButton newLocalSDKBtn;
    private JButton localSDKDetailBtn;

    private AikenSDK emptyLocalSDK = new AikenSDK();
    private boolean configChanged;

    public AikenProjectConfig(Project project) {
        initializeData(project);
        attachHandlers(project);
        setCurrentSelection(project);

        //Don't change this call sequence. This is needed for change notifier
        //Keep it at the end to ignore initial selection
        listenSelectionChange();
    }

    private void initializeData(Project project) {
        populateAvailableLocalSDKs();
    }

    private void setCurrentSelection(Project project) {
        AikenProjectState aikenProjectState = AikenProjectState.getInstance(project);
        AikenProjectState.State state = aikenProjectState.getState();

        //set compiler setting
        if(AikenProjectState.ConfigType.local_sdk == state.getSdkType()){
            setSelectedLocalSDK(localSDKCB, state.getSdkId());
        }
    }

    private void setSelectedLocalSDK(JComboBox cb, String id) {
        for (int i = 0; i < cb.getItemCount(); i++) {
            AikenSDK aikenSDK = (AikenSDK) cb.getItemAt(i);
            if (aikenSDK == null) continue;

            if (aikenSDK.getId() != null && aikenSDK.getId().equals(id)) {
                cb.setSelectedIndex(i);
                break;
            }
        }
    }

    private void populateAvailableLocalSDKs() {
        AikenSDKState localSDKState = ServiceManager.getService(AikenSDKState.class);
        List<AikenSDK> localSdks = localSDKState.getSdks();

        localSDKCB.removeAllItems();
        if (localSdks != null) {
            localSDKCB.addItem(emptyLocalSDK);
            for (AikenSDK localSDK : localSdks) {
                localSDKCB.addItem(localSDK);
            }
        }
    }


    private void attachHandlers(Project project) {

        newLocalSDKBtn.addActionListener(e -> {
            AikenSDK localSDK = AikenConfigurationHelperService.createOrUpdateLocalSDKConfiguration(project, null);
            if (localSDK != null) {
                populateAvailableLocalSDKs();
                setSelectedLocalSDK(localSDKCB, localSDK.getId());
            }
        });

        localSDKDetailBtn.addActionListener(e -> { //Update if required.
            AikenSDK localSDK = (AikenSDK) localSDKCB.getSelectedItem();
            if (localSDK == null || StringUtil.isEmpty(localSDK.getId())) {
                Messages.showWarningDialog("Please select a Aiken SDK first to see the details", "");
                return;
            }

            AikenSDK updatedSDK = AikenConfigurationHelperService.createOrUpdateLocalSDKConfiguration(project, localSDK);
            if (updatedSDK != null) {
                updateLocalSDKInComboBox(localSDKCB, updatedSDK);
            }
        });
    }

    //This is required for change notifier
    private void listenSelectionChange() {
        localSDKCB.addActionListener(e -> {
            configChanged = true;
        });
    }

    public boolean isConfigChanged() {
        return configChanged;
    }


    private void updateLocalSDKInComboBox(JComboBox cb, AikenSDK updatedLocalSDK) {
        int count = cb.getItemCount();
        if (count == 0) return;

        for (int i = 0; i < count; i++) {
            AikenSDK lsdk = (AikenSDK) cb.getItemAt(i);
            if (lsdk == null || StringUtil.isEmpty(lsdk.getId()))
                continue;
            if (lsdk.getId().equals(updatedLocalSDK.getId())) {
                lsdk.updateValues(updatedLocalSDK);
                break;
            }
        }

    }

    public Tuple<AikenProjectState.ConfigType, String> getAikenSdkId() {

            AikenSDK aikenSDK = (AikenSDK) localSDKCB.getSelectedItem();
            if (aikenSDK != null)
                return new Tuple(AikenProjectState.ConfigType.local_sdk, aikenSDK.getId());
            else
                return null;

    }


    public void updateDataToState(AikenProjectState.State state) {
        //Save Compile / Build settings
        Tuple<AikenProjectState.ConfigType, String> compilerSetting = getAikenSdkId();

        if (compilerSetting != null) {
            state.setSdkType(compilerSetting._1());
            state.setSdkId(compilerSetting._2());
        }
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }

    public ValidationInfo doValidate() {
        return null;
    }
}
