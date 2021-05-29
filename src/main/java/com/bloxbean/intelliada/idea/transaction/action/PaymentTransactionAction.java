package com.bloxbean.intelliada.idea.transaction.action;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.configuration.service.RemoteNodeState;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListenerAdapter;
import com.bloxbean.intelliada.idea.nodeint.service.api.NetworkInfoService;
import com.bloxbean.intelliada.idea.nodeint.service.api.TransactionService;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.transaction.ui.PaymentTransactionDialog;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

public class PaymentTransactionAction extends AnAction {

    public PaymentTransactionAction() {
        super(AllIcons.Actions.Execute);
    }

    @Override
    public boolean isDumbAware() {
        return true;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PaymentTransactionDialog dialog = new PaymentTransactionDialog(project);
        boolean ok = dialog.showAndGet();

//        dialog.get
//
//        PaymentTransaction transaction = PaymentTransaction.builder()
//                .sender(new Account(Networks.testnet(), senderMnemonic))
//                .receiver(reciverAddress)
//                .amount(new BigInteger("50000000")).build();

//        List<PaymentTransaction> paymentTransactions = Arrays.asList(transaction);


        RemoteNode node = RemoteNodeState.getInstance().getDefaultRemoteNode();
        if(node == null) {
            IdeaUtil.showNotification(project, "Node Configuration", "No default node selected", NotificationType.ERROR, null);
            return;
        }

        CardanoConsole console = CardanoConsole.getConsole(project);
        LogListenerAdapter logListenerAdapter = new LogListenerAdapter(console);

        Task.Backgroundable task = new Task.Backgroundable(project, "Payment Transaction") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                console.showInfoMessage(String.format("Payment transaction starts ...\n"));

//                try {
//                    TransactionService transactionService = new BFTransactionServiceImpl(node, logListenerAdapter);
//
//                    Result result = transactionService.transfer(paymentTransactions, new TransactionDetailsParams());
//                    if(result.isSuccessful()) {
//                        console.showInfoMessage(result.getResponse());
////                        IdeaUtil.showNotification(project, getTitle(), String.format("%s was successful", getTxnCommand()), NotificationType.INFORMATION, null);
//                    } else {
//                        console.showErrorMessage(String.format("%s failed", getTxnCommand()));
//                        IdeaUtil.showNotification(project, getTitle(), String.format("%s failed", getTxnCommand()), NotificationType.ERROR, null);
//
//                    }
//                } catch (Exception exception) {
//                    console.showErrorMessage("Error getting network info", exception);
//                }
            }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }

    private String getTxnCommand() {
        return "Payment Transaction";
    }
}
