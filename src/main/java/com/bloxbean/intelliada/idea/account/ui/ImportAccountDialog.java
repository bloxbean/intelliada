package com.bloxbean.intelliada.idea.account.ui;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.core.util.Network;
import com.bloxbean.intelliada.idea.core.util.NetworkUtil;
import com.bloxbean.intelliada.idea.core.util.Networks;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.UUID;

public class ImportAccountDialog extends DialogWrapper {
    private JPanel mainPanel;
    private JTextField mnemonicTf;
    private JTextField accountTf;
    private JCheckBox readOnlyAccount;
    private JTextField accountNameTf;
    private JComboBox networkType;

    protected ImportAccountDialog() {
        super(false);
        init();
        setTitle("Import External Account");
        attachListener();

        accountTf.setEditable(false);
    }

    private void attachListener() {

        readOnlyAccount.addActionListener(e -> {
            if(readOnlyAccount.isSelected()) {
                mnemonicTf.setText("");
                mnemonicTf.setEditable(false);
                accountTf.setEditable(true);
            } else {
                accountTf.setEditable(false);
                mnemonicTf.setEditable(true);
            }
        });

        mnemonicTf.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {

            }

            @Override
            public void focusLost(FocusEvent e) {
                String mnemonic = mnemonicTf.getText();
                try {
                    Account account = deriveAccountFromMnemonic();
                    //Account account = new Account(mnemonic);
                    //accountTf.setText(account.getAddress().toString());
                    if(account != null)
                        accountTf.setText(account.baseAddress()); //TODO
                } catch (Exception ex) {
                    accountTf.setText("");
                }
            }
        });

    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if(StringUtil.isEmpty(accountNameTf.getText())) {
            return new ValidationInfo("Account name cannot be empty", accountNameTf);
        }

        if (readOnlyAccount.isSelected()) {
            if (StringUtil.isEmpty(accountTf.getText()))
                return new ValidationInfo("Enter a valid address", accountTf);

//            try {
//                //Address  address = new Address(accountTf.getText());
//            } catch (Exception e) {
//                return new ValidationInfo("Invalid address", accountTf);
//            }

            return null;
        } else {
            if (StringUtil.isEmpty(accountTf.getText()))
                return new ValidationInfo("Enter a valid mnemonic phrase", accountTf);

            if (StringUtil.isEmpty(mnemonicTf.getText()))
                return new ValidationInfo("Enter a valid mnemonic phrase", mnemonicTf);
        }

        return null;
    }

    private Account deriveAccountFromMnemonic() {
        String mnemonic = mnemonicTf.getText().trim();

        Network network = getNetwork();
        com.bloxbean.cardano.client.common.model.Network clNetwork = null;

        if(network != null) {
            clNetwork = NetworkUtil.convertToCLNetwork(network);
        } else {
            clNetwork = com.bloxbean.cardano.client.common.model.Networks.testnet(); //default
        }

        try {
            Account account = new Account(clNetwork, mnemonic);
            return account;
        } catch (Error e) {
            //e.printStackTrace();
            return null;
        }
    }

    public CardanoAccount getAccount() {

        if(readOnlyAccount.isSelected()) {
            CardanoAccount account = new CardanoAccount(StringUtil.trim(accountTf.getText()));
            account.setName(accountNameTf.getText());
            return account;
        } else {
            Account account = deriveAccountFromMnemonic();
            if(account == null)
                return null;

            CardanoAccount cardanoAccount = new CardanoAccount(account.baseAddress(), account.mnemonic());
            cardanoAccount.setName(accountNameTf.getText());
            return cardanoAccount;
        }
    }

    public Network getNetwork() {
        return (Network) networkType.getSelectedItem();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        networkType = new ComboBox();
        networkType.addItem(Networks.testnet());
        networkType.addItem(Networks.mainnet());
    }
}
