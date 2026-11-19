package com.bloxbean.intelliada.idea.joust.project;

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
import java.util.concurrent.CompletableFuture;

/**
 * ADR-004 D7, I18: JOUST diagnostic lines in IntelliJ consoles (Gradle runs, the npm CLI) become links to the
 * {@code .jsc} position, only for files whose real path lies inside a verified contracts directory. When the link is
 * clicked, the line is resolved again (off the EDT) against the current layout and must give the same real file
 * (M2-CDX-9); {@link JoustSafeOpen} then checks it once more.
 */
public final class JoustConsoleFilterProvider implements ConsoleFilterProvider {

    @Override
    public Filter @NotNull [] getDefaultFilters(@NotNull Project project) {
        return new Filter[]{new JoustDiagnosticFilter(project)};
    }

    /** The filter itself. */
    public static final class JoustDiagnosticFilter implements Filter {
        private final Project project;

        public JoustDiagnosticFilter(Project project) {
            this.project = project;
        }

        @Override
        public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
            if (!line.contains("[JSC") || project.isDisposed()) return null;
            JoustProjectLayout layout = JoustProjectService.getInstance(project).layout();
            JoustDiagnosticLinks.Link link = JoustDiagnosticLinks.find(line, layout);
            if (link == null) return null;
            int offset = entireLength - line.length();
            return new Result(offset + link.start(), offset + link.end(), new JoustLinkInfo(line, link.target()));
        }
    }

    /** Re-resolves at click time and opens only the same real file. */
    public record JoustLinkInfo(String line, Path target) implements HyperlinkInfo {
        @Override
        public void navigate(@NotNull Project p) {
            open(p);
        }

        /** Completes with true when the file was opened. */
        public CompletableFuture<Boolean> open(Project p) {
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    JoustDiagnosticLinks.Link again = p.isDisposed() ? null
                            : JoustDiagnosticLinks.find(line, JoustProjectService.getInstance(p).layout());
                    if (again == null || !again.target().equals(target)) {
                        done.complete(false);
                        return;
                    }
                    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(again.target());
                    ApplicationManager.getApplication().invokeLater(() -> done.complete(
                            vf != null && !p.isDisposed() && JoustSafeOpen.open(p, vf, again.line() - 1, again.column() - 1, true)));
                } catch (RuntimeException e) {
                    done.complete(false);
                }
            });
            return done;
        }
    }
}
