package com.bloxbean.intelliada.idea.joust.project;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;

import java.nio.file.Path;
import java.util.List;

/**
 * ADR-004 D7: the project's JOUST layout, read as text and cached; the cache is dropped when a build file,
 * {@code package.json}, {@code joust.config.json} or a directory changes. Never runs a build.
 */
@Service(Service.Level.PROJECT)
public final class JoustProjectService {

    private final Project project;
    private volatile JoustProjectLayout layout;

    public JoustProjectService(Project project) {
        this.project = project;
        project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(List<? extends VFileEvent> events) {
                for (VFileEvent e : events) {
                    if (affectsLayout(e.getPath(), e.getFile() == null || e.getFile().isDirectory())) {
                        layout = null;
                        return;
                    }
                }
            }
        });
    }

    /**
     * Whether a change at {@code path} can change the layout (M2-DES-4): a build or settings file, {@code package.json},
     * {@code joust.config.json}, the project's {@code @julclang/joust}, or a directory outside build output,
     * {@code node_modules} and IDE metadata. A Gradle or npm build writing its outputs does not.
     */
    static boolean affectsLayout(String path, boolean directoryOrGone) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.startsWith("build.gradle") || name.startsWith("settings.gradle") || name.equals("package.json")
                || name.equals("joust.config.json")) {
            return true;
        }
        if (path.contains("/node_modules/@julclang/joust")) return true;
        if (!directoryOrGone) return false;
        return !(path.contains("/build/") || path.endsWith("/build") || path.contains("/node_modules/") || path.contains("/.gradle")
                || path.contains("/.idea") || path.contains("/.git/") || path.contains("/out/"));
    }

    private final java.util.concurrent.atomic.AtomicBoolean computing = new java.util.concurrent.atomic.AtomicBoolean();

    /** Computes the layout on a pooled thread (once at a time), then refreshes editor banners and generated markers. */
    public void computeInBackground() {
        if (!computing.compareAndSet(false, true)) return;
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                layout();
            } finally {
                computing.set(false);
            }
            if (!project.isDisposed()) com.intellij.ui.EditorNotifications.getInstance(project).updateAllNotifications();
        });
    }

    public static JoustProjectService getInstance(Project project) {
        return project.getService(JoustProjectService.class);
    }

    /** The layout; computed off the EDT by the caller's thread (it reads files). Null when the project has no base. */
    public JoustProjectLayout layout() {
        JoustProjectLayout l = layout;
        if (l != null) return l;
        Path base = JoustForbiddenRoots.baseReal(project);
        if (base == null) return null;
        l = JoustProjectLayout.scan(base, JoustPaths.Flavor.current());
        layout = l;
        return l;
    }

    /** The cached layout, or null if it has not been computed (for EDT callers that must not read files). */
    public JoustProjectLayout cachedLayout() {
        return layout;
    }
}
