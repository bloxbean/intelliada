package com.bloxbean.intelliada.idea.transaction.ui;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.account.service.AccountChooser;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.core.util.Network;
import com.bloxbean.intelliada.idea.core.util.NetworkUtil;
import com.bloxbean.intelliada.idea.core.util.Networks;
import com.bloxbean.intelliada.idea.nodeint.CardanoNodeConfigurationHelper;
import com.bloxbean.intelliada.idea.nodeint.service.api.CardanoAccountService;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListenerAdapter;
import com.bloxbean.intelliada.idea.nodeint.service.api.TransactionService;
import com.bloxbean.intelliada.idea.nodeint.service.api.model.AssetBalance;
import com.bloxbean.intelliada.idea.nodeint.service.impl.AccountServiceImpl;
import com.bloxbean.intelliada.idea.nodeint.service.impl.TransactionServiceImpl;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.transaction.TransactionEntryListener;
import com.bloxbean.intelliada.idea.util.AdaConversionUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.intelliada.idea.util.AdaConversionUtil.*;

public class TransactionEntryForm {
    private JTextField senderTf;
    private JButton senderAccChooserBtn;
    private JTextField receiverTf;
    private JButton receiverAccChooserBtn;
    private JPasswordField senderMnemonicTf;
    private JTextField amountTf;
    private JComboBox availableBalanceCB;
    private JTextField feeTf;
    private JCheckBox feeOverrideCB;
    private JComboBox amtUnitTypeCB;
    private JButton calculateFeeBtn;
    private JLabel feeLabel;
    private JPanel mainPanel;
    private CardanoConsole console;
    private Project project;
    private DefaultComboBoxModel<AssetBalance> availableBalanceComboBoxModel;
    private boolean isMainnet;
    private Network network;
    private TransactionEntryListener transactionEntryListener;

    public TransactionEntryForm() {
        feeLabel.setText(ADA_SYMBOL + "");
    }

    public void initializeData(Project project) {
        console = CardanoConsole.getConsole(project);
        RemoteNode node = CardanoNodeConfigurationHelper.getTargetRemoteNode(project);
        if(node != null)
            isMainnet = NetworkUtil.isMainnet(node);

        if(isMainnet) {
            network = Networks.mainnet();
        } else {
            network = Networks.testnet();
        }

        senderAccChooserBtn.addActionListener(e -> {
            CardanoAccount cardanoAccount = AccountChooser.getSelectedAccountForNetwork(project, network, true);
            if(cardanoAccount != null) {
                setSenderAddress(cardanoAccount.getAddress());
                senderMnemonicTf.setText(cardanoAccount.getMnemonic());

                fetchSenderBalance();
            }
        });

        senderMnemonicTf.addFocusListener(new FocusListener() {
            String oldMnemonic;
            @Override
            public void focusGained(FocusEvent e) {
                oldMnemonic = senderMnemonicTf.getText();
            }

            @Override
            public void focusLost(FocusEvent e) {
                if(oldMnemonic != null && oldMnemonic.equals(senderMnemonicTf.getText())) {
                    oldMnemonic = null;
                    return;
                }
                oldMnemonic = null; //reset old mnemonic

                String mnemonic = String.valueOf(senderMnemonicTf.getPassword());
                try {
                    RemoteNode node = CardanoNodeConfigurationHelper.getTargetRemoteNode(project);
                    if(node != null) {
                        if(NetworkUtil.isMainnet(node)) {
                            Account account = new Account(mnemonic);
                            setSenderAddress(account.baseAddress());
                        } else {
                            Account account = new Account(com.bloxbean.cardano.client.common.model.Networks.testnet(), mnemonic);
                            setSenderAddress(account.baseAddress());
                        }
                    } else {
                        console.showErrorMessage("Target Cardano node is not configured. Please select a default node first.");
                    }
                } catch (Exception ex) {
                    senderTf.setText("");
                }

                fetchSenderBalance();
            }
        });

        receiverAccChooserBtn.addActionListener(e -> {
            CardanoAccount cardanoAccount = AccountChooser.getSelectedAccountForNetwork(project, network, true);
            if(cardanoAccount != null) {
                receiverTf.setText(cardanoAccount.getAddress());
            }
        });

        availableBalanceCB.addActionListener(e -> {
           AssetBalance assetBalance = (AssetBalance) availableBalanceCB.getSelectedItem();
           if(assetBalance == null) return;

           if(LOVELACE.equals(assetBalance.getUnit())) {
               amtUnitTypeCB.setVisible(true);
           } else {
               amtUnitTypeCB.setVisible(false);
           }
        });

        calculateFeeBtn.addActionListener(e -> {
            calculateFee();
        });

        feeOverrideCB.addActionListener(e -> {
            if(feeOverrideCB.isSelected()) {
                feeTf.setEditable(true);
            } else {
                feeTf.setEditable(false);
            }
        });
    }

    private void setSenderAddress(String address) {
        senderTf.setText(address);
        if(transactionEntryListener != null && address != null && !address.isEmpty()) {
            transactionEntryListener.senderAddressChanged(senderTf.getText());
        }
    }

