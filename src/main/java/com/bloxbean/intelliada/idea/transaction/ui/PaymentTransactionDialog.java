package com.bloxbean.intelliada.idea.transaction.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PaymentTransactionDialog extends DialogWrapper {
    private JPanel mainPanel;
    private TransactionEntryForm txnEntryForm;
    private JTabbedPane tabbedPane1;

    public PaymentTransactionDialog(@Nullable Project project) {
        super(project, true);
        init();
        setTitle("Payment Transaction");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel;
    }
}
