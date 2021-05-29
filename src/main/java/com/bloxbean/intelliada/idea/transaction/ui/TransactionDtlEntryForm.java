package com.bloxbean.intelliada.idea.transaction.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.math.BigInteger;

public class TransactionDtlEntryForm {
    private JPanel mainPanel;
    private JTextField feeTf;
    private JButton fetchDefaultsButton;

    public TransactionDtlEntryForm() {

    }

    public void initializeData(Project project) {

    }

    public BigInteger getFee() {
        if(StringUtil.isEmpty(feeTf.getText()))
            return null;

        try {
            return new BigInteger(StringUtil.trim(feeTf.getText()));
        } catch (Exception e) {
            return null;
        }
    }
}
