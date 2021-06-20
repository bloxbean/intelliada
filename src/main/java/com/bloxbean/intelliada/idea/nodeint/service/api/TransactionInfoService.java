package com.bloxbean.intelliada.idea.nodeint.service.api;

import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;

import java.util.List;

public interface TransactionInfoService {
    TransactionContent getTransactionDetailsByTxnHash(String txnHash) throws ApiCallException;

    List<MetadataJSONContent> getTransactionMetadata(String txnHash) throws ApiCallException;

//    String getTransactionMetadataAsJson(String txnHash) throws ApiCallException;
}
