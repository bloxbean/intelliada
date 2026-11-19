package com.bloxbean.intelliada.idea.joust.wizard;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ADR-004 D9 "Afterwards": links the Gradle build of a project the wizard created. The implementation lives under the
 * <b>optional</b> {@code com.intellij.gradle} dependency ({@code intelliada-gradle.xml}); without the Gradle plugin there
 * is no implementation and the wizard still works, telling the user to link the build by hand.
 */
public interface JoustBuildLinker {

    /** Links (and imports) the Gradle build rooted at {@code buildRoot}. Called on the EDT, for a trusted project only. */
    void linkGradle(@NotNull Project project, @NotNull String buildRoot);

    /** The registered linker, or {@code null} when IntelliJ's Gradle plugin is not present. */
    static @Nullable JoustBuildLinker getInstance() {
        return ApplicationManager.getApplication().getService(JoustBuildLinker.class);
    }
}
