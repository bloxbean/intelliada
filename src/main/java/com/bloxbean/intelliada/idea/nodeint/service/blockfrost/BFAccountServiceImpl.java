package com.bloxbean.intelliada.idea.nodeint.service.blockfrost;

import com.bloxbean.cardano.blockfrost.api.CardanoAddressesApi;
import com.bloxbean.cardano.blockfrost.model.AddressContent;
import com.bloxbean.cardano.blockfrost.model.TxContentOutputAmount;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.nodeint.model.LedgerAccount;
import com.bloxbean.intelliada.idea.nodeint.model.Result;
import com.bloxbean.intelliada.idea.nodeint.service.api.CardanoAccountService;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.util.AdaConversionUtil;

import java.util.List;

public class BFAccountServiceImpl extends BFBaseService implements CardanoAccountService {

    public BFAccountServiceImpl(RemoteNode remoteNode, LogListener logListener) {
        super(remoteNode, logListener);
    }

    @Override
    public Result<Long> getBalance(String address) {
        CardanoAddressesApi addressesApi = new CardanoAddressesApi(apiClient);

        LedgerAccount account;
        AddressContent addressContent = null;
        try {
            addressContent = addressesApi.addressesAddressGet(address);
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
