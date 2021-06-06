package com.bloxbean.intelliada.idea.transaction;

public interface TransactionEntryListener {
    public void senderAddressChanged(String address);
    public void receiverAddressChanged(String receiver);
}
