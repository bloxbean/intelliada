package com.bloxbean.intelliada.idea.nodeint.service.impl;

import com.bloxbean.cardano.client.api.helper.model.TransactionResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;
import com.bloxbean.intelliada.idea.nodeint.exception.TargetNodeNotConfigured;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.api.TransactionService;
import com.bloxbean.intelliada.idea.util.AdaConversionUtil;
import com.bloxbean.intelliada.idea.util.JsonUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.bloxbean.intelliada.idea.util.AdaConversionUtil.ADA_SYMBOL;

public class TransactionServiceImpl extends NodeBaseService implements TransactionService {

    public TransactionServiceImpl(Project project, LogListener logListener) throws TargetNodeNotConfigured {
        super(project, logListener);
    }

    public BigInteger calculateFee(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams,
                                   Metadata metadata) throws ApiCallException {
        BigInteger fee = null;
        try {
            long ttl = calculateTtl();
            detailsParams.setTtl(ttl);
            fee = backendService.getFeeCalculationService().calculateFee(paymentTransaction, detailsParams, metadata);
        } catch (Exception e) {
            throw new ApiCallException("Fee calculation failed", e);
        }
        logListener.info("Estimated fee for transaction " + " : "
                + AdaConversionUtil.lovelaceToAdaFormatted(fee) + " " + '\u20B3');

        return fee;
    }

