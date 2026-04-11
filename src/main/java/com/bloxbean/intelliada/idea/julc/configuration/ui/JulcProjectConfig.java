package com.bloxbean.intelliada.idea.julc.configuration.ui;

import com.bloxbean.intelliada.idea.julc.configuration.JulcConfigurationHelperService;
import com.bloxbean.intelliada.idea.julc.configuration.JulcSDK;
import com.bloxbean.intelliada.idea.julc.configuration.service.JulcProjectState;
import com.bloxbean.intelliada.idea.julc.configuration.service.JulcSDKState;
import com.bloxbean.intelliada.idea.common.Tuple;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class JulcProjectConfig {
    private JPanel mainPanel;
    private JComboBox<JulcSDK> localSDKCB;
    private JButton newSDKBtn;
    private JButton sdkDetailBtn;

    private final JulcSDK emptySDK = new JulcSDK();
    private boolean configChanged;

    public JulcProjectConfig(Project project) {
        initComponents();
        initializeData();
        attachHandlers(project);
        setCurrentSelection(project);
        listenSelectionChange();
    }

    private void initComponents() {
        mainPanel = new JPanel(new BorderLayout());
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("julc Configuration"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new java.awt.Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        formPanel.add(new JLabel("julc SDK"), c);

        localSDKCB = new JComboBox<>();
        localSDKCB.setMinimumSize(new Dimension(350, 27));
        localSDKCB.setPreferredSize(new Dimension(350, 27));
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        formPanel.add(localSDKCB, c);

        newSDKBtn = new JButton("New");
        c.gridx = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        formPanel.add(newSDKBtn, c);

        sdkDetailBtn = new JButton("Details");
        c.gridx = 3;
        formPanel.add(sdkDetailBtn, c);

        mainPanel.add(formPanel, BorderLayout.NORTH);
        mainPanel.setMinimumSize(new Dimension(650, 100));
        mainPanel.setPreferredSize(new Dimension(650, 100));
    }

    private void initializeData() {
        populateAvailableSDKs();
    }

    private void setCurrentSelection(Project project) {
        JulcProjectState projectState = JulcProjectState.getInstance(project);
        JulcProjectState.State state = projectState.getState();

        if (JulcProjectState.ConfigType.local_sdk == state.getSdkType()) {
            setSelectedSDK(localSDKCB, state.getSdkId());
        }
    }

    private void setSelectedSDK(JComboBox<JulcSDK> cb, String id) {
        for (int i = 0; i < cb.getItemCount(); i++) {
            JulcSDK sdk = cb.getItemAt(i);
            if (sdk == null) continue;
            if (sdk.getId() != null && sdk.getId().equals(id)) {
                cb.setSelectedIndex(i);
                break;
            }
        }
    }

    private void populateAvailableSDKs() {
        List<JulcSDK> sdks = JulcSDKState.getInstance().getSdks();
        localSDKCB.removeAllItems();
        localSDKCB.addItem(emptySDK);
        if (sdks != null) {
            for (JulcSDK sdk : sdks) {
                localSDKCB.addItem(sdk);
            }
        }
    }

    private void attachHandlers(Project project) {
        newSDKBtn.addActionListener(e -> {
            JulcSDK sdk = JulcConfigurationHelperService.createOrUpdateSDKConfiguration(project, null);
            if (sdk != null) {
                populateAvailableSDKs();
                setSelectedSDK(localSDKCB, sdk.getId());
            }
        });

        sdkDetailBtn.addActionListener(e -> {
            JulcSDK sdk = (JulcSDK) localSDKCB.getSelectedItem();
            if (sdk == null || StringUtil.isEmpty(sdk.getId())) {
                Messages.showWarningDialog("Please select a julc SDK first to see the details", "");
                return;
            }
            JulcSDK updatedSDK = JulcConfigurationHelperService.createOrUpdateSDKConfiguration(project, sdk);
            if (updatedSDK != null) {
                updateSDKInComboBox(localSDKCB, updatedSDK);
            }
        });
    }

    private void listenSelectionChange() {
        localSDKCB.addActionListener(e -> configChanged = true);
    }

    public boolean isConfigChanged() {
        return configChanged;
    }

    private void updateSDKInComboBox(JComboBox<JulcSDK> cb, JulcSDK updatedSDK) {
        for (int i = 0; i < cb.getItemCount(); i++) {
            JulcSDK sdk = cb.getItemAt(i);
            if (sdk == null || StringUtil.isEmpty(sdk.getId())) continue;
            if (sdk.getId().equals(updatedSDK.getId())) {
                sdk.updateValues(updatedSDK);
                break;
            }
        }
    }

    public Tuple<JulcProjectState.ConfigType, String> getSdkId() {
        JulcSDK sdk = (JulcSDK) localSDKCB.getSelectedItem();
        if (sdk != null)
            return new Tuple<>(JulcProjectState.ConfigType.local_sdk, sdk.getId());
        return null;
    }

    public void updateDataToState(JulcProjectState.State state) {
        Tuple<JulcProjectState.ConfigType, String> setting = getSdkId();
        if (setting != null) {
            state.setSdkType(setting._1());
            state.setSdkId(setting._2());
        }
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public ValidationInfo doValidate() {
        return null;
    }
}
