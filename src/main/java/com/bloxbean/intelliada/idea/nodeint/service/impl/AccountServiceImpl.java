package com.bloxbean.intelliada.idea.nodeint.service.impl;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.nodeint.model.LedgerAccount;
import com.bloxbean.intelliada.idea.nodeint.service.api.CardanoAccountService;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.util.AdaConversionUtil;

import java.util.List;

public class AccountServiceImpl extends NodeBaseService implements CardanoAccountService {

    public AccountServiceImpl(RemoteNode remoteNode, LogListener logListener) {
        super(remoteNode, logListener);
    }

    @Override
    public Result<Long> getBalance(String address) {

        LedgerAccount account;
        AddressContent addressContent = null;
        try {
            Result<AddressContent> result = backendService.getAddressService().getAddressInfo(address);
            if(result.isSuccessful()) {
                addressContent = result.getValue();
            } else {
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
            if(AdaConversionUtil.LOVELACE.equals(amt.getUnit())) {
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
}
