package com.bloxbean.intelliada.idea.nodeint.service.impl;

import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.api.TransactionService;

import java.util.List;

public class TransactionServiceImpl extends NodeBaseService implements TransactionService  {

    public TransactionServiceImpl(RemoteNode remoteNode, LogListener logListener) {
        super(remoteNode, logListener);
    }

    @Override
    public Result transfer(List<PaymentTransaction> transactions, TransactionDetailsParams detailsParams) throws ApiCallException {
        return null;
    }
}
