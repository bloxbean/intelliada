package com.bloxbean.intelliada.idea.joust.project;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/** ADR-004 D7: Java under a JOUST module's {@code build/generated/sources/jsc/java/main} is generated (F13, F19). */
public final class JoustGeneratedSourcesFilter extends GeneratedSourcesFilter {

    @Override
    public boolean isGeneratedSource(@NotNull VirtualFile file, @NotNull Project project) {
        return moduleOf(file, project) != null;
    }

    /** The JOUST Gradle module that generated {@code file}, or null. Uses the cached layout (no file reads on the EDT). */
    static JoustProjectLayout.GradleModule moduleOf(VirtualFile file, Project project) {
        if (project.isDisposed() || !file.isInLocalFileSystem() || !file.getName().endsWith(".java")) return null;
        if (!file.getPath().contains("/build/generated/sources/jsc/")) return null;
        JoustProjectLayout layout = JoustProjectService.getInstance(project).cachedLayout();
        if (layout == null) layout = JoustProjectService.getInstance(project).layout();
        try {
            Path real = file.toNioPath().toRealPath();
            return layout == null ? null : layout.gradleModuleGenerating(real, JoustPaths.Flavor.current());
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }
}
