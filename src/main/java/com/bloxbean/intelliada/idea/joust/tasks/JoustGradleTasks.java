package com.bloxbean.intelliada.idea.joust.tasks;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import java.nio.file.Path;
import java.util.List;

/**
 * ADR-004 D7: running a JOUST Gradle task through IntelliJ's Gradle integration (the project's Gradle JVM and wrapper),
 * with freshly built execution settings, never a run configuration from {@code .idea/}. Registered only when IntelliJ's
 * Gradle plugin is present ({@code intelliada-gradle.xml}); without it the Gradle actions are hidden.
 */
public interface JoustGradleTasks {

    /** The service, or null when IntelliJ's Gradle plugin is absent. */
    static JoustGradleTasks getInstance() {
        return ApplicationManager.getApplication().getService(JoustGradleTasks.class);
    }

    /** The linked Gradle build the module directory belongs to, and the module's Gradle path; null if not linked. */
    Linked linked(Project project, Path moduleDir);

    /** A linked module: the root build's directory and the module's project path ({@code ":"} for the root). */
    record Linked(Path rootDir, String gradlePath) {
    }

    /**
     * Runs {@code args} (qualified task names and their options, as a list) on the linked build, blocking until it ends.
     * The task is registered with {@link JoustTaskTracker}, so losing trust or closing the project cancels it.
     */
    JoustAbiReview.Result run(Project project, Path rootDir, List<String> args, String title) throws InterruptedException;
}
