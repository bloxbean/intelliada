package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.core.JoustConstants;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

/** Notifications whose title and content are always escaped plain text (I15): no markup and no URL listener. */
public final class JoustNotifications {

    private JoustNotifications() {
    }

    public static Notification create(String title, String text, NotificationType type) {
        return NotificationGroupManager.getInstance().getNotificationGroup(JoustConstants.NOTIFICATION_GROUP)
                .createNotification(PlainText.escape(PlainText.clean(title, 200)), PlainText.fragment(text, 2_000), type);
    }

    public static void show(Project project, String title, String text, NotificationType type) {
        create(title, text, type).notify(project);
    }
}
