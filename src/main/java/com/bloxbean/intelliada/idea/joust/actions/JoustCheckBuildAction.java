package com.bloxbean.intelliada.idea.joust.actions;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.editor.JoustServerAccess;
import com.bloxbean.intelliada.idea.joust.lsp.JoustLanguageServerApi;
import com.bloxbean.intelliada.idea.joust.lsp.JoustNotifications;
import com.intellij.notification.NotificationType;

import java.util.Map;

/**
 * JOUST: Check build (in memory) ({@code joust/checkBuild}): the server's capped in-memory build on its bundled
 * compiler; it writes nothing. Its diagnostics arrive as ordinary diagnostics.
 */
public final class JoustCheckBuildAction extends JoustCommandAction {
    @Override
    String title() {
        return "Checking the JOUST build in memory";
    }

    @Override
    void perform(JoustLanguageServerApi server, Context ctx) {
        Map<String, Object> r = JoustServerAccess.await(server.joustCheckBuild(Map.of("uri", ctx.uri())));
        if (r == null) {
            JoustNotifications.show(ctx.project(), "JOUST build check", "The language server did not answer.", NotificationType.WARNING);
        } else if (r.containsKey("error")) {
            JoustNotifications.show(ctx.project(), "JOUST build check", PlainText.clean(String.valueOf(r.get("error")), 500), NotificationType.WARNING);
        } else {
            boolean ok = Boolean.TRUE.equals(r.get("ok"));
            String errors = r.get("errors") instanceof Number n ? String.valueOf(n.longValue()) : "?";
            String contracts = r.get("contracts") instanceof Number n ? String.valueOf(n.longValue()) : "?";
            JoustNotifications.show(ctx.project(), "JOUST build check",
                    (ok ? "The build succeeds" : "The build fails") + " in memory: " + errors + " error(s), " + contracts + " contract(s).",
                    ok ? NotificationType.INFORMATION : NotificationType.WARNING);
        }
    }
}
