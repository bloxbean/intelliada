package com.bloxbean.intelliada.idea.nodeint.service.api;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;
import com.bloxbean.intelliada.idea.nodeint.service.api.model.AssetBalance;

import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.List;

public interface CardanoAccountService {
    public Result<BigInteger> getAdaBalance(String address) throws ApiCallException, URISyntaxException;

    public List<AssetBalance> getBalance(String address) throws ApiCallException;

    public List<Utxo> getUtxos(String address, int count, int page, OrderEnum order) throws ApiCallException;

    public List<String> getRecentTransactions(String address, int count, int page, OrderEnum order) throws ApiCallException;
}
