package com.bloxbean.intelliada.idea.transaction.ui;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.account.service.AccountChooser;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.NetworkUtil;
import com.bloxbean.intelliada.idea.nodeint.CardanoNodeConfigurationHelper;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class TransactionDtlEntryForm {
    private JPanel mainPanel;
    private JTextField ttlTf;
    private JButton fetchDefaultsButton;
    private JList addlWitnessAccountList;
    private JButton addAccBtn;
    private JButton removeAccBtn;
    private CardanoConsole console;
    private Project project;
    private DefaultListModel<Account> addlAccountListModel;
    private boolean isMainnet;

    public TransactionDtlEntryForm() {

    }

    public void initializeData(Project project) {
        this.project = project;
        this.console = CardanoConsole.getConsole(project);

        this.addlAccountListModel = new DefaultListModel<>();
        addlWitnessAccountList.setModel(addlAccountListModel);
        this.addlWitnessAccountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        RemoteNode node = CardanoNodeConfigurationHelper.getTargetRemoteNode(project);
        if(node != null)
            isMainnet = NetworkUtil.isMainnet(node);

        fetchDefaultsButton.addActionListener(e -> {
            fetchTtl(this.project);
        });

        addAccBtn.addActionListener(e -> {
            CardanoAccount cardanoAccount = AccountChooser.getSelectedAccount(project, true);
            if(cardanoAccount != null) {
                Account account = deriveAccount(cardanoAccount);
                if(account != null) {
                    addlAccountListModel.addElement(account);
                }
            }
        });

        removeAccBtn.addActionListener(e -> {
            int index = addlWitnessAccountList.getSelectedIndex();
            if(index == -1)
                return;

            addlAccountListModel.remove(index);
        });
    }

    private void fetchTtl(Project project) {
            if(console != null)
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
                            console.showErrorMessage("Error getting ttl info", e);
                        }
                        progressIndicator.setFraction(1.0);
                    }
                }, "Calculating ttl ...", true, project);

            } finally {

            }

    }

    private Account deriveAccount(CardanoAccount cardanoAccount) {
        if(cardanoAccount == null)
            return null;

        String senderMnenomic = cardanoAccount.getMnemonic();
        if(StringUtil.isEmpty(senderMnenomic))
            return null;

        try {
            Account senderAcc = null;
            if(isMainnet) {
                senderAcc = new Account(senderMnenomic);
                String baseAddress = senderAcc.baseAddress(); //Check if baseAddress can be derived
                if(StringUtil.isEmpty(baseAddress))
                    return null;
            } else {
                senderAcc = new Account(Networks.testnet(), senderMnenomic);
                String baseAddress = senderAcc.baseAddress(); //Check if baseAddress can be derived
                if(StringUtil.isEmpty(baseAddress))
                    return null;
            }
            return senderAcc;
        } catch (Exception e) {
            console = CardanoConsole.getConsole(project);
            return null;
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

    public List<Account> getAdditionalWitnessAccounts() {
        Enumeration<Account> accountsEnum = addlAccountListModel.elements();
        if(accountsEnum == null)
            return Collections.EMPTY_LIST;

        List<Account> accounts = new ArrayList<>();
        while(accountsEnum.hasMoreElements())  {
            accounts.add(accountsEnum.nextElement());
        }

        return accounts;
    }

    public @Nullable ValidationInfo doValidate() {
        return null;
    }
}
