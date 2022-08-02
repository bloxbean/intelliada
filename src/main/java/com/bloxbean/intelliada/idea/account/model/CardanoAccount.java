package com.bloxbean.intelliada.idea.account.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CardanoAccount {
    private String name;
    private String address;
    private String mnemonic;
    private Long balance;
    private boolean isReadOnly;

    public CardanoAccount() {

    }

    public CardanoAccount(String address) {
        this.address = address;
        this.isReadOnly = true;
    }

    public CardanoAccount(String address, String mnemonic) {
        this.address = address;
        this.mnemonic = mnemonic;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setReadOnly(boolean readOnly) {
        isReadOnly = readOnly;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getMnemonic() {
        return mnemonic;
    }

    public void setMnemonic(String mnemonic) {
        this.mnemonic = mnemonic;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }

    public boolean isReadOnly() {
        return isReadOnly;
    }

    @Override
    public String toString() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardanoAccount that = (CardanoAccount) o;
        if (address == null || that.address == null)
            return false;
        return address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }
}
