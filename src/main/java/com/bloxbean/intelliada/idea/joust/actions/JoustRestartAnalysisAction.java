package com.bloxbean.intelliada.idea.joust.actions;

import com.bloxbean.intelliada.idea.joust.editor.JoustServerAccess;
import com.bloxbean.intelliada.idea.joust.lsp.JoustLanguageServerApi;
import com.bloxbean.intelliada.idea.joust.lsp.JoustNotifications;
import com.intellij.notification.NotificationType;

import java.util.Map;

/** JOUST: Restart analysis ({@code joust/restartAnalysis}). In M1 the server is advisory, so no project code loads. */
public final class JoustRestartAnalysisAction extends JoustCommandAction {
    @Override
    String title() {
        return "Restarting JOUST analysis";
    }

    @Override
    void perform(JoustLanguageServerApi server, Context ctx) {
        var r = JoustServerAccess.await(server.joustRestartAnalysis(Map.of()));
        JoustNotifications.show(ctx.project(), "JOUST", r == null ? "The analysis did not restart." : "The analysis restarted.",
                r == null ? NotificationType.WARNING : NotificationType.INFORMATION);
    }
}
