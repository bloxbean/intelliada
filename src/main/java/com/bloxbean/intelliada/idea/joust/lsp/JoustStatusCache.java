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

        /** The summary, plus the project's declaration unless it is an exact pin equal to the compiler (D5's version skew). */
        public String summary(com.bloxbean.intelliada.idea.joust.core.JoustDeclaredVersion.Declared declared) {
            if (declared == null || (declared.exact() && declared.spec().equals(compiler))) return summary();
            return summary() + "; " + declared.describe();
        }
    }

    private final Project project;
    private final Map<String, Status> byUri = new ConcurrentHashMap<>();

    public JoustStatusCache(Project project) {
        this.project = project;
        // A declaration changes when a build file or package.json does (M1-CDX-15).
        project.getMessageBus().connect().subscribe(com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES,
                new com.intellij.openapi.vfs.newvfs.BulkFileListener() {
                    @Override
                    public void after(java.util.List<? extends com.intellij.openapi.vfs.newvfs.events.VFileEvent> events) {
                        for (var e : events) {
                            String path = e.getPath();
                            String name = path.substring(path.lastIndexOf('/') + 1);
                            if (name.equals("package.json") || name.startsWith("build.gradle") || name.startsWith("settings.gradle")) {
                                invalidateDeclarations();
                                return;
                            }
                        }
                    }
                });
    }

    /** Forgets every cached declaration and refreshes the widget. */
    public void invalidateDeclarations() {
        declared.clear();
        refresh();
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

    /** Clears every status (the server stopped or restarted) and refreshes the widget (M1-CDX-15, M1-DES-12). */
    public void clear() {
        byUri.clear();
        declared.clear();
        refresh();
    }

    private final Map<String, java.util.Optional<com.bloxbean.intelliada.idea.joust.core.JoustDeclaredVersion.Declared>> declared =
            new ConcurrentHashMap<>();

    /**
     * The project's declaration for a file, from a cache filled off the EDT (M1-DES-16); {@code null} until computed.
     * The first call schedules the read and refreshes the widget when it is done.
     */
    public com.bloxbean.intelliada.idea.joust.core.JoustDeclaredVersion.Declared declaredFor(String uri, java.nio.file.Path file) {
        var cached = declared.get(uri);
        if (cached != null) return cached.orElse(null);
        if (declared.size() >= MAX_FILES) declared.clear();
        declared.put(uri, java.util.Optional.empty());
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                var base = com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots.baseReal(project);
                var d = com.bloxbean.intelliada.idea.joust.core.JoustDeclaredVersion.declared(file.toRealPath(), base);
                declared.put(uri, java.util.Optional.ofNullable(d));
                refresh();
            } catch (java.io.IOException | RuntimeException ignored) {
                // no declaration
            }
        });
        return null;
    }

    private void refresh() {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
            var bar = project.isDisposed() ? null : WindowManager.getInstance().getStatusBar(project);
            if (bar != null) bar.updateWidget(JoustStatusBarWidgetFactory.ID);
        }, project.getDisposed());
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
