package com.bloxbean.intelliada.idea.transaction;

public interface TransactionEntryListener {
    void senderAddressChanged(String address);

    void receiverAddressChanged(String receiver);
}
