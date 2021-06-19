package com.bloxbean.intelliada.idea.transaction.action;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.intelliada.idea.metadata.exception.InvalidMetadataException;
import com.bloxbean.intelliada.idea.metadata.ui.MetadataEntryForm;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListenerAdapter;
import com.bloxbean.intelliada.idea.nodeint.service.api.TransactionService;
import com.bloxbean.intelliada.idea.nodeint.service.impl.TransactionServiceImpl;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.transaction.ui.PaymentTransactionDialog;
import com.bloxbean.intelliada.idea.transaction.ui.TransactionDtlEntryForm;
import com.bloxbean.intelliada.idea.transaction.ui.TransactionEntryForm;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.bloxbean.intelliada.idea.utxos.ui.UtxoSelectEntryForm;
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

        TransactionDtlEntryForm transactionDtlEntryForm = dialog.getTransactionDetlEntryForm();
        BigInteger ttl = transactionDtlEntryForm.getTtl();
        List<Account> additionalWitnessAccounts = transactionDtlEntryForm.getAdditionalWitnessAccounts();

        PaymentTransaction paymentTransaction = txnEntryForm.buildTransaction();

        UtxoSelectEntryForm utxoSelectEntryForm = dialog.getUtxoSelectEntryForm();
        List<Utxo> selectedUtxos = utxoSelectEntryForm.getUtxos();
        if(selectedUtxos != null && selectedUtxos.size() > 0) {
            paymentTransaction.setUtxosToInclude(selectedUtxos);
        }
        if(additionalWitnessAccounts != null)
            paymentTransaction.setAdditionalWitnessAccounts(additionalWitnessAccounts);

        TransactionDetailsParams detailsParams = new TransactionDetailsParams();
        if(ttl != null)
            detailsParams.setTtl(ttl.longValue());

        CardanoConsole console = CardanoConsole.getConsole(project);
        LogListenerAdapter logListenerAdapter = new LogListenerAdapter(console);
        console.clearAndshow();

        MetadataEntryForm metadataEntryForm = dialog.getMetadataEntryForm();
        final Metadata metadata;
        try {
            metadata = metadataEntryForm.getMetadata();
        } catch (InvalidMetadataException invalidMetadataException) {
            console.showErrorMessage("Invalid metadata", invalidMetadataException);
            return;
        }

        List<PaymentTransaction> paymentTransactions = Arrays.asList(paymentTransaction);

        Task.Backgroundable task = new Task.Backgroundable(project, "Payment Transaction") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                console.showInfoMessage(String.format("Payment transaction starts ...\n"));

                try {
                    TransactionService transactionService = new TransactionServiceImpl(project, logListenerAdapter);

                    String txnId = transactionService.transfer(paymentTransactions, detailsParams, metadata);
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
