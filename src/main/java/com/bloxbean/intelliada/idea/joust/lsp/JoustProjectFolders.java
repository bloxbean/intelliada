package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.core.JoustWorkspaceFolders;
import com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.eclipse.lsp4j.WorkspaceFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** I25: the project's top-level content roots whose real paths lie inside its base directory, as LSP workspace folders. */
public final class JoustProjectFolders {

    private JoustProjectFolders() {
    }

    public static List<WorkspaceFolder> confined(Project project) {
        Path base = JoustForbiddenRoots.baseReal(project);
        if (base == null) return List.of();
        List<Path> candidates = new ArrayList<>();
        VirtualFile[] roots = ReadAction.compute(() -> project.isDisposed() ? VirtualFile.EMPTY_ARRAY
                : ProjectRootManager.getInstance(project).getContentRoots());
        for (VirtualFile r : roots) {
            if (!r.isInLocalFileSystem()) continue;
            try {
                candidates.add(Path.of(r.getPath()).toRealPath());
            } catch (IOException | RuntimeException ignored) {
                // a root that does not resolve is not a workspace folder
            }
        }
        List<WorkspaceFolder> out = new ArrayList<>();
        for (Path p : JoustWorkspaceFolders.confine(candidates, base)) {
            String name = p.getFileName() == null ? p.toString() : p.getFileName().toString();
            out.add(new WorkspaceFolder(p.toUri().toString(), name));
        }
        return out;
    }
}
