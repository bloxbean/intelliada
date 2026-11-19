package com.bloxbean.intelliada.idea.joust.project;

import com.bloxbean.intelliada.idea.joust.core.JoustRealPaths;
import com.bloxbean.intelliada.idea.joust.editor.JoustSafeOpen;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * ADR-004 D7, I18: JOUST diagnostic lines in IntelliJ consoles (Gradle runs, the npm CLI) become links to the
 * {@code .jsc} position, only for files whose real path lies inside a verified contracts directory. A console started
 * for one npm root resolves within that root only (M2-CDX-14). When a link is clicked, the line is resolved again (off
 * the EDT) and must give the same real file, and on the EDT, right before opening, the file's real path is checked once
 * more against that file and its verified root (M2-CDX-9); {@link JoustSafeOpen} then checks the project.
 */
public final class JoustConsoleFilterProvider implements ConsoleFilterProvider {

    @Override
    public Filter @NotNull [] getDefaultFilters(@NotNull Project project) {
        return new Filter[]{new JoustDiagnosticFilter(project, null)};
    }

    /** The filter itself; {@code ownerDirs} limits it to the source roots of those owners (null: every root). */
    public static final class JoustDiagnosticFilter implements Filter {
        private final Project project;
        private final List<Path> ownerDirs;

        public JoustDiagnosticFilter(Project project) {
            this(project, null);
        }

        public JoustDiagnosticFilter(Project project, List<Path> ownerDirs) {
            this.project = project;
            this.ownerDirs = ownerDirs;
        }

        @Override
        public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
            if (!line.contains("[JSC") || project.isDisposed()) return null;
            JoustDiagnosticLinks.Link link = resolve(project, line, ownerDirs);
            if (link == null) return null;
            int offset = entireLength - line.length();
            return new Result(offset + link.start(), offset + link.end(), new JoustLinkInfo(line, link.target(), ownerDirs));
        }
    }

    static JoustDiagnosticLinks.Link resolve(Project project, String line, List<Path> ownerDirs) {
        JoustProjectLayout layout = JoustProjectService.getInstance(project).layout();
        if (layout == null) return null;
        List<JoustProjectLayout.SourceRoot> roots = layout.sourceRoots();
        if (ownerDirs != null) roots = roots.stream().filter(r -> ownerDirs.contains(r.ownerDir())).toList();
        return JoustDiagnosticLinks.find(line, roots, layout.base());
    }

    /** Re-resolves at click time and opens only the same real file, still inside its verified root. */
    public record JoustLinkInfo(String line, Path target, List<Path> ownerDirs) implements HyperlinkInfo {
        @Override
        public void navigate(@NotNull Project p) {
            open(p);
        }

        /** Completes with true when the file was opened. */
        public CompletableFuture<Boolean> open(Project p) {
            return open(p, () -> {
            });
        }

        /** As {@link #open(Project)}; {@code betweenResolveAndOpen} runs after the pooled resolution (a test seam). */
        public CompletableFuture<Boolean> open(Project p, Runnable betweenResolveAndOpen) {
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    JoustDiagnosticLinks.Link again = p.isDisposed() ? null : resolve(p, line, ownerDirs);
                    if (again == null || again.root() == null || !again.target().toString().equals(target.toString())) {
                        done.complete(false);
                        return;
                    }
                    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(again.target());
                    betweenResolveAndOpen.run();
                    ApplicationManager.getApplication().invokeLater(() -> done.complete(vf != null && !p.isDisposed()
                            && stillTheSame(again) && JoustSafeOpen.open(p, vf, again.line() - 1, again.column() - 1, true)));
                } catch (RuntimeException e) {
                    done.complete(false);
                }
            });
            return done;
        }

        /** On the EDT, right before opening: the same real file, a regular file, inside the same verified root. */
        static boolean stillTheSame(JoustDiagnosticLinks.Link link) {
            try {
                Path now = link.target().toRealPath();
                Path root = link.root().toRealPath();
                return now.toString().equals(link.target().toString()) && root.toString().equals(link.root().toString())
                        && java.nio.file.Files.isRegularFile(now) && JoustRealPaths.isInside(now, root);
            } catch (java.io.IOException | RuntimeException e) {
                return false;
            }
        }
    }
}
