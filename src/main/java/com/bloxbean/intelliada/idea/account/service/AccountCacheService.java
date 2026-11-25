/*
 * Copyright (c) 2021 BloxBean Project
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.bloxbean.intelliada.idea.account.service;

import com.bloxbean.intelliada.idea.account.cache.AccountCache;
import com.bloxbean.intelliada.idea.account.cache.GlobalCache;
import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

public class AccountCacheService {
    private final static Logger log = Logger.getInstance(AccountCacheService.class);
    //private Project project;

    /**
     * One lock for every read-modify-write of the legacy cache file (each rewrites the whole file): two writers never
     * read the same old list (ADR-005 M32-CDX-30). Taken after the account stores' key lock, never before it.
     */
    public static final Object STORAGE = new Object();

    public AccountCacheService() {
        //this.project = project;
    }

    public List<CardanoAccount> getAccounts() {
//        if(!isContinue())
//            return Collections.EMPTY_LIST;

        try {
            GlobalCache globalCache = new GlobalCache(getAccountCacheFolder());
            AccountCache accountCache = globalCache.getAccountCache();

            if (accountCache != null) {
                List<CardanoAccount> accounts = accountCache.getAccounts();
                return accounts;
            } else {
                return Collections.EMPTY_LIST;
            }
        } catch (Exception e) {
            return Collections.EMPTY_LIST;
        }
    }

    /** Every legacy account, or an IOException when the store exists but cannot be read (M32-CDX-18). */
    public List<CardanoAccount> getAccountsStrict() throws java.io.IOException {
        // Both places the cache can live, whatever is writable now: the home folder's, and the temp-dir fallback used
        // when home was not writable (M32-CDX-39). A folder that exists but cannot be read refuses.
        java.util.LinkedHashSet<java.nio.file.Path> folders = new java.util.LinkedHashSet<>();
        folders.add(java.nio.file.Path.of(System.getProperty("user.home"), ".intelliada"));
        folders.add(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), ".intelliada"));
        List<CardanoAccount> all = new java.util.ArrayList<>();
        synchronized (STORAGE) {
            for (java.nio.file.Path f : folders) {
                if (java.nio.file.Files.notExists(f, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                // The shared temp directory counts only when this user made it: a real directory, owned by this user,
                // writable by no one else (anyone can create /tmp/.intelliada; M33A-SEC-5).
                // Only the exact home cache is exempt; any other folder, under the profile or not, is checked (M33A-CDX-42).
                boolean homeCache = f.toAbsolutePath().normalize().equals(java.nio.file.Path.of(System.getProperty("user.home"), ".intelliada").toAbsolutePath().normalize());
                if (!homeCache && !ownPrivateDirectory(f)) {
                    // Untrusted and not in use: skipped. Untrusted but the legacy store's active folder (its chooser
                    // and writer use it): the ownership decision is refused, never made without it (M33A-CDX-21).
                    String active = getAccountCacheFolder();
                    if (active != null && java.nio.file.Path.of(active).toAbsolutePath().normalize().equals(f.toAbsolutePath().normalize())) {
                        throw new java.io.IOException("the active legacy account store is in a shared, untrusted folder");
                    }
                    continue;
                }
                if (!java.nio.file.Files.isDirectory(f) || !java.nio.file.Files.isReadable(f)) {
                    throw new java.io.IOException("an account cache folder cannot be read");
                }
                List<CardanoAccount> a = new GlobalCache(f.toString()).getAccountCacheStrict().getAccounts();
                if (a != null) all.addAll(a);
            }
        }
        return all;
    }

    private static boolean ownPrivateDirectory(java.nio.file.Path f) {
        try {
            if (!java.nio.file.Files.isDirectory(f, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return false;
            // The owner as a file-system principal, compared with the principal of a file this process creates: never a
            // name, which distinct users can share (M33A-CDX-32).
            var owner = java.nio.file.Files.getOwner(f, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            java.nio.file.Path probe = java.nio.file.Files.createTempFile("intelliada-owner", null);
            java.nio.file.attribute.UserPrincipal me;
            try {
                me = java.nio.file.Files.getOwner(probe);
            } finally {
                java.nio.file.Files.deleteIfExists(probe);
            }
            if (!owner.equals(me)) return false;
            if (!java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                // Without POSIX permissions the effective write access is not checked here, so a fallback is not trusted.
                return false;
            }
            var perms = java.nio.file.Files.getPosixFilePermissions(f, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            return !perms.contains(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE)
                    && !perms.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return false;
        }
    }

    public void storeAccount(CardanoAccount account) {
        synchronized (STORAGE) {
            GlobalCache globalCache = new GlobalCache(getAccountCacheFolder());
            AccountCache accountCache = globalCache.getAccountCache();

            accountCache.addAccount(account);
            globalCache.setAccountCache(accountCache);
        }
    }

    public boolean deleteAccount(CardanoAccount account) {
        synchronized (STORAGE) {
            GlobalCache globalCache = new GlobalCache(getAccountCacheFolder());
            AccountCache accountCache = globalCache.getAccountCache();

            if (accountCache.deleteAccount(account)) {
                globalCache.setAccountCache(accountCache);
                return true;
            }

            return false;
        }
    }


    public void clearCache() {
        GlobalCache globalCache = new GlobalCache(getAccountCacheFolder());
        globalCache.clearAccountCache();
    }

//    public RemoteAVMNodeAdapter getRemoteAvmAdapter(Project project) {
//        AvmConfigStateService.State state = KernelConfigurationHelper.getRemoteKernelInfo(project);
//
//        if(state == null || StringUtil.isEmpty(state.web3RpcUrl))
//            return null;
//
//        String web3RpcUrl = state.web3RpcUrl.trim();
//        RemoteAVMNodeAdapter remoteAVMNodeAdapter = new RemoteAVMNodeAdapter(web3RpcUrl);
//        return remoteAVMNodeAdapter;
//    }
//
//    public BigInteger getBalance(Account account, boolean isRemote) {
//        RemoteAVMNodeAdapter remoteAVMNodeAdapter = null;
//        if(isRemote) {
//            remoteAVMNodeAdapter = getRemoteAvmAdapter(project);
//        }
//
//        if (isRemote) {
//            if(remoteAVMNodeAdapter != null) {
//                return fetchRemoteBalance(remoteAVMNodeAdapter, account);
//            }
//        }
//
//        return null;
//    }
//
//    private BigInteger fetchRemoteBalance(RemoteAVMNodeAdapter remoteAVMNodeAdapter, Account account) {
//        return remoteAVMNodeAdapter.getBalance(account.getAddress());
//    }

//    private boolean isContinue() {
//        AvmService avmService = ServiceManager.getService(project, AvmService.class);
//
//        if(avmService != null && avmService.isAvmProject()) {
//            return true;
//        } else {
//            return false;
//        }
//    }

    protected String getAccountCacheFolder() {
        String home = System.getProperty("user.home");

        File cacheFolder = new File(home);

        if (!cacheFolder.canWrite()) {
            //Let's find temp folder
            String temp = System.getProperty("java.io.tmpdir");
            File tempFolder = new File(temp);

            if (!tempFolder.canWrite()) {
                log.warn("Unable to find a writable folder to keep account list cache");
                return null;
            } else {
                cacheFolder = tempFolder;
            }
        }

        File intelliAdaFolder = new File(cacheFolder, ".intelliada");
        if (!intelliAdaFolder.exists()) {
            intelliAdaFolder.mkdirs();
        }

        return intelliAdaFolder.getAbsolutePath();
    }

    public BigInteger getBalance(CardanoAccount account, boolean isRemote) {
        return BigInteger.ZERO;
    }

    public boolean updateAccountName(String address, String accountName) {
        synchronized (STORAGE) {
            GlobalCache globalCache = new GlobalCache(getAccountCacheFolder());
            AccountCache accountCache = globalCache.getAccountCache();

            boolean successful = accountCache.updateAccountName(address, accountName);
            globalCache.setAccountCache(accountCache);

            return successful;
        }
    }
}
