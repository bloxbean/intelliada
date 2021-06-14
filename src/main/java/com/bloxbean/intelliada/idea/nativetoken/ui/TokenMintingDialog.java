package com.bloxbean.intelliada.idea.nativetoken.ui;

import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.transaction.ui.TransactionDtlEntryForm;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TokenMintingDialog extends DialogWrapper {
    private JPanel mainPanel;
    private JTabbedPane tabbedPane1;
    private TokenMintAddressEntryForm tokenMintAddressEntryForm;
    private NewAssetEntryForm assetEntryForm;
    private TransactionDtlEntryForm txnDtlEntryForm;
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
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return mainPanel;
    }
}
