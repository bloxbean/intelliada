package com.bloxbean.intelliada.idea.account.cache;

import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to read from result json file
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountCache {
    private List<CardanoAccount> accounts;

    public AccountCache() {
        accounts = new ArrayList<>();
    }

    public List<CardanoAccount> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<CardanoAccount> accounts) {
        this.accounts = accounts;
    }

    public void addAccount(CardanoAccount account) {
        if(accounts == null)
            accounts = new ArrayList<>();

        if(account == null)
            return;

        accounts.add(account);
    }

    public boolean deleteAccount(CardanoAccount account) {
        if(accounts == null || account == null)
            return false;

        if(accounts.contains(account)) {
            accounts.remove(account);
            return true;
        } else {
            return false;
        }
    }

    public boolean updateAccountName(String address, String name) {
        if(accounts == null || StringUtil.isEmpty(address))
            return false;

        for(CardanoAccount account: accounts) {
            if(address.equals(account.getAddress())) {
                account.setName(name);
                return true;
            }
        }

        return false;
    }

}