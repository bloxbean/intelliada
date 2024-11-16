package com.bloxbean.intelliada.idea.nodeint.service.impl;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;
import com.bloxbean.intelliada.idea.nodeint.exception.TargetNodeNotConfigured;
import com.bloxbean.intelliada.idea.nodeint.model.LedgerAccount;
import com.bloxbean.intelliada.idea.nodeint.service.api.CardanoAccountService;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.api.model.AssetBalance;
import com.bloxbean.intelliada.idea.util.JsonUtil;
import com.intellij.openapi.project.Project;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.bloxbean.intelliada.idea.util.AdaConversionUtil.LOVELACE;

public class AccountServiceImpl extends NodeBaseService implements CardanoAccountService {

    public AccountServiceImpl(Project project, LogListener logListener) throws TargetNodeNotConfigured {
        super(project, logListener);
    }

    @Override
    public Result<BigInteger> getAdaBalance(String address) {

        LedgerAccount account;
        AddressContent addressContent = null;
        try {
            Result<AddressContent> result = backendService.getAddressService().getAddressInfo(address);
            if (result.isSuccessful()) {
                addressContent = result.getValue();
            } else {
                if (result != null)
                    logListener.error(result.toString());
                throw new ApiException("Unable to get address details");
            }
            logListener.info(addressContent.toString());
        } catch (Exception e) {
            logListener.error("Error getting account balance", e);
            return Result.error("Error getting account balance : " + e.getMessage());
        }

        List<TxContentOutputAmount> amountList = addressContent.getAmount();

        Long amount = 0L;
        for (TxContentOutputAmount amt : amountList) {
            if (LOVELACE.equals(amt.getUnit())) {
                try {
                    amount += Long.parseLong(amt.getQuantity());
                } catch (Exception e) {
                    //TODO print error
                }
            }
        }

        Result result = Result.create(true, String.valueOf(amount)).withValue(amount);

        return result;
    }


    @Override
    public List<AssetBalance> getBalance(String address) {
        AddressContent addressContent = null;
        try {
            Result<AddressContent> result = backendService.getAddressService().getAddressInfo(address);
            if (result.isSuccessful()) {
                addressContent = result.getValue();
            } else {
                if (result != null)
                    logListener.error(result.toString());
                throw new ApiException("Unable to get address details");
            }
            logListener.info(JsonUtil.getPrettyJson(addressContent));
        } catch (Exception e) {
            logListener.error("Error getting account balance", e);
            return Collections.EMPTY_LIST;
        }

        List<TxContentOutputAmount> amountList = addressContent.getAmount();

        List<AssetBalance> assetBalances = new ArrayList<>();
        for (TxContentOutputAmount amt : amountList) {
            if (LOVELACE.equals(amt.getUnit())) {
                try {
                    AssetBalance assetBalance = AssetBalance.builder()
                            .unit(LOVELACE)
                            .quantity(new BigInteger(amt.getQuantity()))
                            .build();
                    assetBalances.add(0, assetBalance);
                } catch (Exception e) {
                    logListener.error("Error converting balance", e);
                }
            } else {
                try {
                    String assetName = null;
                    String policy = null;
                    try {
                        Tuple<String, String> policyAssetName = AssetUtil.getPolicyIdAndAssetName(amt.getUnit());
                        assetName = new String(HexUtil.decodeHexString(policyAssetName._2), StandardCharsets.UTF_8);
                        policy = policyAssetName._1;
                    } catch (Exception e) {}
                    AssetBalance assetBalance = AssetBalance.builder()
                            .unit(amt.getUnit())
                            .policy(policy)
                            .assetName(assetName)
                            .quantity(new BigInteger(amt.getQuantity()))
                            .build();
                    assetBalances.add(assetBalance);
                } catch (Exception e) {
                    logListener.error("Error converting balance", e);
                }
            }
        }

        return assetBalances;
    }

    @Override
    public List<Utxo> getUtxos(String address, int count, int page, OrderEnum order) throws ApiCallException {
        try {
            Result<List<Utxo>> result = backendService.getUtxoService().getUtxos(address, count, page, order);
            if (result.isSuccessful()) {
                logListener.info(JsonUtil.getPrettyJson(result.getValue()));
                return result.getValue();
            } else {
                if (result != null)
                    logListener.error(result.toString());
                throw new ApiException("Unable to get utxos for the address");
            }
        } catch (Exception e) {
            logListener.error("Error getting Utxos for the address: " + address, e);
            throw new ApiCallException("Error getting Utxos for the address: " + address, e);
        }
    }

    @Override
    public List<String> getRecentTransactions(String address, int count, int page, OrderEnum order) throws ApiCallException {
        try {
            Result<List<AddressTransactionContent>> result = backendService.getAddressService().getTransactions(address, count, page, order);
            if (result.isSuccessful()) {
                logListener.info(JsonUtil.getPrettyJson(result.getValue()));
                return result.getValue().stream().map(atc -> atc.getTxHash())
                        .collect(Collectors.toList());
            } else {
                if (result != null)
                    logListener.error(result.toString());
                throw new ApiException("Unable to get recent transactions for the address");
            }
        } catch (Exception e) {
            logListener.error("Error getting recent transactions for the address: " + address, e);
            throw new ApiCallException("Error getting recent transaction for the address: " + address, e);
        }
    }
}
