package com.bloxbean.intelliada.idea.nativetoken.action;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.intelliada.idea.common.CardanoIcons;
import com.bloxbean.intelliada.idea.core.RequestMode;
import com.bloxbean.intelliada.idea.metadata.exception.InvalidMetadataException;
import com.bloxbean.intelliada.idea.metadata.ui.MetadataEntryForm;
import com.bloxbean.intelliada.idea.nativetoken.ui.TokenMintingDialog;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListenerAdapter;
import com.bloxbean.intelliada.idea.nodeint.service.api.TransactionService;
import com.bloxbean.intelliada.idea.nodeint.service.impl.TransactionServiceImpl;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.core.action.BaseTxnAction;
import com.bloxbean.intelliada.idea.transaction.model.SerializedTransaction;
import com.bloxbean.intelliada.idea.transaction.ui.TransactionDtlEntryForm;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.bloxbean.intelliada.idea.util.JsonUtil;
import com.bloxbean.intelliada.idea.utxos.ui.UtxoSelectEntryForm;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.List;

public class TokenMintingTransactionAction extends BaseTxnAction {

    public TokenMintingTransactionAction() {
        super("Mint Token", "Mint Token", CardanoIcons.MINT_ICON);
    }

    @Override
    public boolean isDumbAware() {
        return true;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        TokenMintingDialog dialog = new TokenMintingDialog(project);
        boolean ok = dialog.showAndGet();

        if (!ok) {
            IdeaUtil.showNotification(project, "Payment Transaction",
                    "Transaction was cancelled", NotificationType.WARNING, null);
            return;
        }

        TransactionDtlEntryForm transactionDtlEntryForm = dialog.getTransactionDtlEntryForm();
        BigInteger ttl = transactionDtlEntryForm.getTtl();
        List<Account> additionalWitnessAccounts = transactionDtlEntryForm.getAdditionalWitnessAccounts();

        MintTransaction mintTransaction = dialog.getMintTransactionRequest();
        //TODO Utxo selection.
        UtxoSelectEntryForm utxoSelectEntryForm = dialog.getUtxoSelectEntryForm();
        List<Utxo> selectedUtxos = utxoSelectEntryForm.getUtxos();
        if (selectedUtxos != null && selectedUtxos.size() > 0) {
            mintTransaction.setUtxosToInclude(selectedUtxos);
        }
        if (additionalWitnessAccounts != null)
            mintTransaction.setAdditionalWitnessAccounts(additionalWitnessAccounts);

        TransactionDetailsParams detailsParams = new TransactionDetailsParams();
        if (ttl != null)
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

        RequestMode requestMode = dialog.getRequestMode();
        if (requestMode == null || requestMode.equals(RequestMode.TRANSACTION)) {
            executeMintToken(project, mintTransaction, detailsParams, console, logListenerAdapter, metadata);
        } else if (requestMode.equals(RequestMode.EXPORT_SIGNED)) {
            String exportFile = getExportFileLocation(project, dialog.getContentPanel());
            exportMintTokenTransaction(project, mintTransaction, detailsParams, console, logListenerAdapter, metadata, exportFile);
        }
    }

    private void executeMintToken(Project project, MintTransaction mintTransaction, TransactionDetailsParams detailsParams, CardanoConsole console, LogListenerAdapter logListenerAdapter, Metadata metadata) {
        Task.Backgroundable task = new Task.Backgroundable(project, "Mint Token Transaction") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                console.showInfoMessage(String.format("Token Minting starts ...\n"));

                try {
                    TransactionService transactionService = new TransactionServiceImpl(project, logListenerAdapter);

                    String txnId = transactionService.mintToken(mintTransaction, detailsParams, metadata);
                    console.showSuccessMessage("Transaction executed successfully with id : " + txnId);
                    IdeaUtil.showNotification(project, getTitle(),
                            String.format("%s was successful", getTxnCommand()), NotificationType.INFORMATION, null);
                } catch (Exception exception) {
                    console.showErrorMessage(String.format("%s failed", getTxnCommand()), exception);
                }
            }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }

    private void exportMintTokenTransaction(Project project, MintTransaction mintTransaction, TransactionDetailsParams detailsParams, CardanoConsole console, LogListenerAdapter logListenerAdapter, Metadata metadata, String exportFile) {
        Task.Backgroundable task = new Task.Backgroundable(project, "Export Mint Token Transaction") {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                console.showInfoMessage(String.format("Export Token Minting Transaction starts ...\n"));

                try {
                    TransactionService transactionService = new TransactionServiceImpl(project, logListenerAdapter);

                    String signedTxnCbor = transactionService.exportMintTokenTransaction(mintTransaction, detailsParams, metadata);
                    SerializedTransaction serializedTransaction = SerializedTransaction.builder()
                            .type(SerializedTransaction.TX_CONWAY_ERA)
                            .description("")
                            .cborHex(signedTxnCbor).build();

                    exportTransaction(serializedTransaction, exportFile, logListenerAdapter);
                    console.showInfoMessage("Cbor hex of signed transaction : " + JsonUtil.getPrettyJson(serializedTransaction));
                    IdeaUtil.showNotification(project, getTitle(),
                            "Token Mint transaction exported successfully", NotificationType.INFORMATION, null);
                } catch (Exception exception) {
                    console.showErrorMessage("Export token mint transaction failed", exception);
                }
            }
        };

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }

    private String getTxnCommand() {
        return "Mint Token Transaction";
    }
}
