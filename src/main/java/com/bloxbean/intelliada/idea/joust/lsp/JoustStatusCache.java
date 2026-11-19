package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last {@code joust/status} joust-lsp pushed for each open .jsc file, reduced to short labels (I15): the compiler
 * version, {@code bundled} or {@code project}, and {@code advisory} or {@code project-matched}.
 */
@Service(Service.Level.PROJECT)
public final class JoustStatusCache {

    static final int MAX_FILES = 500;

    /** What the status bar and Show compiler and mode display. All fields are {@link PlainText#label} output. */
    public record Status(String compiler, String source, String mode, String reason, String server, String state) {
        public String summary() {
            String m = mode.isEmpty() ? "advisory" : mode;
            return "JOUST " + (compiler.isEmpty() ? "" : compiler + " ") + "(" + (source.isEmpty() ? "bundled" : source) + ", " + m + ")";
        }
    }

    private final Project project;
    private final Map<String, Status> byUri = new ConcurrentHashMap<>();

    public JoustStatusCache(Project project) {
        this.project = project;
    }

    public static JoustStatusCache getInstance(Project project) {
        return project.getService(JoustStatusCache.class);
    }

    public Status forUri(String uri) {
        return uri == null ? null : byUri.get(uri);
    }

    public void update(Map<String, Object> json) {
        if (json == null || !(json.get("uri") instanceof String uri) || uri.length() > 4096) return;
        if (byUri.size() >= MAX_FILES && !byUri.containsKey(uri)) byUri.clear();
        byUri.put(uri, from(json));
        var bar = project.isDisposed() ? null : WindowManager.getInstance().getStatusBar(project);
        if (bar != null) bar.updateWidget(JoustStatusBarWidgetFactory.ID);
    }

    public void clear() {
        byUri.clear();
    }

    public static Status from(Map<String, Object> json) {
        return new Status(str(json, "compiler"), str(json, "source"), str(json, "mode"), str(json, "reason"),
                str(json, "server"), str(json, "state"));
    }

    private static String str(Map<String, Object> o, String k) {
        if (o == null) return "";
        Object v = o.get(k);
        return v instanceof String || v instanceof Number || v instanceof Boolean ? PlainText.label(String.valueOf(v), 80) : "";
    }
}
