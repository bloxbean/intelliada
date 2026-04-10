package com.bloxbean.intelliada.idea.julc.module.pkg;

import com.bloxbean.intelliada.idea.julc.configuration.service.JulcProjectState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Project service that detects julc project type (basic, gradle, maven).
 */
public class JulcTomlService {
    private static final Logger LOG = Logger.getInstance(JulcTomlService.class);

    public static final String JULC_TOML = "julc.toml";

    private final Project project;

    public JulcTomlService(Project project) {
        this.project = project;
    }

    public static JulcTomlService getInstance(Project project) {
        return project.getService(JulcTomlService.class);
    }

    /**
     * Returns true if this project is any kind of julc project (basic, gradle, or maven).
     */
    public boolean isJulcProject() {
        return isBasicJulcProject() || isGradleJulcProject() || isMavenJulcProject();
    }

    /**
     * Returns true if a julc.toml file exists at project root (basic julc project).
     */
    public boolean isBasicJulcProject() {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) return false;
        return baseDir.findChild(JULC_TOML) != null;
    }

    /**
     * Returns true if this is a Gradle project with julc dependencies.
     */
    public boolean isGradleJulcProject() {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) return false;

        VirtualFile buildGradle = baseDir.findChild("build.gradle");
        if (buildGradle == null) {
            buildGradle = baseDir.findChild("build.gradle.kts");
        }
        if (buildGradle == null) return false;

        return containsJulcReference(buildGradle);
    }

    /**
     * Returns true if this is a Maven project with julc dependencies.
     */
    public boolean isMavenJulcProject() {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) return false;

        VirtualFile pomXml = baseDir.findChild("pom.xml");
        if (pomXml == null) return false;

        return containsJulcReference(pomXml);
    }

    /**
     * Detects the project type for configuration purposes.
     */
    public JulcProjectState.ProjectType detectProjectType() {
        if (isGradleJulcProject()) return JulcProjectState.ProjectType.gradle;
        if (isMavenJulcProject()) return JulcProjectState.ProjectType.maven;
        if (isBasicJulcProject()) return JulcProjectState.ProjectType.basic;
        return null;
    }

    private boolean containsJulcReference(VirtualFile file) {
        try {
            String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            return content.contains("julc-stdlib")
                    || content.contains("julc-annotation-processor")
                    || content.contains("com.bloxbean.cardano.julc")
                    || content.contains("julc-compiler");
        } catch (IOException e) {
            LOG.warn("Could not read " + file.getPath(), e);
            return false;
        }
    }
}