    private void fetchSenderBalance() {
        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
                @Override
                public void run() {
                    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();

                    String senderAcc = senderTf.getText();
                    if(StringUtil.isEmpty(senderAcc))
                        return;

                    try {
                        CardanoAccountService accountService = new AccountServiceImpl(project, new LogListenerAdapter(console));
                        List<AssetBalance> assetBalanceList = accountService.getBalance(senderTf.getText());

                        availableBalanceComboBoxModel.removeAllElements();
                        availableBalanceComboBoxModel.addAll(assetBalanceList);
                    } catch (Exception e) {
                        console.showErrorMessage("Error getting available balance", e);
                    }
                    progressIndicator.setFraction(1.0);
                }
            }, "Getting available balance ...", true, project);

        } finally {

        }
    }

    private void calculateFee() {
        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
                @Override
                public void run() {
                    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();

                    PaymentTransaction paymentTransaction = buildTransaction();

                    try {
                        TransactionService transactionService = new TransactionServiceImpl(project, new LogListenerAdapter(console));
                        BigInteger fee = transactionService.calculateFee(paymentTransaction, TransactionDetailsParams.builder().build(), null);
                        feeTf.setText(AdaConversionUtil.lovelaceToAdaFormatted(fee));
                    } catch (Exception e) {
                        console.showErrorMessage("Error getting estimated fee", e);
                    }
                    progressIndicator.setFraction(1.0);
                }
            }, "Calculating estimated fee ...", true, project);

        } finally {

        }
    }

    public PaymentTransaction buildTransaction() {
        Account sender = getSender();
        String receiver = getReceiver();
        String unit = getUnit();
        BigInteger amount = getAmount();
        BigInteger fee = getFee();
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .sender(sender)
                .receiver(receiver)
                .unit(unit)
                .amount(amount)
                .fee(fee)
                .build();

        return paymentTransaction;
    }

    public Account getSender() {
        String senderMnenomic = new String(senderMnemonicTf.getPassword());
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
                senderAcc = new Account(com.bloxbean.cardano.client.common.model.Networks.testnet(), senderMnenomic);
                String baseAddress = senderAcc.baseAddress(); //Check if baseAddress can be derived
                if(StringUtil.isEmpty(baseAddress))
                    return null;
            }
            return senderAcc;
        } catch (Exception e) {
            console.showErrorMessage(e.getMessage(), e);
            return null;
        }
    }

    public String getReceiver() {
        return receiverTf.getText();
    }

    public String getUnit() {
        AssetBalance assetBalance = (AssetBalance) availableBalanceCB.getSelectedItem();
        if(assetBalance == null)
            return null;

        return assetBalance.getUnit();
    }

    public BigInteger getAmount() {
        String amtStr = amountTf.getText();
        if(StringUtil.isEmpty(amtStr))
            return null;

        try {
            String unit = getUnit();
            if(unit !=null && LOVELACE.equals(unit)) {
                //Find ADA / Lovelace in dropdown
                String amtUnit = (String)amtUnitTypeCB.getSelectedItem();
                if(ADA.equals(amtUnit)) {
                    return AdaConversionUtil.adaToLovelace(new BigDecimal(amtStr));
                } else if(LOVELACE.equals(amtUnit)) {
                    BigInteger amt = new BigInteger(amtStr);
                    return amt;
                } else {
                    console.showErrorMessage("Invalid unit type for amount : " + amtUnit);
                    return null;
                }
            } else {
                BigInteger amt = new BigInteger(amtStr);
                return amt;
            }
        } catch (Exception e) {
            console.showErrorMessage("Error getting amount", e);
            return null;
        }
    }

    public BigInteger getFee() {
        if(!feeOverrideCB.isSelected())
            return null;

        String feeStr = feeTf.getText();
        if(StringUtil.isEmpty(feeStr))
            return null;

        try {
            BigInteger fee = new BigInteger(feeStr);
            return fee;
        } catch (Exception e) {
            return null;
        }
    }

    public void addTransactionEntryListener(TransactionEntryListener transactionEntryListener) {
        this.transactionEntryListener = transactionEntryListener;
    }

    public @Nullable ValidationInfo doValidate() {
        if(getSender() == null) {
            return new ValidationInfo("Please select a valid sender Account or enter valid sender Mnemonic", senderTf);
        }

        if(getUnit() == null) {
            return new ValidationInfo("Please select a valid asset to transfer", availableBalanceCB);
        }

        if(StringUtil.isEmpty(getReceiver())) {
            return new ValidationInfo("Please select a valid receiver address", receiverTf);
        }

        if(getAmount() == null) {
            return new ValidationInfo("Please enter valid amount", amountTf);
        }

        return null;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here

        availableBalanceComboBoxModel = new DefaultComboBoxModel<AssetBalance>();
        availableBalanceCB = new ComboBox(availableBalanceComboBoxModel);

        amtUnitTypeCB = new ComboBox();
        amtUnitTypeCB.addItem(ADA);
        amtUnitTypeCB.addItem(LOVELACE);
    }
}
