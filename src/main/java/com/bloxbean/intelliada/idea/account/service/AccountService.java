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
        if (StringUtil.isEmpty(accountName))
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
        if (account == null)
            return false;
        // ADR-005 I12: the ownership check and the write are one step for both stores (M32-CDX-17); off the EDT.
        Object keys = keysLock();
        synchronized (keys) {
            return importChecked(account);
        }
    }

    private boolean importChecked(CardanoAccount account) {
        synchronized (AccountCacheService.STORAGE) {   // key lock, then storage lock (M32-CDX-30)
            return importLocked(account);
        }
    }

    /** Whether a legacy row is withheld because the JOUST store holds its key: only rows that hold a phrase are (L39). */
    public static boolean withheld(CardanoAccount a) {
        return a != null && a.getMnemonic() != null && !a.getMnemonic().isBlank() && heldByJoust(a.getAddress());
    }

    private boolean importLocked(CardanoAccount account) {
        if (withheld(account))
            return false;   // a key the JOUST run panel holds is never also a legacy account; watch-only rows hold none (M32-CDX-36)

        CardanoAccount existingAcc = getAccountByAddress(account.getAddress());
        if (existingAcc != null) {
            return false;
        }

        accountCacheService.storeAccount(account);
        return true;
    }

    /** The monitor both account stores take for an ownership check and its write (the JOUST store's). */
    private static Object keysLock() {
        com.intellij.openapi.application.Application app = com.intellij.openapi.application.ApplicationManager.getApplication();
        return app == null ? AccountService.class : com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts.getInstance();
    }

    /** Whether the JOUST run panel's store holds this address's payment key (lock-free; never reads a secret). */
    public static boolean heldByJoust(String address) {
        if (address == null) return false;
        String fp = com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts.anyPaymentKeyHash(address);
        if (fp == null) return false;
        com.intellij.openapi.application.Application app = com.intellij.openapi.application.ApplicationManager.getApplication();
        return app != null && com.bloxbean.intelliada.idea.joust.accounts.JoustAccounts.getInstance().holdsKey(fp);
    }

    /**
     * The legacy accounts IntelliAda offers: every chooser and the account list come here. An account whose payment key
     * the JOUST store holds (a migrated copy) is hidden, so a key is selectable under one class only (ADR-005 L39).
     */
    public List<CardanoAccount> getAccounts() {
        // A watch-only entry holds no key: it stays listed (M32-DES-18).
        return accountCacheService.getAccounts().stream().filter(a -> !withheld(a))
                .collect(java.util.stream.Collectors.toList());
    }

    /** Every legacy account, hidden ones included; an IOException when the store cannot be read (for ownership checks). */
    public List<CardanoAccount> getAllAccountsStrict() throws java.io.IOException {
        return accountCacheService.getAccountsStrict();
    }

    public CardanoAccount getAccountByAddress(String address) {
        List<CardanoAccount> accounts = accountCacheService.getAccounts();
        for (CardanoAccount acc : accounts) {
            if (acc.getAddress().equals(address))
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
            if (LOG.isDebugEnabled()) {
                LOG.warn("Account derivation failed from mnemonic");
            }
            return null;
        }
    }

    public void clearCache() {
        accountCacheService.clearCache();
    }

    public boolean removeAccount(CardanoAccount account) {
        if (account == null) return false;
        return accountCacheService.deleteAccount(account);
    }

    public boolean updateAccountName(String address, String accountName) {
        if (StringUtil.isEmpty(address))
            return false;

        return accountCacheService.updateAccountName(address, accountName);
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        new AccountService().createNewAccount();
    }

}
