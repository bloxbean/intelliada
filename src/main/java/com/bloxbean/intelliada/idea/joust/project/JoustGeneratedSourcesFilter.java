package com.bloxbean.intelliada.idea.joust.project;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * ADR-004 D7: Java under a JOUST module's {@code build/generated/sources/jsc/java/main}, or an npm root's
 * {@code build/joust/generated}, is generated (F13, F19). On the EDT only the cached layout is used; when it is not
 * computed yet, it is computed in the background and editors are refreshed (M2-DES-4).
 */
public final class JoustGeneratedSourcesFilter extends GeneratedSourcesFilter {

    @Override
    public boolean isGeneratedSource(@NotNull VirtualFile file, @NotNull Project project) {
        return rootOf(file, project) != null;
    }

    /** The source root whose generated directory holds {@code file}, or null. */
    static JoustProjectLayout.SourceRoot rootOf(VirtualFile file, Project project) {
        if (project.isDisposed() || !file.isInLocalFileSystem() || !file.getName().endsWith(".java")) return null;
        String path = file.getPath();
        if (!path.contains("/build/generated/sources/jsc/") && !path.contains("/build/joust/generated/")) return null;
        JoustProjectService service = JoustProjectService.getInstance(project);
        JoustProjectLayout layout = service.cachedLayout();
        if (layout == null) {
            if (ApplicationManager.getApplication().isDispatchThread()) {
                service.computeInBackground();
                return null;
            }
            layout = service.layout();
        }
        if (layout == null) return null;
        try {
            return layout.rootGenerating(file.toNioPath().toRealPath(), JoustPaths.Flavor.current());
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    /** The Gradle module that generated {@code file}, or null (Gradle only). */
    static JoustProjectLayout.GradleModule moduleOf(VirtualFile file, Project project) {
        JoustProjectLayout.SourceRoot r = rootOf(file, project);
        return r == null ? null : r.gradle();
    }
}
