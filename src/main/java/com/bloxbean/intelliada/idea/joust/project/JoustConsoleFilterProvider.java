package com.bloxbean.intelliada.idea.joust.project;

import com.bloxbean.intelliada.idea.joust.editor.JoustSafeOpen;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ADR-004 D7, I18: JOUST diagnostic lines in IntelliJ consoles (Gradle runs, the npm CLI) become links to the
 * {@code .jsc} position, only for files whose real path lies inside a contracts directory; the target is checked again
 * when the link is clicked ({@link JoustSafeOpen}).
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
            HyperlinkInfo info = p -> {
                VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(link.target());
                if (vf != null) JoustSafeOpen.open(p, vf, link.line() - 1, link.column() - 1, true);
            };
            return new Result(offset + link.start(), offset + link.end(), info);
        }
    }
}
