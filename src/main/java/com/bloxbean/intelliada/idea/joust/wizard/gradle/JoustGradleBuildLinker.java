package com.bloxbean.intelliada.idea.joust.wizard.gradle;

import com.bloxbean.intelliada.idea.joust.wizard.JoustBuildLinker;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineStart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.project.open.GradleProjectImportUtil;

/**
 * ADR-004 D9: links a wizard-created Gradle build through IntelliJ's own Gradle integration
 * ({@code GradleProjectImportUtil.linkAndSyncGradleProject}, a suspending function, launched in a coroutine scope that
 * belongs to the project, so it ends when the project closes). Loaded only with the optional {@code com.intellij.gradle}
 * dependency ({@code intelliada-gradle.xml}). The sync that follows is IntelliJ's behaviour in a trusted project (T1);
 * the caller checks trust first (D1).
 */
public final class JoustGradleBuildLinker implements JoustBuildLinker {

    @Override
    public void linkGradle(@NotNull Project project, @NotNull String buildRoot) {
        CoroutineScope scope = project.getService(LinkScope.class).scope;
        BuildersKt.launch(scope, EmptyCoroutineContext.INSTANCE, CoroutineStart.DEFAULT,
                (cs, continuation) -> GradleProjectImportUtil.linkAndSyncGradleProject(project, buildRoot, continuation));
    }

    /** The project's coroutine scope, injected by the platform into this light service. */
    @Service(Service.Level.PROJECT)
    static final class LinkScope {
        final CoroutineScope scope;

        LinkScope(@NotNull CoroutineScope scope) {
            this.scope = scope;
        }
    }
}
