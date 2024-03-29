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
import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.notification.NotificationType;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * This class is used to get account list and balance. But it's only used for local Avm.
 */
public class AccountResultReader {

    public static List<CardanoAccount> getAccounts(File file) {
        ObjectMapper objectMapper = new ObjectMapper();
        AccountCache accountCache = null;
        try {
            accountCache = objectMapper.readValue(file, AccountCache.class);
        } catch (IOException e) {
            IdeaUtil.showNotification(null, "Account List", "Error reading account list", NotificationType.ERROR, null);
            return Collections.EMPTY_LIST;
        }

        List<CardanoAccount> accounts = accountCache.getAccounts();
        return accounts;
    }
}