    @Override
    public String transfer(List<PaymentTransaction> transactions, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiCallException {

        logListener.info("Starting payment transaction ...");
        printRemoteNodeDetails();
        //Calculate ttl
        if (detailsParams.getTtl() == 0) { //Get ttl
            logListener.info("Calculate Time to Live (ttl) = current slot + 1000");
            Long calculatedTtl = calculateTtl();
            logListener.info("Time to Live (ttl) : " + calculatedTtl);
            if (calculatedTtl == null)
                throw new ApiCallException("Ttl calculation failed");
            detailsParams.setTtl(calculatedTtl);
        }

        try {
            int count = 1;
            for (PaymentTransaction paymentTransaction : transactions) {
                if (paymentTransaction.getFee() == null || paymentTransaction.getFee().equals(BigInteger.ZERO)) { //Calculate fee
                    logListener.info("Calculate fee ...");
                    BigInteger fee = null;
                    fee = backendService.getFeeCalculationService().calculateFee(paymentTransaction, detailsParams, metadata);
                    logListener.info("Estimated fee for transaction " + count++ + " : "
                            + AdaConversionUtil.lovelaceToAdaFormatted(fee) + " " + ADA_SYMBOL);
                    paymentTransaction.setFee(fee);
                }
            }
        } catch (Exception e) {
            throw new ApiCallException("Fee calculation failed", e);
        }

        try {
            printTransactionRequests(transactions);

            Result<TransactionResult> result = backendService.getTransactionHelperService().transfer(transactions, detailsParams, metadata);
            try {
                byte[] txnCborBytes = result.getValue().getSignedTxn();
                Transaction transaction = Transaction.deserialize(txnCborBytes);
                logListener.info("Transaction Request: " + JsonUtil.getPrettyJson(transaction));
            } catch (Exception e) {
                logListener.warn("Transaction de-serialization error", e);
            }
            if (result.isSuccessful()) {
                logListener.info("Transaction submitted successfully");
                logListener.info("Transaction id: " + result.getValue().getTransactionId());

                waitForTransaction(result.getValue().getTransactionId());
                return result.getValue().getTransactionId();
            } else {
                logListener.error("Transaction failed");
                logListener.error(result.toString());
                throw new ApiCallException("Transaction failed. " + result.getResponse());
            }
        } catch (Exception e) {
            throw new ApiCallException("Transaction failed", e);
        }
    }

    @Override
    public String exportSignedTransaction(List<PaymentTransaction> transactions, TransactionDetailsParams detailsParams, Metadata metadata) throws ApiCallException {
        logListener.info("Creating transaction ...");
        printRemoteNodeDetails();
        //Calculate ttl
        if (detailsParams.getTtl() == 0) { //Get ttl
            logListener.info("Calculate Time to Live (ttl) = current slot + 1000");
            Long calculatedTtl = calculateTtl();
            logListener.info("Time to Live (ttl) : " + calculatedTtl);
            if (calculatedTtl == null)
                throw new ApiCallException("Ttl calculation failed");
            detailsParams.setTtl(calculatedTtl);
        }

        try {
            int count = 1;
            for (PaymentTransaction paymentTransaction : transactions) {
                if (paymentTransaction.getFee() == null || paymentTransaction.getFee().equals(BigInteger.ZERO)) { //Calculate fee
                    logListener.info("Calculate fee ...");
                    BigInteger fee = null;
                    fee = backendService.getFeeCalculationService().calculateFee(paymentTransaction, detailsParams, metadata);
                    logListener.info("Estimated fee for transaction " + count++ + " : "
                            + AdaConversionUtil.lovelaceToAdaFormatted(fee) + " " + ADA_SYMBOL);
                    paymentTransaction.setFee(fee);
                }
            }
        } catch (Exception e) {
            throw new ApiCallException("Fee calculation failed", e);
        }

        try {
            printTransactionRequests(transactions);

            String signedCborHex = backendService.getTransactionHelperService().getTransactionBuilder()
                    .createSignedTransaction(transactions, detailsParams, metadata);
            if (StringUtil.isEmpty(signedCborHex))
                throw new ApiCallException("Export transaction failed.");
            else
                return signedCborHex;
        } catch (Exception e) {
            throw new ApiCallException("Export Transaction failed", e);
        }
    }

    @Override
    public String mintToken(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata) throws ApiCallException {
        logListener.info("Starting Token Mint transaction ...");
        printRemoteNodeDetails();
        //Calculate ttl
        if (detailsParams.getTtl() == 0) { //Get ttl
            logListener.info("Calculate Time to Live (ttl) = current slot + 1000");
            Long calculatedTtl = calculateTtl();
            logListener.info("Time to Live (ttl) : " + calculatedTtl);
            if (calculatedTtl == null)
                throw new ApiCallException("Ttl calculation failed");
            detailsParams.setTtl(calculatedTtl);
        }

        try {
            int count = 1;
            logListener.info("Calculate fee ...");
            BigInteger fee = null;
            fee = backendService.getFeeCalculationService().calculateFee(mintTransaction, detailsParams, metadata);
            logListener.info("Estimated fee for transaction " + count++ + " : "
                    + AdaConversionUtil.lovelaceToAdaFormatted(fee) + " " + ADA_SYMBOL);
            mintTransaction.setFee(fee);
        } catch (Exception e) {
            throw new ApiCallException("Fee calculation failed", e);
        }

        try {
            printTokenMintRequest(mintTransaction);

            Result<TransactionResult> result = backendService.getTransactionHelperService().mintToken(mintTransaction, detailsParams, metadata);
            try {
                byte[] txnCborBytes = result.getValue().getSignedTxn();
                Transaction transaction = Transaction.deserialize(txnCborBytes);
                logListener.info("Transaction Request: " + JsonUtil.getPrettyJson(transaction));
            } catch (Exception e) {
                logListener.warn("Transaction de-serialization error", e);
            }
            if (result.isSuccessful()) {
                logListener.info("Transaction submitted successfully");
                logListener.info("Transaction id: " + result.getValue().getTransactionId());

                waitForTransaction(result.getValue().getTransactionId());
                return result.getValue().getTransactionId();
            } else {
                logListener.error("Transaction failed");
                logListener.error(result.toString());
                throw new ApiCallException("Transaction failed. " + result.getResponse());
            }
        } catch (Exception e) {
            throw new ApiCallException("Transaction failed", e);
        }
    }

    @Override
    public String exportMintTokenTransaction(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata) throws ApiCallException {
        logListener.info("Creating Token Mint transaction ...");
        printRemoteNodeDetails();
        //Calculate ttl
        if (detailsParams.getTtl() == 0) { //Get ttl
            logListener.info("Calculate Time to Live (ttl) = current slot + 1000");
            Long calculatedTtl = calculateTtl();
            logListener.info("Time to Live (ttl) : " + calculatedTtl);
            if (calculatedTtl == null)
                throw new ApiCallException("Ttl calculation failed");
            detailsParams.setTtl(calculatedTtl);
        }

        try {
            int count = 1;
            logListener.info("Calculate fee ...");
            BigInteger fee = null;
            fee = backendService.getFeeCalculationService().calculateFee(mintTransaction, detailsParams, metadata);
            logListener.info("Estimated fee for transaction " + count++ + " : "
                    + AdaConversionUtil.lovelaceToAdaFormatted(fee) + " " + ADA_SYMBOL);
            mintTransaction.setFee(fee);
        } catch (Exception e) {
            throw new ApiCallException("Fee calculation failed", e);
        }

        try {
            printTokenMintRequest(mintTransaction);

            String signedCborHex = backendService.getTransactionHelperService()
                    .getTransactionBuilder().createSignedMintTransaction(mintTransaction, detailsParams, metadata);
            if (StringUtil.isEmpty(signedCborHex))
                throw new ApiCallException("Export transaction failed.");
            else
                return signedCborHex;
        } catch (Exception e) {
            throw new ApiCallException("Export transaction failed", e);
        }
    }

    private void printTransactionRequests(List<PaymentTransaction> transactions) {
        int count = 1;
        for (PaymentTransaction transaction : transactions) {
            logListener.info("\nTransaction - " + count++);
            logListener.info("Sender   : " + transaction.getSender());
            logListener.info("Receiver : " + transaction.getReceiver());
            logListener.info("Unit     : " + transaction.getUnit());
            logListener.info("Amount   : " + transaction.getAmount());
            logListener.info("-------------------------------------------\n");
        }
    }

    private void printTokenMintRequest(MintTransaction mintTransaction) {
        logListener.info("Creator   : " + mintTransaction.getSender());
        logListener.info("Receiver  : " + mintTransaction.getReceiver());
        if (mintTransaction.getMintAssets() != null) {
            for (MultiAsset ma : mintTransaction.getMintAssets()) {
                logListener.info("Policy Id  : " + ma.getPolicyId());
                for (Asset asset : ma.getAssets()) {
                    logListener.info("AssetName  : " + asset.getName());
                    logListener.info("Quantity   : " + asset.getValue());

                    String assetNameHex = null;
                    if (asset.getName() != null)
                        assetNameHex = HexUtil.encodeHexString(asset.getName().getBytes(StandardCharsets.UTF_8));
                    else
                        assetNameHex = HexUtil.encodeHexString("".getBytes(StandardCharsets.UTF_8));

                    try {
                        logListener.info("Fingerprint: " +
                                AssetUtil.calculateFingerPrint(ma.getPolicyId(), assetNameHex));
                    } catch (Exception e) {
                    }
                    logListener.info("\n");
                }
            }
        }
        logListener.info("-------------------------------------------\n");
    }

    @NotNull
    private Long calculateTtl() {
        try {
            Result<Block> blockResult = backendService.getBlockService().getLatestBlock();
            if (blockResult.isSuccessful()) {
                return blockResult.getValue().getSlot() + 1000;
            } else
                return null;
        } catch (Exception e) {
            logListener.error("Ttl calcuation failed", e);
            return null;
        }
    }
}
