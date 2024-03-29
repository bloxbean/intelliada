package com.bloxbean.intelliada.idea.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;

public class IdeaUtil {
    public final static String PLUGIN_ID = "com.bloxbean.intelliada";

    public final static String ACCOUNT_LIST_ACTION = "Cardano.account.list";
    public final static String MULTISIG_ACCOUNT_LIST_ACTION = "Cardano.multisig-account.list";

    public static void showNotification(Project project, String title, String content, NotificationType notificationType, String actionId) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                Notification notification = new Notification(IdeaUtil.PLUGIN_ID,
                        title, content,
                        notificationType);

                if (actionId != null) {
                    ActionManager am = ActionManager.getInstance();
                    AnAction action = am.getAction(actionId);
                    notification.addAction(action);
                }

                Notifications.Bus.notify(notification, project);
            }
        });
    }

    public static void showNotification(String title, String content, NotificationType notificationType, String actionId) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                Notification notification = new Notification(IdeaUtil.PLUGIN_ID,
                        title, content,
                        notificationType);

                if (actionId != null) {
                    ActionManager am = ActionManager.getInstance();
                    AnAction action = am.getAction(actionId);
                    notification.addAction(action);
                }

                Notifications.Bus.notify(notification);
            }
        });
    }

    public static void showNotificationWithAction(Project project, String title, String content, NotificationType notificationType, NotificationAction action) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                Notification notification = new Notification(IdeaUtil.PLUGIN_ID,
                        title, content,
                        notificationType);

                notification.addAction(action);

                Notifications.Bus.notify(notification, project);
            }
        });
    }

    public static void invokeLater(Runnable runnable, ModalityState state) {
        ApplicationManager.getApplication().invokeLater(runnable, state);
    }
}
