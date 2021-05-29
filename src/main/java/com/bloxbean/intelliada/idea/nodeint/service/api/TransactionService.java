package com.bloxbean.intelliada.idea.nodeint.service.api;

import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;

import java.util.List;

public interface TransactionService {

    public Result transfer(List<PaymentTransaction> transactions, TransactionDetailsParams detailsParams) throws ApiCallException;

}
