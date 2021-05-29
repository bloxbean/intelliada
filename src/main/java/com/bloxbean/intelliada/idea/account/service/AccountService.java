package com.bloxbean.intelliada.idea.account.service;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

public class AccountService {
    private final static Logger LOG = Logger.getInstance(AccountService.class);

    private AccountCacheService accountCacheService;

    public static AccountService getAccountService() {
        return new AccountService();
    }

    public AccountService() {
        this.accountCacheService = new AccountCacheService();
    }

    public AccountService(AccountCacheService accountCacheService) {
        this.accountCacheService = accountCacheService;
    }

    public CardanoAccount createNewAccount(String accountName, Network network) throws NoSuchAlgorithmException {
        if(StringUtil.isEmpty(accountName))
            accountName = "New Account";

        CardanoAccount cardanoAccount = new CardanoAccount();
        Account account = new Account(network);
        cardanoAccount.setAddress(account.baseAddress());
        cardanoAccount.setMnemonic(account.mnemonic());
        cardanoAccount.setName(accountName);
        accountCacheService.storeAccount(cardanoAccount);
        return cardanoAccount;
    }

    public CardanoAccount createNewAccount() throws NoSuchAlgorithmException {
        return createNewAccount(null, null);
    }

    public boolean importAccount(CardanoAccount account) {
        if(account == null)
            return false;

        CardanoAccount existingAcc = getAccountByAddress(account.getAddress());
        if(existingAcc != null) {
            return false;
        }

        accountCacheService.storeAccount(account);
        return true;
    }

    public List<CardanoAccount> getAccounts() {
        return accountCacheService.getAccounts();
    }

    public CardanoAccount getAccountByAddress(String address) {
        List<CardanoAccount> accounts = accountCacheService.getAccounts();
        for(CardanoAccount acc: accounts) {
            if(acc.getAddress().equals(address))
                return acc;
        }

        return null;
    }

    public CardanoAccount getAccountFromMnemonic(String mnemonic) {
        try {
//            Account account = new Account(mnemonic);
//            return new AlgoAccount(account.getAddress().toString(), mnemonic);
            return new CardanoAccount();
        } catch (Exception e) {
            if(LOG.isDebugEnabled()) {
                LOG.warn("Account derivation failed from mnemonic");
            }
            return null;
        }
    }

    public void clearCache() {
        accountCacheService.clearCache();
    }

    public boolean removeAccount(CardanoAccount account) {
        if(account == null) return false;
        return accountCacheService.deleteAccount(account);
    }

    public boolean updateAccountName(String address, String accountName) {
        if(StringUtil.isEmpty(address))
            return false;

        return accountCacheService.updateAccountName(address, accountName);
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        new AccountService().createNewAccount();
    }

}
