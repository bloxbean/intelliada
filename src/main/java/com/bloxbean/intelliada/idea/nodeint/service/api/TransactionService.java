package com.bloxbean.intelliada.idea.nodeint.service.api;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.transaction.model.MintTransaction;
import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import com.bloxbean.cardano.client.transaction.model.TransactionDetailsParams;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;

import java.math.BigInteger;
import java.util.List;

public interface TransactionService {

    BigInteger calculateFee(PaymentTransaction paymentTransaction, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiCallException;

    String transfer(PaymentTransaction transactions,
                           TransactionDetailsParams detailsParams, Metadata metadata) throws ApiCallException;

    String exportSignedTransaction(PaymentTransaction transactions,
                                          TransactionDetailsParams detailsParams, Metadata metadata) throws ApiCallException;


    String mintToken(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiCallException;

    String exportMintTokenTransaction(MintTransaction mintTransaction, TransactionDetailsParams detailsParams, Metadata metadata)
            throws ApiCallException;

}
