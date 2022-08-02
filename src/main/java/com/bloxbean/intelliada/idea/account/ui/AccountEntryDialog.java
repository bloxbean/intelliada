package com.bloxbean.intelliada.idea.account.ui;

import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.account.service.AccountChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AccountEntryDialog extends DialogWrapper {
    private JPanel mainPanel;
    private JTextField accountTf;
    private JButton accountChooserBtn;
    private JButton multiSigBtn;

    public AccountEntryDialog(Project project, String title) {
        super(project);
        init();
        setTitle(title);

        accountChooserBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                CardanoAccount account = AccountChooser.getSelectedAccount(project, true);
                if (account != null) {
                    accountTf.setText(account.getAddress());
                }
            }
        });

//        multiSigBtn.addActionListener(e -> {
//            AlgoMultisigAccount algoMultisigAccount = AccountChooser.getSelectedMultisigAccount(project, true);
//            if(algoMultisigAccount != null) {
//                accountTf.setText(algoMultisigAccount.getAddress());
//            }
//        });
    }

    @Override
    protected @Nullable
    ValidationInfo doValidate() {
        if (StringUtil.isEmpty(accountTf.getText())) {
            return new ValidationInfo("Please enter a valid account", accountTf);
        } else
            return null;
    }

    public String getAccount() {
        return accountTf.getText();
    }

    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        return mainPanel;
    }
}
