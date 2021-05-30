package com.bloxbean.intelliada.idea.transaction.action;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListenerAdapter;
import com.bloxbean.intelliada.idea.nodeint.service.api.TransactionService;
import com.bloxbean.intelliada.idea.nodeint.service.impl.TransactionServiceImpl;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.transaction.ui.PaymentTransactionDialog;
import com.bloxbean.intelliada.idea.transaction.ui.TransactionDtlEntryForm;
import com.bloxbean.intelliada.idea.transaction.ui.TransactionEntryForm;
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

        if(!ok) {
            IdeaUtil.showNotification(project, "Payment Transaction",
                    "Transaction was cancelled", NotificationType.WARNING, null);
            return;
        }

        TransactionEntryForm txnEntryForm = dialog.getTxnEntryForm();
//        Account sender = txnEntryForm.getSender();
//        String receiver = txnEntryForm.getReceiver();
//        String unit = txnEntryForm.getUnit();
//        BigInteger amount = txnEntryForm.getAmount();
//        BigInteger fee = txnEntryForm.getFee();

        TransactionDtlEntryForm transactionDtlEntryForm = dialog.getTransactionDetlEntryForm();
        BigInteger ttl = transactionDtlEntryForm.getTtl();

        PaymentTransaction paymentTransaction = txnEntryForm.buildTransaction();

        TransactionDetailsParams detailsParams = new TransactionDetailsParams();
        if(ttl != null)
            detailsParams.setTtl(ttl.longValue());

        List<PaymentTransaction> paymentTransactions = Arrays.asList(paymentTransaction);

        CardanoConsole console = CardanoConsole.getConsole(project);
        LogListenerAdapter logListenerAdapter = new LogListenerAdapter(console);
        console.clearAndshow();

        Task.Backgroundable task = new Task.Backgroundable(project, "Payment Transaction") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                console.showInfoMessage(String.format("Payment transaction starts ...\n"));

                try {
                    TransactionService transactionService = new TransactionServiceImpl(project, logListenerAdapter);

                    String txnId = transactionService.transfer(paymentTransactions, detailsParams, null);
                    console.showSuccessMessage("Transaction executed successfully with id : " + txnId);
                    IdeaUtil.showNotification(project, getTitle(),
                            String.format("%s was successful", getTxnCommand()), NotificationType.INFORMATION, null);
                }catch (Exception exception) {
                    console.showErrorMessage(String.format("%s failed", getTxnCommand()), exception);
                }
            }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }

    private String getTxnCommand() {
        return "Payment Transaction";
    }
}
