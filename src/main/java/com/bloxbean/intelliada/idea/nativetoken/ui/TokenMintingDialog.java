package com.bloxbean.intelliada.idea.nativetoken.ui;

import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.intelliada.idea.core.ui.BaseTransactionDialog;
import com.bloxbean.intelliada.idea.metadata.ui.MetadataEntryForm;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.transaction.TransactionEntryListener;
import com.bloxbean.intelliada.idea.transaction.ui.TransactionDtlEntryForm;
import com.bloxbean.intelliada.idea.utxos.ui.UtxoSelectEntryForm;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

public class TokenMintingDialog extends BaseTransactionDialog {
    private JPanel mainPanel;
    private JTabbedPane tabbedPane1;
    private TokenMintAddressEntryForm tokenMintAddressEntryForm;
    private NewAssetEntryForm assetEntryForm;
    private TransactionDtlEntryForm txnDtlEntryForm;
    private UtxoSelectEntryForm utxoSelectEntryForm;
    private MetadataEntryForm metadataEntryForm;
    private Project project;
    private CardanoConsole console;

    public TokenMintingDialog(@Nullable Project project) {
        super(project, true);
        setTitle("Mint Token");
        initialize(project);
        init();
    }

    public void initialize(Project project) {
        this.console = CardanoConsole.getConsole(project);
        this.project = project;
        tokenMintAddressEntryForm.initialize(project, console);
        assetEntryForm.initialize(project, console);
        txnDtlEntryForm.initializeData(project);
        utxoSelectEntryForm.initialize(project);
        metadataEntryForm.initialize(project);

        attachTransactionEntryListener();
    }

    public MintTransaction getMintTransactionRequest() {
        MintTransaction mintTransaction = new MintTransaction();
        mintTransaction.setSender(tokenMintAddressEntryForm.getCreatorAccount());
        mintTransaction.setReceiver(tokenMintAddressEntryForm.getReceiver());
        mintTransaction.setMintAssets(Arrays.asList(assetEntryForm.getMultiAsset()));

        Policy policy = new Policy();
        policy.setPolicyScript(assetEntryForm.getPolicyScript());
        policy.setPolicyKeys(assetEntryForm.getPolicyKeys());
        mintTransaction.setPolicy(policy);

        return mintTransaction;
    }

    public TransactionDtlEntryForm getTransactionDtlEntryForm() {
        return txnDtlEntryForm;
    }

    public UtxoSelectEntryForm getUtxoSelectEntryForm() {
        return utxoSelectEntryForm;
    }

    public MetadataEntryForm getMetadataEntryForm() {
        return metadataEntryForm;
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

        tokenMintAddressEntryForm.addTransactionEntryListener(transactionEntryListener);
    }

    @Override
    protected @Nullable ValidationInfo doInputValidation() {
        ValidationInfo validationInfo = tokenMintAddressEntryForm.doValidate();
        if (validationInfo != null)
            return validationInfo;

        validationInfo = assetEntryForm.doValidation();
        if (validationInfo != null)
            return validationInfo;

        validationInfo = txnDtlEntryForm.doValidate();
        if (validationInfo != null)
            return validationInfo;

        validationInfo = metadataEntryForm.doValidate();
        if (validationInfo != null)
            return validationInfo;

        return null;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel;
    }
}
