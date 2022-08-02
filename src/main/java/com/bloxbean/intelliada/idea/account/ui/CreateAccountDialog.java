package com.bloxbean.intelliada.idea.account.ui;

import com.bloxbean.intelliada.idea.core.util.Network;
import com.bloxbean.intelliada.idea.core.util.Networks;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CreateAccountDialog extends DialogWrapper {
    private JPanel mainPanel;
    private JTextField accountNameTf;
    private JComboBox accountType;

    public CreateAccountDialog() {
        super(false);
        init();
        setTitle("Create Account");
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (StringUtil.isEmpty(getAccountName())) {
            return new ValidationInfo("Please provide a valid account name", accountNameTf);
        }
        return null;
    }

    public String getAccountName() {
        return accountNameTf.getText();
    }

    public Network getNetwork() {
        return (Network) accountType.getSelectedItem();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        accountType = new ComboBox();
        accountType.addItem(Networks.testnet());
        accountType.addItem(Networks.mainnet());
    }

}
