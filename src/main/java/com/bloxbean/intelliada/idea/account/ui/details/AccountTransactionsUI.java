package com.bloxbean.intelliada.idea.account.ui.details;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.common.ui.JsonEditorTextField;
import com.bloxbean.intelliada.idea.nodeint.service.CardanoServiceFactory;
import com.bloxbean.intelliada.idea.nodeint.service.api.CardanoAccountService;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListenerAdapter;
import com.bloxbean.intelliada.idea.nodeint.service.api.TransactionInfoService;
import com.bloxbean.intelliada.idea.nodeint.service.impl.TransactionInfoServiceImpl;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.util.JsonUtil;
import com.bloxbean.intelliada.idea.util.MessageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static java.awt.event.MouseEvent.BUTTON1;

public class AccountTransactionsUI {
    private JPanel panel;
    private JList transactionList;
    private JTabbedPane metadataTf;
    private EditorTextField transactionContentTf;
    private EditorTextField metadataEditorTf;
    private Project project;
    private CardanoConsole console;
    private CardanoAccount account;
    private DefaultListModel<String> txnListModel;

    private boolean supported;

    public AccountTransactionsUI(Project project, boolean supported) {
        this.project = project;
        this.supported = supported;
    }

    public void initialize(CardanoAccount account, CardanoConsole console) {
        this.project = project;
        this.console = console;
        this.account = account;

        attachListeners();
        getRecentTransactions();

        if(!supported) {
            transactionContentTf.setText("Transaction details are not supported for the selected node type");
            metadataEditorTf.setText("Transaction metadata are not supported for the selected node type");

            transactionContentTf.setEnabled(false);
            metadataEditorTf.setEnabled(false);
        }
    }

    private void attachListeners() {
        transactionList.setSelectionMode(DefaultListSelectionModel.SINGLE_SELECTION);
        ListSelectionModel listSelectionModel = transactionList.getSelectionModel();
        listSelectionModel.addListSelectionListener(e -> {
            int index = transactionList.getSelectedIndex();
            if (index != -1 && index <= txnListModel.size() - 1) {
                transactionContentTf.setText("");
                metadataEditorTf.setText("");
            }
        });

        attachListDoubleClickListener();
    }

    private void attachListDoubleClickListener() {
        transactionList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent me) {
                if (me.getClickCount() == 2 && me.getButton() == BUTTON1) {     // to detect doble click events
                    int index = transactionList.getSelectedIndex();
                    if (index != -1 && index <= txnListModel.size() - 1) {
                        String txnHash = txnListModel.get(index);
                        if (StringUtil.isEmpty(txnHash)) {
                            return;
                        }
                        getTransactionDetail(txnHash);
                    }
                }
            }
        });

    }

    private void getRecentTransactions() {
        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
                @Override
                public void run() {
                    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
                    progressIndicator.setIndeterminate(false);

                    if (account == null)
                        return;

                    String address = account.getAddress();
                    if (StringUtil.isEmpty(address))
                        return;

                    try {
                        CardanoAccountService accountService = CardanoServiceFactory.getAccountService(project, new LogListenerAdapter(console));
                        List<String> txnList = accountService.getRecentTransactions(address, 100, 1, OrderEnum.desc);
                        txnListModel.addAll(txnList);
                    } catch (Exception e) {
                        console.showErrorMessage("Error getting recent transactions", e);
                        MessageUtil.showMessage("Error getting recent transactions", "Transactions", true);
                    }
                    progressIndicator.setFraction(1.0);
                }
            }, "Getting recent transactions ...", true, project);

        } finally {

        }
    }

    private void getTransactionDetail(String txnHash) {
        if (StringUtil.isEmpty(txnHash)) {
            console.showErrorMessage("Invalid transaction hash");
            return;
        }

        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
                @Override
                public void run() {
                    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
                    progressIndicator.setIndeterminate(false);

                    if (account == null)
                        return;

                    String address = account.getAddress();
                    if (StringUtil.isEmpty(address))
                        return;

                    try {
                        TransactionInfoService transactionInfoService = new TransactionInfoServiceImpl(project, new LogListenerAdapter(console));
                        TransactionContent txnContent = transactionInfoService.getTransactionDetailsByTxnHash(txnHash);
                        if (txnContent == null) {
                            console.showErrorMessage("Error getting transaction details");
                            MessageUtil.showMessage("Error getting transaction details", "Transaction Details", true);
                            return;
                        }

                        ApplicationManager.getApplication().invokeLater(new Runnable() {
                            public void run() {
                                transactionContentTf.setText(JsonUtil.getPrettyJson(txnContent));
                            }
                        });

                    } catch (Exception e) {
                        console.showErrorMessage("Error getting transaction details", e);
                        MessageUtil.showMessage("Error getting transaction details", "Transaction Details", true);
                        return;
                    }

                    progressIndicator.setFraction(0.5);
                    if (progressIndicator.isCanceled())
                        return;

                    progressIndicator.setText("Getting metadata ...");

                    try {
                        TransactionInfoService transactionInfoService = new TransactionInfoServiceImpl(project, new LogListenerAdapter(console));
                        List<MetadataJSONContent> metadata = transactionInfoService.getTransactionMetadata(txnHash);
                        if (metadata == null) {
                            console.showErrorMessage("Error getting transaction metadata");
                            MessageUtil.showMessage("Error getting transaction metadata", "Transaction Details", true);
                            return;
                        }

                        ApplicationManager.getApplication().invokeLater(new Runnable() {
                            public void run() {
                                metadataEditorTf.setText(JsonUtil.getPrettyJson(metadata));
                            }
                        });

                    } catch (Exception e) {
                        console.showErrorMessage("Error getting transaction metadata", e);
                        MessageUtil.showMessage("Error getting transaction metadata", "Transaction Details", true);
                        return;
                    }

                    progressIndicator.setFraction(1.0);
                }
            }, "Getting transaction details ...", true, project);

        } finally {
        }
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        transactionContentTf = new JsonEditorTextField(project);
        metadataEditorTf = new JsonEditorTextField(project);

        txnListModel = new DefaultListModel<>();
        transactionList = new JBList(txnListModel);
    }
}
