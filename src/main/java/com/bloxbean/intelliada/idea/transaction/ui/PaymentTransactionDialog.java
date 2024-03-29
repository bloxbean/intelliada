package com.bloxbean.intelliada.idea.transaction.ui;

import com.bloxbean.intelliada.idea.core.ui.BaseTransactionDialog;
import com.bloxbean.intelliada.idea.metadata.ui.MetadataEntryForm;
import com.bloxbean.intelliada.idea.transaction.TransactionEntryListener;
import com.bloxbean.intelliada.idea.utxos.ui.UtxoSelectEntryForm;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PaymentTransactionDialog extends BaseTransactionDialog {
    private JPanel mainPanel;
    private TransactionEntryForm txnEntryForm;
    private JTabbedPane tabbedPane1;
    private TransactionDtlEntryForm txnDtlForm;
    private UtxoSelectEntryForm utxoSelectEntryForm;
    private MetadataEntryForm metadataEntryForm;

    public PaymentTransactionDialog(@Nullable Project project) {
        super(project, true);
        init();
        setTitle("Payment Transaction");

        txnEntryForm.initializeData(project);
        txnDtlForm.initializeData(project);
        utxoSelectEntryForm.initialize(project);
        metadataEntryForm.initialize(project);
        attachTransactionEntryListener();
    }

    private void attachTransactionEntryListener() {
        TransactionEntryListener transactionEntryListener = new TransactionEntryListener() {
            @Override
            public void senderAddressChanged(String address) {
                utxoSelectEntryForm.setAddress(address);
            }

            @Override
            public void receiverAddressChanged(String receiver) {
                //Not used for now
            }
        };

        txnEntryForm.addTransactionEntryListener(transactionEntryListener);
    }

    public TransactionEntryForm getTxnEntryForm() {
        return txnEntryForm;
    }

    public TransactionDtlEntryForm getTransactionDetlEntryForm() {
        return txnDtlForm;
    }

    public UtxoSelectEntryForm getUtxoSelectEntryForm() {
        return utxoSelectEntryForm;
    }

    public MetadataEntryForm getMetadataEntryForm() {
        return metadataEntryForm;
    }

    //    @Override
    protected @Nullable ValidationInfo _doValidate() {
        ValidationInfo validationInfo = txnEntryForm.doValidate();
        if (validationInfo != null)
            return validationInfo;

        validationInfo = txnDtlForm.doValidate();
        if (validationInfo != null)
            return validationInfo;

        validationInfo = utxoSelectEntryForm.doValidate(txnEntryForm.getUnit(), txnEntryForm.getAmount());
        if (validationInfo != null)
            return validationInfo;

        return null;
    }

    @Override
    protected ValidationInfo doInputValidation() {
        return _doValidate();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel;
    }
}
