package com.bloxbean.intelliada.idea.account.ui;

import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
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
                    //Account account = new Account(mnemonic);
                    //accountTf.setText(account.getAddress().toString());
                    accountTf.setText(UUID.randomUUID().toString()); //TODO
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

//    private Account deriveAccountFromMnemonic() {
//        String mnemonic = mnemonicTf.getText().trim();
//        try {
//            Account account = new Account(mnemonic);
//            return account;
//        } catch (Exception e) {
//            return null;
//        }
//    }
//
    public CardanoAccount getAccount() {

        if(readOnlyAccount.isSelected()) {
            CardanoAccount account = new CardanoAccount(StringUtil.trim(accountTf.getText()));
            account.setName(accountNameTf.getText());
            return account;
        } else {
//            Account account = deriveAccountFromMnemonic();
//            if(account == null)
//                return null;
//
//            CardanoAccount algoAccount = new AlgoAccount(account.getAddress().toString(), account.toMnemonic());
            CardanoAccount account = new CardanoAccount("dddd");
            account.setName(accountNameTf.getText());
            return account;
        }
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel;
    }
}
