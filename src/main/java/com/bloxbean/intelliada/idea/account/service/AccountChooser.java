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

import com.bloxbean.intelliada.idea.account.model.CardanoAccount;
import com.bloxbean.intelliada.idea.account.ui.ListAccountDialog;
import com.bloxbean.intelliada.idea.core.util.Network;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.intellij.openapi.project.Project;

import java.util.List;

public class AccountChooser {

    public static CardanoAccount getSelectedAccount(Project project, boolean showBalance) {
        ListAccountDialog listAccountDialog = new ListAccountDialog(project, null, showBalance);
        try {
            boolean result = listAccountDialog.showAndGet();

            if (!result) {
                return null;
            }

            CardanoAccount selectedAccount = listAccountDialog.getSelectAccount();
            return selectedAccount;
        } catch (Exception e) {
//            printError(project, e);
            return null;
        } finally {
            listAccountDialog.disposeIfNeeded();
        }
    }

    public static CardanoAccount getSelectedAccountForNetwork(Project project, Network network, boolean showBalance) {
        ListAccountDialog listAccountDialog = new ListAccountDialog(project, network, showBalance);
        try {
            boolean result = listAccountDialog.showAndGet();

            if (!result) {
                return null;
            }

            CardanoAccount selectedAccount = listAccountDialog.getSelectAccount();
            return selectedAccount;
        } catch (Exception e) {
//            printError(project, e);
            return null;
        } finally {
            listAccountDialog.disposeIfNeeded();
        }
    }

    //Just a workaround to capture error in console instead of ide error
    private static void printError(Project project, Exception e) {
        try {
            CardanoConsole console = CardanoConsole.getConsole(project);
            console.showErrorMessage("Error", e);
        } catch (Exception ex) {
        }
    }

}
