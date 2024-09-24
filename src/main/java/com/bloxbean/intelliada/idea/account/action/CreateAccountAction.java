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

package com.bloxbean.intelliada.idea.account.action;

import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.account.service.AccountService;
import com.bloxbean.intelliada.idea.account.ui.CreateAccountDialog;
import com.bloxbean.intelliada.idea.core.util.Network;
import com.bloxbean.intelliada.idea.core.util.NetworkUtil;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.security.NoSuchAlgorithmException;

/**
 * @author Satya
 */

public class CreateAccountAction extends AnAction {
    private final static Logger LOG = Logger.getInstance(CreateAccountAction.class);

    public CreateAccountAction() {
        super(AllIcons.General.Add);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();

        CreateAccountDialog dialog = new CreateAccountDialog();
        boolean ok = dialog.showAndGet();
        if (!ok) return;

        String accountName = dialog.getAccountName();
        Network network = dialog.getNetwork();

        com.bloxbean.cardano.client.common.model.Network clNetwork = NetworkUtil.convertToCLNetwork(network);

        AccountService accountService = new AccountService();

        CardanoConsole console = CardanoConsole.getConsole(project);

        try {
            CardanoAccount account = accountService.createNewAccount(accountName, clNetwork);

            console.clearAndshow();
            console.showInfoMessage(String.format("Address: %s", account.getAddress()));
            console.showInfoMessage(String.format("Mnemonic: %s", account.getMnemonic()));
            console.showSuccessMessage("New account created successfully");

            IdeaUtil.showNotification(project, "Account Create",
                    "A new account created successfully", NotificationType.INFORMATION, IdeaUtil.ACCOUNT_LIST_ACTION);
        } catch (NoSuchAlgorithmException ex) {
            LOG.warn("Unable to create new Account", ex);
            console.showErrorMessage("Account creation failed", ex);
        }
    }

}
