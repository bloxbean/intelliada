package com.bloxbean.intelliada.idea.account.ui.details;

import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.account.service.AccountService;
import com.bloxbean.intelliada.idea.nodeint.service.api.CardanoAccountService;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListenerAdapter;
import com.bloxbean.intelliada.idea.nodeint.service.api.model.AssetBalance;
import com.bloxbean.intelliada.idea.nodeint.service.impl.AccountServiceImpl;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.utxos.service.UtxoChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.Collections;
import java.util.List;

public class AccountBasicDetailsForm {
    private JTextField addressTf;
    private JComboBox assetsCB;
    private JPanel mainPanel;
    private JTextField nameTf;
    private JButton accountNameUpdateBtn;
    private JButton showUtxosBtn;
    private DefaultComboBoxModel<AssetBalance>  assetComboBoxModel;
    private Project project;
    private CardanoConsole console;
    private CardanoAccount account;
    private AccountService accountService;
    private boolean infoUpdate;

    public void initialize(Project project, CardanoAccount account, CardanoConsole console) {
        this.project = project;
        this.console = console;
        this.account = account;
        if(account != null) {
            nameTf.setText(account.getName());
            addressTf.setText(account.getAddress());
        }

        this.accountService = AccountService.getAccountService();

        attachListeners();
        fetchBalance();
    }

    private void attachListeners() {
        nameTf.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                accountNameUpdateBtn.setEnabled(true);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                accountNameUpdateBtn.setEnabled(true);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                accountNameUpdateBtn.setEnabled(true);
            }
        });

        accountNameUpdateBtn.addActionListener(e -> {

            ApplicationManager.getApplication().runWriteAction(() -> {
                if (this.accountService == null)
                    return;

                String accountName = nameTf.getText();
                if (!StringUtil.isEmpty(accountName)) {
                    boolean status = accountService.updateAccountName(account.getAddress().toString(), accountName);
                    accountNameUpdateBtn.setEnabled(false);
                    infoUpdate = true;
                    if (status) {
                        ApplicationManager.getApplication().invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                Messages.showInfoMessage(mainPanel, "Account name updated successfully", "Account Name Update");
                            }
                        }, ModalityState.any());
                    }
                }
            });
        });

        showUtxosBtn.addActionListener(e -> {
            String address = getAddresss();
            if(!StringUtil.isEmpty(address)) {
                UtxoChooser.selectUtxos(project, address, Collections.EMPTY_LIST);
            }
        });
    }

    private String getAddresss() {
        return addressTf.getText();
    }

    private void fetchBalance() {
        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
                @Override
                public void run() {
                    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
                    progressIndicator.setIndeterminate(false);

                    String address = addressTf.getText();
                    if(StringUtil.isEmpty(address))
                        return;

                    try {
                        CardanoAccountService accountService = new AccountServiceImpl(project, new LogListenerAdapter(console));
                        List<AssetBalance> assetBalanceList = accountService.getBalance(address);
                        assetComboBoxModel.addAll(assetBalanceList);
                        if(assetBalanceList.size() > 0)
                            assetsCB.setSelectedIndex(0);
                    } catch (Exception e) {
                        console.showErrorMessage("Error getting available assets", e);
                    }
                    progressIndicator.setFraction(1.0);
                }
            }, "Getting available assets ...", true, project);

        } finally {

        }
    }

    public boolean getAccountInfoUpdate() {
        return infoUpdate;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        assetComboBoxModel = new DefaultComboBoxModel<>();
        assetsCB = new ComboBox(assetComboBoxModel);
    }
}
