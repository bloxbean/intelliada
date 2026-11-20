package com.bloxbean.intelliada.idea.joust.actions;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.lsp.JoustLanguageServerApi;
import com.bloxbean.intelliada.idea.joust.lsp.JoustNotifications;
import com.bloxbean.intelliada.idea.joust.lsp.JoustStatusCache;
import com.intellij.notification.NotificationType;

/**
 * JOUST: Show compiler and mode: the last {@code joust/status} joust-lsp pushed for this file (the compiler version,
 * bundled or project, advisory or project-matched, and why).
 */
public final class JoustShowStatusAction extends JoustCommandAction {
    @Override
    String title() {
        return "Asking the JOUST language server";
    }

    @Override
    void perform(JoustLanguageServerApi server, Context ctx) {
        var s = JoustStatusCache.getInstance(ctx.project()).forUri(ctx.uri());
        if (s == null) {
            JoustNotifications.show(ctx.project(), "JOUST compiler and mode",
                    "The language server has not reported on this file yet. Try again once its diagnostics appear.",
                    NotificationType.INFORMATION);
            return;
        }
        String text = "Compiler: JOUST " + s.compiler() + " (" + s.source() + ")\nMode: " + s.mode()
                + (s.reason().isEmpty() ? "" : " (" + s.reason() + ")") + "\nServer: " + s.server() + "\nState: " + s.state()
                + "\n\nIntelliAda runs joust-lsp in advisory mode: it analyses with the compiler bundled in your joust-lsp "
                + "and runs no project code (ADR-004 D5).";
        JoustNotifications.show(ctx.project(), "JOUST compiler and mode", PlainText.clean(text, 1_000), NotificationType.INFORMATION);
    }
}
