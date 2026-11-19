package com.bloxbean.intelliada.idea.joust.wizard.gradle;

import com.bloxbean.intelliada.idea.joust.wizard.JoustBuildLinker;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.project.open.GradleProjectImportUtil;

/**
 * ADR-004 D9: links a wizard-created Gradle build through IntelliJ's own Gradle integration
 * ({@code GradleProjectImportUtil.linkAndRefreshGradleProject}). Loaded only with the optional {@code com.intellij.gradle}
 * dependency ({@code intelliada-gradle.xml}). The import that follows is IntelliJ's behaviour in a trusted project
 * (T1); the caller checks trust first (D1).
 */
public final class JoustGradleBuildLinker implements JoustBuildLinker {

    @Override
    public void linkGradle(@NotNull Project project, @NotNull String buildRoot) {
        GradleProjectImportUtil.linkAndRefreshGradleProject(buildRoot, project);
    }
}
