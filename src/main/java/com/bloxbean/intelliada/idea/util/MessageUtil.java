package com.bloxbean.intelliada.idea.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.Messages;

public class MessageUtil {

    public static void showMessage(String message, String title, boolean isError) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                if (isError) {
                    Messages.showErrorDialog(message, title);
                } else {
                    Messages.showInfoMessage(message, title);
                }
            }
        }, ModalityState.any());
    }
}
