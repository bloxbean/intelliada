package com.bloxbean.intelliada.idea.account.ui.details;

import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AccountDetailsDialog extends DialogWrapper {
    private JPanel mainPanel;
    private AccountTransactionsUI transactionUI;
    private AccountBasicDetailsForm basicDetailsForm;
    private Project project;
    private CardanoConsole console;
    private CardanoAccount account;

    public AccountDetailsDialog(@Nullable Project project, CardanoAccount account) {
        super(project, true);
        this.project = project;
        this.console = CardanoConsole.getConsole(project);
        this.account = account;
        initialize(project, account, console);
        setTitle("Account Details");
        init();
    }

    private void initialize(Project project, CardanoAccount account, CardanoConsole console) {
        basicDetailsForm.initialize(project, account, console);
        transactionUI.initialize(account, console);
    }

    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        return mainPanel;
    }

    public boolean getAccountInfoUpdated() {
        return basicDetailsForm.getAccountInfoUpdate();
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        //Need to initialize here because as non-null project required for JsonEditorTextField
        transactionUI = new AccountTransactionsUI(project);
    }
}
