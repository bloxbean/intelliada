package com.bloxbean.intelliada.idea.account.service;

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

    public CardanoAccount createNewAccount(String accountName) throws NoSuchAlgorithmException {
        if(StringUtil.isEmpty(accountName))
            accountName = "New Account";

        CardanoAccount account = new CardanoAccount();
//        Address address = account.getAddress();
//
//        AlgoAccount algoAccount = new AlgoAccount(address.toString(), account.toMnemonic());
//        algoAccount.setName(accountName);

        account.setAddress(UUID.randomUUID().toString());
        account.setMnemonic(UUID.randomUUID().toString());

        accountCacheService.storeAccount(account);
        return account;
    }

    public CardanoAccount createNewAccount() throws NoSuchAlgorithmException {
        return createNewAccount(null);
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
