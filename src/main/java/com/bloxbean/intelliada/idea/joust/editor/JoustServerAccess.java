package com.bloxbean.intelliada.idea.joust.editor;

import com.bloxbean.intelliada.idea.joust.core.JoustConstants;
import com.bloxbean.intelliada.idea.joust.core.JoustLocationPolicy;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.lsp.JoustLanguageServerApi;
import com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServerManager;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * IntelliAda's own requests to joust-lsp, for the features LSP4IJ offers no result hook for (ADR-004 D3a items 6 and 7):
 * hover, definition, references, and the read-only commands.
 */
public final class JoustServerAccess {

    static final long TIMEOUT_SECONDS = 10;

    private JoustServerAccess() {
    }

    /** A location the user may open: inside the project, by real path (D3a item 7). */
    public record Target(VirtualFile file, Range range) {
    }

    public static boolean isJsc(VirtualFile file) {
        return file != null && file.isInLocalFileSystem() && JoustConstants.isJscName(file.getName());
    }

    /** The JOUST server of the project, or {@code null} when it is not configured or does not start. */
    public static JoustLanguageServerApi server(Project project) {
        CompletableFuture<LanguageServerItem> f = LanguageServerManager.getInstance(project).getLanguageServer(JoustConstants.SERVER_ID);
        LanguageServerItem item = await(f);
        if (item == null) return null;
        return item.getServer() instanceof JoustLanguageServerApi api ? api : null;
    }

    public static String uriOf(VirtualFile file) {
        return LSPIJUtils.toUriAsString(file);
    }

    /** Waits for a server answer, with cancellation and a timeout; {@code null} on failure. */
    public static <T> T await(CompletableFuture<T> f) {
        if (f == null) return null;
        try {
            return ProgressIndicatorUtils.awaitWithCheckCanceled(f.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (com.intellij.openapi.progress.ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Keeps only locations that pass {@link JoustLocationPolicy}; everything else is dropped before any UI sees it. */
    public static List<Target> confine(Project project, List<Location> locations) {
        List<Target> out = new ArrayList<>();
        if (locations == null) return out;
        Path base = JoustForbiddenRoots.baseReal(project);
        for (Location l : locations) {
            if (l == null) continue;
            Path real = JoustLocationPolicy.allowedTarget(l.getUri(), base, JoustPaths.Flavor.forAllowDecisions());
            if (real == null) continue;
            VirtualFile vf = LocalFileSystem.getInstance().findFileByNioFile(real);
            if (vf != null) out.add(new Target(vf, l.getRange()));
            if (out.size() >= 500) break;
        }
        return out;
    }

    /** Flattens a definition answer to locations, with a link's selection range as the target. */
    public static List<Location> locations(Either<List<? extends Location>, List<? extends LocationLink>> either) {
        List<Location> out = new ArrayList<>();
        if (either == null) return out;
        if (either.isLeft()) {
            if (either.getLeft() != null) out.addAll(either.getLeft());
        } else if (either.getRight() != null) {
            Function<LocationLink, Location> f = l -> new Location(l.getTargetUri(),
                    l.getTargetSelectionRange() != null ? l.getTargetSelectionRange() : l.getTargetRange());
            for (LocationLink l : either.getRight()) out.add(f.apply(l));
        }
        return out;
    }
}
