package com.bloxbean.intelliada.idea.transaction.ui;

import com.bloxbean.intelliada.idea.nodeint.service.api.LogListenerAdapter;
import com.bloxbean.intelliada.idea.nodeint.service.api.NetworkInfoService;
import com.bloxbean.intelliada.idea.nodeint.service.impl.NetworkServiceImpl;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.math.BigInteger;

public class TransactionDtlEntryForm {
    private JPanel mainPanel;
    private JTextField ttlTf;
    private JButton fetchDefaultsButton;
    private Project project;

    public TransactionDtlEntryForm() {

    }

    public void initializeData(Project project) {
        this.project = project;
        fetchDefaultsButton.addActionListener(e -> {
            fetchTtl(this.project);
        });
    }

    private void fetchTtl(Project project) {
            CardanoConsole console = CardanoConsole.getConsole(project);
            console.clearAndshow();
            try {
                ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
                    @Override
                    public void run() {
                        ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();

                        try {
                            NetworkInfoService networkInfoService = new NetworkServiceImpl(project, new LogListenerAdapter(console));
                            Long ttl = networkInfoService.getTtl();
                            if(ttl != null)
                                ttlTf.setText(String.valueOf(ttl));
                        } catch (Exception e) {
                            e.getMessage();
                            console.showErrorMessage("Error getting ttl info", e);
                        }
                        progressIndicator.setFraction(1.0);
                    }
                }, "Calculating ttl ...", true, project);

            } finally {

            }

    }

    public BigInteger getTtl() {
        if(StringUtil.isEmpty(ttlTf.getText()))
            return null;

        try {
            return new BigInteger(StringUtil.trim(ttlTf.getText()));
        } catch (Exception e) {
            return null;
        }
    }

    public @Nullable ValidationInfo doValidate() {
        return null;
    }
}
