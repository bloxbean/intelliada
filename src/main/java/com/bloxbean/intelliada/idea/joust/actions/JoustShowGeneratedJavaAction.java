package com.bloxbean.intelliada.idea.joust.actions;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.editor.JoustServerAccess;
import com.bloxbean.intelliada.idea.joust.lsp.JoustLanguageServerApi;
import com.bloxbean.intelliada.idea.joust.lsp.JoustNotifications;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.testFramework.LightVirtualFile;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * JOUST: Show generated Java ({@code joust/generatedJava}, then {@code joust/generatedContent}): opened as a read-only
 * plain-text light virtual file, never written to disk and never rendered as Javadoc (M1-SEC-4).
 */
public final class JoustShowGeneratedJavaAction extends JoustCommandAction {

    static final int MAX_CHARS = 2 * 1024 * 1024;
    private static final Pattern JAVA_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{0,200}\\.java");

    @Override
    String title() {
        return "Lowering .jsc to Java";
    }

    @Override
    void perform(JoustLanguageServerApi server, Context ctx) {
        Map<String, Object> g = JoustServerAccess.await(server.joustGeneratedJava(Map.of("uri", ctx.uri(), "position", ctx.position())), 60);
        if (g == null || g.containsKey("error") || !(g.get("id") instanceof String)) {
            fail(ctx, g == null ? "The language server did not answer." : str(g, "error"));
            return;
        }
        Map<String, Object> c = JoustServerAccess.await(server.joustGeneratedContent(Map.of("id", str(g, "id"))));
        if (c == null || c.containsKey("error") || !(c.get("text") instanceof String)) {
            fail(ctx, c == null ? "The language server did not answer." : str(c, "error"));
            return;
        }
        String text = str(c, "text");
        if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS);
        String name = fileName(str(g, "javaFile"));
        int line = g.get("line") instanceof Number n ? (int) Math.max(0, Math.min(n.longValue(), 10_000_000)) : 0;
        // Plain text, not Java: Quick Documentation would render the server's doc comments as HTML (M1-SEC-4, I15).
        LightVirtualFile file = new LightVirtualFile(name, com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE, text);
        file.setWritable(false);
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!ctx.project().isDisposed()) new OpenFileDescriptor(ctx.project(), file, line, 0).navigate(true);
        });
    }

    static String fileName(String javaFile) {
        String n = javaFile == null ? "" : javaFile;
        int cut = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (cut >= 0) n = n.substring(cut + 1);
        return JAVA_NAME.matcher(n).matches() ? n : "Generated.java";
    }

    private static void fail(Context ctx, String why) {
        JoustNotifications.show(ctx.project(), "Generated Java is unavailable", PlainText.clean(why, 500), NotificationType.WARNING);
    }

    private static String str(Map<String, Object> o, String k) {
        Object v = o.get(k);
        return v instanceof String s ? s : "";
    }
}
