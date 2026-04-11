package com.bloxbean.intelliada.idea.julc.module.project;

import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Detects julc projects on open and configures source roots.
 */
public class JulcProjectOpenProcessor extends ProjectOpenProcessor {
    private static final Logger LOG = Logger.getInstance(JulcProjectOpenProcessor.class);

    @Override
    public @NotNull String getName() {
        return "julc";
    }

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }

    @Override
    public boolean canOpenProject(@NotNull VirtualFile file) {
        VirtualFile dir = file.isDirectory() ? file : file.getParent();
        if (dir == null) return false;

        // Basic julc project detection: julc.toml exists
        VirtualFile julcToml = dir.findChild(JulcTomlService.JULC_TOML);
        return julcToml != null;
    }

    @Override
    public @Nullable Project doOpenProject(@NotNull VirtualFile virtualFile, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
        VirtualFile dir = virtualFile.isDirectory() ? virtualFile : virtualFile.getParent();
        if (dir == null) return null;

        Project project = ProjectUtil.openOrImport(dir.toNioPath(), projectToClose, forceOpenInNewFrame);
        if (project == null) return null;

        configureSourceRoots(project, dir);
        return project;
    }

    private void configureSourceRoots(Project project, VirtualFile projectDir) {
        try {
            var modules = ModuleRootManager.getInstance(
                    com.intellij.openapi.module.ModuleManager.getInstance(project).getModules()[0]
            );
            ModifiableRootModel model = modules.getModifiableModel();

            ContentEntry[] entries = model.getContentEntries();
            if (entries.length > 0) {
                ContentEntry entry = entries[0];

                // Mark src/ as source root for basic julc projects
                VirtualFile srcDir = projectDir.findChild("src");
                if (srcDir != null) {
                    entry.addSourceFolder(srcDir, false);
                }

                // Mark test/ as test root
                VirtualFile testDir = projectDir.findChild("test");
                if (testDir != null) {
                    entry.addSourceFolder(testDir, true);
                }

                // Exclude build/ directory
                VirtualFile buildDir = projectDir.findChild("build");
                if (buildDir != null) {
                    entry.addExcludeFolder(buildDir);
                }
            }

            model.commit();
        } catch (Exception e) {
            LOG.warn("Could not configure source roots for julc project", e);
        }
    }
}
