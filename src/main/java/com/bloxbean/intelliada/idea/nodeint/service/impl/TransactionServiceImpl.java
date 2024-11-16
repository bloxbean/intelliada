package com.bloxbean.intelliada.idea.nodeint.service.impl;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
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
    public String transfer(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams, Metadata metadata)
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

        var transaction = buildTransaction(paymentTransaction, detailsParams, metadata);
        try {
            var result = backendService.getTransactionService().submitTransaction(transaction.serialize());

            if (result.isSuccessful()) {
                logListener.info("Transaction submitted successfully");
                logListener.info("Transaction id: " + result.getValue());

                waitForTransaction(result.getValue());
                return result.getValue();
            } else {
                logListener.error("Transaction failed");
                logListener.error(result.toString());
                throw new ApiCallException("Transaction failed. " + result.getResponse());
            }
        } catch (ApiException e) {
            throw new RuntimeException(e);
        } catch (CborSerializationException e) {
            throw new RuntimeException(e);
        }
    }

    private Transaction buildTransaction(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams,
                                         Metadata metadata) throws ApiCallException {
        try {
            Tx tx = new Tx();

            if (paymentTransaction.getUtxosToInclude() != null && paymentTransaction.getUtxosToInclude().size() > 0)
                tx.collectFrom(paymentTransaction.getUtxosToInclude());

            tx.payToAddress(paymentTransaction.getReceiver(), new Amount(paymentTransaction.getUnit(), paymentTransaction.getAmount()));
            tx.from(paymentTransaction.getSender().baseAddress());

            if (metadata != null)
                tx.attachMetadata(metadata);

            var txContext = new QuickTxBuilder(backendService)
                    .compose(tx)
                    .withSigner(SignerProviders.signerFrom(paymentTransaction.getSender()));

            //Additional accounts
            for (Account account : paymentTransaction.getAdditionalWitnessAccounts()) {
                txContext.withSigner(SignerProviders.signerFrom(account));
            }


            txContext = txContext.preBalanceTx((context, transaction) -> {
                if (detailsParams.getTtl() != 0)
                    transaction.getBody().setTtl(detailsParams.getTtl());

                if (detailsParams.getValidityStartInterval() != 0)
                    transaction.getBody().setValidityStartInterval(detailsParams.getValidityStartInterval());
            });

            txContext.withTxInspector(transaction -> {
                logListener.info(JsonUtil.getPrettyJson(transaction));
            });

            return txContext.buildAndSign();


        } catch (Exception e) {
            throw new ApiCallException("Transaction build failed", e);
        }
    }

    @Override
    public String exportSignedTransaction(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams, Metadata metadata) throws ApiCallException {
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

        var transaction = buildTransaction(paymentTransaction, detailsParams, metadata);

        try {
            printTransactionRequests(paymentTransaction);

            String signedCborHex = transaction.serializeToHex();
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
            printTokenMintRequest(mintTransaction);

            var transaction = buildMintTransaction(mintTransaction, detailsParams, metadata);

            var result = backendService.getTransactionService().submitTransaction(transaction.serialize());

            if (result.isSuccessful()) {
                logListener.info("Transaction submitted successfully");
                logListener.info("Transaction id: " + result.getValue());

                waitForTransaction(result.getValue());
                return result.getValue();
            } else {
                logListener.error("Transaction failed");
                logListener.error(result.toString());
                throw new ApiCallException("Transaction failed. " + result.getResponse());
            }
        } catch (Exception e) {
            throw new ApiCallException("Transaction failed", e);
        }
    }

    private Transaction buildMintTransaction(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata) {
        Tx tx = new Tx();

        if (mintTransaction.getUtxosToInclude() != null && mintTransaction.getUtxosToInclude().size() > 0)
            tx.collectFrom(mintTransaction.getUtxosToInclude());

        for (MultiAsset multiAsset : mintTransaction.getMintAssets()) {
            tx.mintAssets(mintTransaction.getPolicy().getPolicyScript(), multiAsset.getAssets(), mintTransaction.getReceiver());
        }

        if (metadata != null)
            tx.attachMetadata(metadata);

        tx.from(mintTransaction.getSender().baseAddress());

        var txContext = new QuickTxBuilder(backendService)
                .compose(tx)
                .withSigner(SignerProviders.signerFrom(mintTransaction.getSender()))
                .withSigner(SignerProviders.signerFrom(mintTransaction.getPolicy()));

        //Additional accounts
        if (mintTransaction.getAdditionalWitnessAccounts() != null && mintTransaction.getAdditionalWitnessAccounts().size() > 0) {
            for (Account account : mintTransaction.getAdditionalWitnessAccounts()) {
                txContext.withSigner(SignerProviders.signerFrom(account));
            }
        }

        txContext = txContext.preBalanceTx((context, transaction) -> {
            if (detailsParams.getTtl() != 0)
                transaction.getBody().setTtl(detailsParams.getTtl());

            if (detailsParams.getValidityStartInterval() != 0)
                transaction.getBody().setValidityStartInterval(detailsParams.getValidityStartInterval());
        });

        txContext.withTxInspector(transaction -> {
            logListener.info(JsonUtil.getPrettyJson(transaction));
        });

        return txContext.buildAndSign();
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

        var transaction = buildMintTransaction(mintTransaction, detailsParams, metadata);

        try {
            printTokenMintRequest(mintTransaction);

            String signedCborHex = transaction.serializeToHex();
            if (StringUtil.isEmpty(signedCborHex))
                throw new ApiCallException("Export transaction failed.");
            else
                return signedCborHex;
        } catch (Exception e) {
            throw new ApiCallException("Export transaction failed", e);
        }
    }

    private void printTransactionRequests(PaymentTransaction transaction) {
        logListener.info("Sender   : " + transaction.getSender());
        logListener.info("Receiver : " + transaction.getReceiver());
        logListener.info("Unit     : " + transaction.getUnit());
        logListener.info("Amount   : " + transaction.getAmount());
        logListener.info("-------------------------------------------\n");
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
