package com.bloxbean.intelliada.idea.nodeint.service.impl;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;
import com.bloxbean.intelliada.idea.nodeint.exception.TargetNodeNotConfigured;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.api.TransactionInfoService;
import com.intellij.openapi.project.Project;

import java.util.List;

public class TransactionInfoServiceImpl extends NodeBaseService implements TransactionInfoService {

    public TransactionInfoServiceImpl(RemoteNode node, LogListener logListener) throws TargetNodeNotConfigured {
        super(node, logListener);
    }

    public TransactionInfoServiceImpl(Project project, LogListener logListener) throws TargetNodeNotConfigured {
        super(project, logListener);
    }

    @Override
    public TransactionContent getTransactionDetailsByTxnHash(String txnHash) throws ApiCallException {
        Result<TransactionContent> result = null;
        try {
            result = backendService.getTransactionService().getTransaction(txnHash);
        } catch (ApiException e) {
            throw new ApiCallException("Could not get transaction details for the transaction hash : " + txnHash + "\n" + result.toString());
        }

        if(result.isSuccessful()) {
            return result.getValue();
        } else {
            if(result != null)
                logListener.error(result.toString());
            throw new ApiCallException("Could not get transaction details for the transaction hash : " + txnHash + "\n" + result.toString());
        }
    }

    @Override
    public List<MetadataJSONContent> getTransactionMetadata(String txnHash) throws ApiCallException {
        Result<List<MetadataJSONContent>> result = null;
        try {
            result = backendService.getMetadataService().getJSONMetadataByTxnHash(txnHash);
        } catch (ApiException e) {
            throw new ApiCallException("Could not get transaction metadata. TxnHash : " + txnHash + "\n" + result.toString());
        }

        if(result.isSuccessful()) {
            return result.getValue();
        } else {
            if(result != null)
                logListener.error(result.toString());
            throw new ApiCallException("Could not get transaction metadata. TxnHash : " + txnHash + "\n" + result.toString());
        }
    }
}
