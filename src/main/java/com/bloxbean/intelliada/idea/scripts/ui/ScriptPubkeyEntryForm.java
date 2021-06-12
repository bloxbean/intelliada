package com.bloxbean.intelliada.idea.scripts.ui;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.account.service.AccountChooser;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NetworkUtil;
import com.bloxbean.intelliada.idea.nodeint.CardanoNodeConfigurationHelper;
import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.bloxbean.intelliada.idea.scripts.service.ScriptService;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class ScriptPubkeyEntryForm {
    private JPanel panel1;
    private JTextField addressTf;
    private JButton accountChooserBtn;
    private JRadioButton addressRB;
    private JRadioButton vkeyRB;
    private JRadioButton generateNewRB;
    private JTextField nameTf;
    private JTextField vkeyTf;
    private JPasswordField mnemonicTf;
    private ButtonGroup buttonGroup;
    private CardanoConsole console;
    private Project project;
    private boolean isMainnet;
    private ScriptGenListener scriptGenListener;
    private ScriptInfo scriptInfo; //This is used only when generateNew is selected to avoid re-generation

    public ScriptPubkeyEntryForm() {
        //Create button group
        buttonGroup = new ButtonGroup();
        buttonGroup.add(generateNewRB);
        buttonGroup.add(addressRB);
        buttonGroup.add(vkeyRB);
        generateNewRB.setSelected(true);
        generateFromTypesSelected();
    }

    public void initializeData(Project project, CardanoConsole console) {
        this.console = console;
        RemoteNode node = CardanoNodeConfigurationHelper.getTargetRemoteNode(project);
        if (node != null)
            isMainnet = NetworkUtil.isMainnet(node);

        generateNewRB.addActionListener(e -> {
            generateFromTypesSelected();
        });
        addressRB.addActionListener(e -> {
            generateFromTypesSelected();
        });
        vkeyRB.addActionListener(e -> {
            generateFromTypesSelected();
        });

        accountChooserBtn.addActionListener(e -> {
            CardanoAccount cardanoAccount = AccountChooser.getSelectedAccount(project, true);
            if (cardanoAccount != null) {
                setAddresss(cardanoAccount.getAddress());
                mnemonicTf.setText(cardanoAccount.getMnemonic());
            }
        });

        mnemonicTf.addFocusListener(new FocusListener() {
            String oldMnemonic;

            @Override
            public void focusGained(FocusEvent e) {
                oldMnemonic = mnemonicTf.getText();
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (oldMnemonic != null && oldMnemonic.equals(mnemonicTf.getText())) {
                    oldMnemonic = null;
                    return;
                }
                oldMnemonic = null; //reset old mnemonic

                String mnemonic = String.valueOf(mnemonicTf.getPassword());
                try {
                    RemoteNode node = CardanoNodeConfigurationHelper.getTargetRemoteNode(project);
                    if (node != null) {
                        if (NetworkUtil.isMainnet(node)) {
                            Account account = new Account(mnemonic);
                            setAddresss(account.baseAddress());
                        } else {
                            Account account = new Account(Networks.testnet(), mnemonic);
                            setAddresss(account.baseAddress());
                        }
                    } else {
                        console.showErrorMessage("Target Cardano node is not configured. Please select a default node first.");
                    }
                } catch (Exception ex) {
                    addressTf.setText("");
                }
            }
        });
    }

    public boolean isGenerateNew() {
        return generateNewRB.isSelected();
    }

    public boolean isGenerateFromAddress() {
        return addressRB.isSelected();
    }

    public boolean isGenerateFromVkey() {
        return vkeyRB.isSelected();
    }

    public Account getAccount() {
        String senderMnenomic = new String(mnemonicTf.getPassword());
        if(StringUtil.isEmpty(senderMnenomic))
            return null;

        try {
            Account senderAcc = null;
            if(isMainnet) {
                senderAcc = new Account(senderMnenomic);
                String baseAddress = senderAcc.baseAddress(); //Check if baseAddress can be derived
                if(StringUtil.isEmpty(baseAddress))
                    return null;
            } else {
                senderAcc = new Account(Networks.testnet(), senderMnenomic);
                String baseAddress = senderAcc.baseAddress(); //Check if baseAddress can be derived
                if(StringUtil.isEmpty(baseAddress))
                    return null;
            }
            return senderAcc;
        } catch (Exception e) {
            console.showErrorMessage(e.getMessage(), e);
            return null;
        }
    }

    public String getVkey() {
        return vkeyTf.getText();
    }

    public ScriptInfo generateScriptPubkey() {
        try {
            ScriptService scriptGeneratorService = new ScriptService();
            String name = nameTf.getText();
            if (isGenerateFromAddress()) {
                return scriptGeneratorService.generateScriptPubkeyFromAddress(name, getAccount());
            } else if(isGenerateNew()) {
                //If Generate New. Then don't create if scriptInfo is already generated
                if(scriptInfo != null)
                    return scriptInfo;
                return scriptGeneratorService.generateNewScriptPubkey(name);
            } else if(isGenerateFromVkey()) {
                return scriptGeneratorService.generateScriptPubkeyFromVkey(name, getVkey());
            } else {
                return null;
            }
        } catch (Exception e) {
            console.showErrorMessage("Script generation error", e);
            return null;
        }
    }

    public void addScriptGenListener(ScriptGenListener scriptGenListener) {
        this.scriptGenListener = scriptGenListener;
    }

    private void generateFromTypesSelected() {
        if(generateNewRB.isSelected()) {
            clearAllInput();
            addressTf.setEnabled(false);
            mnemonicTf.setEnabled(false);
            accountChooserBtn.setEnabled(false);
            vkeyTf.setEnabled(false);
        } else if(addressRB.isSelected()) {
            clearAllInput();
            addressTf.setEnabled(true);
            mnemonicTf.setEnabled(true);
            accountChooserBtn.setEnabled(true);
            vkeyTf.setEnabled(false);
        } else if(vkeyRB.isSelected()) {
            clearAllInput();
            addressTf.setEnabled(false);
            mnemonicTf.setEnabled(false);
            accountChooserBtn.setEnabled(false);
            vkeyTf.setEnabled(true);
        }

        if(scriptGenListener != null)
            scriptGenListener.generateFromTypeChanged();
    }

    private void setAddresss(String address) {
        addressTf.setText(address);
    }

    private void clearAllInput() {
        if(!addressRB.isSelected()) {
            addressTf.setText("");
            mnemonicTf.setText("");
        }
        if(!vkeyRB.isSelected()) {
            vkeyTf.setText("");
        }

        invalidateScriptInfo();
    }

    private void invalidateScriptInfo() {
        scriptInfo = null;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }

    interface ScriptGenListener {
        public void generateFromTypeChanged();
    }
}
