package com.bloxbean.intelliada.idea.scripts.ui;

import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptType;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NetworkUtil;
import com.bloxbean.intelliada.idea.nodeint.CardanoNodeConfigurationHelper;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListenerAdapter;
import com.bloxbean.intelliada.idea.nodeint.service.api.NetworkInfoService;
import com.bloxbean.intelliada.idea.nodeint.service.impl.NetworkServiceImpl;
import com.bloxbean.intelliada.idea.scripts.service.ScriptChooser;
import com.bloxbean.intelliada.idea.scripts.service.ScriptGenInputs;
import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.bloxbean.intelliada.idea.scripts.service.ScriptService;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.util.MessageUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class CompositeScriptEntryForm {
    private JPanel panel1;
    private JComboBox scriptTypeCB;
    private JTextField slotNoTf;
    private JList scriptList;
    private JButton removeScriptButton;
    private JButton addScriptButton;
    private JTextField nameTf;
    private JTextField requiredTf;
    private JButton currentSlotButton;
    private JComboBox timeLockingTypeCB;
    private CardanoConsole console;
    private Project project;
    private boolean isMainnet;
    private ScriptService scriptService;

    private DefaultListModel<ScriptInfo> scriptModel;

    public CompositeScriptEntryForm() {
        scriptService = new ScriptService();
        scriptModel = new DefaultListModel<>();
        scriptList.setModel(scriptModel);
        scriptList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    public void initializeData(Project project, CardanoConsole console) {
        this.project = project;
        this.console = console;

        RemoteNode node = CardanoNodeConfigurationHelper.getTargetRemoteNode(project);
        if (node != null)
            isMainnet = NetworkUtil.isMainnet(node);

        scriptTypeCB.addItem(null);
        scriptTypeCB.addItem(ScriptType.all);
        scriptTypeCB.addItem(ScriptType.any);
        scriptTypeCB.addItem(ScriptType.atLeast);
//        scriptTypeCB.addItem(ScriptType.before);
//        scriptTypeCB.addItem(ScriptType.after);

        timeLockingTypeCB.addItem(null);
        timeLockingTypeCB.addItem(ScriptType.before);
        timeLockingTypeCB.addItem(ScriptType.after);

        scriptTypeCB.setSelectedItem(ScriptType.all);
        resetTypeSpecificFields();

        attachListeners();
    }

    private void attachListeners() {
        addScriptButton.addActionListener(e -> {
            ScriptInfo scriptInfo = ScriptChooser.selectScript(project);
            if(scriptInfo == null)
                return;
            if(!scriptModel.contains(scriptInfo))
                scriptModel.addElement(scriptInfo);
        });

        removeScriptButton.addActionListener(e -> {
            int index = scriptList.getSelectedIndex();
            if(index == -1)
                return;
            scriptModel.remove(index);
        });

        currentSlotButton.addActionListener(e -> {
            fetchCurrentSlot(project);
        });

        scriptTypeCB.addActionListener(e -> {
            ScriptType type = getType();
            if(type == null) return;

            if(ScriptType.all == type || ScriptType.any == type) {
                resetTypeSpecificFields();
                enableTimeLockFieldsIfRequired();

            } else  if(ScriptType.atLeast == type) {
                resetTypeSpecificFields();
                requiredTf.setEnabled(true);
                enableTimeLockFieldsIfRequired();
            }
//            else if(ScriptType.after == type || ScriptType.before == type) {
//                resetTypeSpecificFields();
//                slotNoTf.setEnabled(true);
//                currentSlotButton.setEnabled(true);
//            }
        });

        timeLockingTypeCB.addActionListener(e -> {
            ScriptType type = getTimeLockType();
            if(type == null) {
                slotNoTf.setText("");
                slotNoTf.setEnabled(false);
                currentSlotButton.setEnabled(false);
                return;
            }

            if(ScriptType.after == type || ScriptType.before == type) {
//                resetTypeSpecificFields();
                enableTimeLockFieldsIfRequired();
            }
        });
    }

    private void enableTimeLockFieldsIfRequired() {
        if(getTimeLockType() != null) {
            slotNoTf.setEnabled(true);
            currentSlotButton.setEnabled(true);
        }
    }

    private void resetTypeSpecificFields() {
        slotNoTf.setText("");
        requiredTf.setText("");
        slotNoTf.setEnabled(false);
        requiredTf.setEnabled(false);
        currentSlotButton.setEnabled(false);
    }

    private String getName() {
        return nameTf.getText();
    }

    private ScriptType getType() {
        return (ScriptType)scriptTypeCB.getSelectedItem();
    }

    private ScriptType getTimeLockType() {
        return (ScriptType)timeLockingTypeCB.getSelectedItem();
    }

    private Long getSlot() {
        try {
            return Long.parseLong(slotNoTf.getText());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getRequired() {
        try {
            return Integer.parseInt(requiredTf.getText());
        } catch (Exception e) {
            return null;
        }
    }

    private List<NativeScript> getNativeScripts() {
        Enumeration<ScriptInfo> enumeration = scriptModel.elements();
        if(enumeration == null)
            return Collections.emptyList();

        List<NativeScript> scripts = new ArrayList<>();
        while(enumeration.hasMoreElements()) {
            ScriptInfo scriptInfo = enumeration.nextElement();
            if(scriptInfo != null && scriptInfo.getScript() != null)
                scripts.add(scriptInfo.getScript());
            else
                continue;;
        }

        return scripts;
    }

    public ValidationInfo doValidation() {
        if(StringUtil.isEmpty(getName())) {
            return new ValidationInfo("Please provide a valid name", nameTf);
        }
        if(getType() == ScriptType.atLeast && getRequired() == null) {
            return new ValidationInfo("Please provide a valid integer value for min required", requiredTf);
        }
        if((getTimeLockType() == ScriptType.before || getTimeLockType() == ScriptType.after) && getSlot() == null) {
            return new ValidationInfo("Invalid slot number", slotNoTf);
        }

        return null;
    }

    public ScriptInfo generateCompositeScript() {
        try {
            ScriptGenInputs scriptGenInputs = ScriptGenInputs
                    .builder()
                    .slot(getSlot())
                    .required(getRequired())
                    .build();

            ScriptInfo scriptInfo =
                    scriptService.generateCompositeScript(getName(), getType(), getTimeLockType(), getNativeScripts(), scriptGenInputs);
            return scriptInfo;
        } catch (Exception e) {
            console.showErrorMessage("Script generation error", e);
            return null;
        }
    }

    private void fetchCurrentSlot(Project project) {
        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
                @Override
                public void run() {
                    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
                    if(console != null)
                        console.clearAndshow();

                    try {
                        NetworkInfoService networkInfoService = new NetworkServiceImpl(project, new LogListenerAdapter(console));
                        Long currentSlot = networkInfoService.getCurrentSlot();
                        if(currentSlot != null)
                            slotNoTf.setText(String.valueOf(currentSlot));
                    } catch (Exception e) {
                        console.showErrorMessage("Error getting current slot", e);
                        MessageUtil.showMessage("Unable to fetch current slot. Check the console for detailed log. \nReason: " + e.getMessage(),
                                "Get current slot", true);
                    }
                    progressIndicator.setFraction(1.0);
                }
            }, "Getting current slot ...", true, project);

        } finally {

        }

    }
}
