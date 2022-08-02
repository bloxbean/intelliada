package com.bloxbean.intelliada.idea.nativetoken.ui;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.account.service.AccountChooser;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.Network;
import com.bloxbean.intelliada.idea.core.util.NetworkUtil;
import com.bloxbean.intelliada.idea.core.util.Networks;
import com.bloxbean.intelliada.idea.nodeint.CardanoNodeConfigurationHelper;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.transaction.TransactionEntryListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class TokenMintAddressEntryForm {
    private JPanel panel1;
    private JTextField creatorTf;
    private JPasswordField mnemonicTf;
    private JButton creatorAddressBtn;
    private JTextField receiverTf;
    private JButton receiverAddressBtn;
    private CardanoConsole console;
    private boolean isMainnet = false;
    private Network network;
    private TransactionEntryListener transactionEntryListener;

    public TokenMintAddressEntryForm() {

    }

    public void initialize(Project project, CardanoConsole console) {
        this.console = console;
        RemoteNode node = CardanoNodeConfigurationHelper.getTargetRemoteNode(project);
        if (node != null)
            isMainnet = NetworkUtil.isMainnet(node);

        if (isMainnet)
            network = Networks.mainnet();
        else
            network = Networks.testnet(); //all networks except mainnet are testnets

        creatorAddressBtn.addActionListener(e -> {
            CardanoAccount cardanoAccount = AccountChooser.getSelectedAccountForNetwork(project, network, true);
            if (cardanoAccount != null) {
                setAddresss(cardanoAccount.getAddress());
                mnemonicTf.setText(cardanoAccount.getMnemonic());
            }
        });

        mnemonicTf.addFocusListener(new FocusListener() {
            String oldMnemonic;

            @Override
            public void focusGained(FocusEvent e) {
                oldMnemonic = String.valueOf(mnemonicTf.getPassword());
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (oldMnemonic != null && oldMnemonic.equals(String.valueOf(mnemonicTf.getPassword()))) {
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
                            Account account = new Account(com.bloxbean.cardano.client.common.model.Networks.testnet(), mnemonic);
                            setAddresss(account.baseAddress());
                        }
                    } else {
                        console.showErrorMessage("Target Cardano node is not configured. Please select a default node first.");
                    }
                } catch (Exception ex) {
                    setAddresss("");
                }
            }
        });

        receiverAddressBtn.addActionListener(e -> {
            CardanoAccount cardanoAccount = AccountChooser.getSelectedAccountForNetwork(project, network, true);
            if (cardanoAccount != null) {
                setReceiver(cardanoAccount.getAddress());
            }
        });
    }

    private void setAddresss(String address) {
        creatorTf.setText(address);
        transactionEntryListener.senderAddressChanged(address);

        setReceiver(address);
    }

    public Account getCreatorAccount() {
        String creatorMnemonic = new String(mnemonicTf.getPassword());
        if (StringUtil.isEmpty(creatorMnemonic))
            return null;

        try {
            Account creatorAccount = null;
            if (isMainnet) {
                creatorAccount = new Account(creatorMnemonic);
                String baseAddress = creatorAccount.baseAddress(); //Check if baseAddress can be derived
                if (StringUtil.isEmpty(baseAddress))
                    return null;
            } else {
                creatorAccount = new Account(com.bloxbean.cardano.client.common.model.Networks.testnet(), creatorMnemonic);
                String baseAddress = creatorAccount.baseAddress(); //Check if baseAddress can be derived
                if (StringUtil.isEmpty(baseAddress))
                    return null;
            }
            return creatorAccount;
        } catch (Exception e) {
            console.showErrorMessage(e.getMessage(), e);
            return null;
        }
    }

    public void addTransactionEntryListener(TransactionEntryListener transactionEntryListener) {
        this.transactionEntryListener = transactionEntryListener;
    }

    public ValidationInfo doValidate() {
        if (getCreatorAccount() == null) {
            return new ValidationInfo("Please select a valid creator account or enter a valid mnemonic phrase", creatorTf);
        }

        if (StringUtil.isEmpty(getReceiver())) {
            return new ValidationInfo("Please enter a valid receiver address", receiverTf);
        }

        return null;
    }

    private void setReceiver(String address) {
        receiverTf.setText(address);
    }

    public String getReceiver() {
        return receiverTf.getText();
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }
}
