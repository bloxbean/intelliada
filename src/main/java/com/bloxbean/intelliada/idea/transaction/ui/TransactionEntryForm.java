package com.bloxbean.intelliada.idea.transaction.ui;

import com.intellij.openapi.project.Project;

import javax.swing.*;

public class TransactionEntryForm {
    private JPanel mainPanel;
    private JTextField senderTf;
    private JButton senderAccChooserBtn;
    private JTextField receiverTf;
    private JButton receiverAccChooserBtn;
    private JPasswordField senderMnemonicTf;
    private JTextField amountTf;
    private JComboBox availableBalanceCB;
    private JTextField feeTf;
    private JCheckBox feeCalcCB;

    public TransactionEntryForm() {

    }

    public void initializeData(Project project) {

    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }
}
