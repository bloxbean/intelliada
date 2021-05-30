package com.bloxbean.intelliada.idea.nodeint.service.impl;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import com.bloxbean.intelliada.idea.nodeint.exception.TargetNodeNotConfigured;
import com.bloxbean.intelliada.idea.nodeint.model.LedgerAccount;
import com.bloxbean.intelliada.idea.nodeint.service.api.CardanoAccountService;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.api.model.AssetBalance;
import com.intellij.openapi.project.Project;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.bloxbean.intelliada.idea.util.AdaConversionUtil.LOVELACE;

public class AccountServiceImpl extends NodeBaseService implements CardanoAccountService {

    public AccountServiceImpl(Project project, LogListener logListener) throws TargetNodeNotConfigured {
        super(project, logListener);
    }

    @Override
    public Result<Long> getAdaBalance(String address) {

        LedgerAccount account;
        AddressContent addressContent = null;
        try {
            Result<AddressContent> result = backendService.getAddressService().getAddressInfo(address);
            if(result.isSuccessful()) {
                addressContent = result.getValue();
            } else {
                if(result != null)
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
        for(TxContentOutputAmount amt: amountList) {
            if(LOVELACE.equals(amt.getUnit())) {
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
            if(result.isSuccessful()) {
                addressContent = result.getValue();
            } else {
                if(result != null)
                    logListener.error(result.toString());
                throw new ApiException("Unable to get address details");
            }
            logListener.info(addressContent.toString());
        } catch (Exception e) {
            logListener.error("Error getting account balance", e);
            return Collections.EMPTY_LIST;
        }

        List<TxContentOutputAmount> amountList = addressContent.getAmount();

        List<AssetBalance> assetBalances = new ArrayList<>();
        for(TxContentOutputAmount amt: amountList) {
            if(LOVELACE.equals(amt.getUnit())) {
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
                    AssetBalance assetBalance = AssetBalance.builder()
                            .unit(amt.getUnit())
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
}
