package com.bloxbean.intelliada.idea.joust.project.actions;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbiDialogUi;
import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.core.JoustLaunchPlanner;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.lsp.JoustNotifications;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots;
import com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.bloxbean.intelliada.idea.joust.trust.JoustTrust;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * ADR-004 D8: Review ABI change… for the selected {@code .jsc}, in the module that owns it. The contract is the
 * {@code .jsc}'s qualified name under its contracts directory; the flow itself is {@link JoustAbiReview}.
 */
public final class JoustReviewAbiAction extends JoustProjectAction {

    @Override
    boolean applies(Ctx ctx) {
        if (ctx.file() == null || !ctx.file().getName().endsWith(".jsc")) return false;
        return ctx.gradle() != null ? JoustGradleTasks.getInstance() != null && ctx.gradle().hasJsc() : ctx.npm().cliInstalled();
    }

    @Override
    String unavailable(Ctx ctx) {
        if (ctx.gradle() != null) return gradleUnavailable(ctx);
        return null;
    }

    @Override
    String title(Ctx ctx) {
        return "Review ABI change (" + ctx.moduleName() + ")";
    }

    @Override
    void perform(Ctx ctx, JoustRunGuard guard) throws Exception {
        review(ctx, guard);
    }

    /** Starts the review from a notification, for the file of {@code ctx}, under a guard captured now. */
    void start(Ctx ctx) {
        if (!JoustTrust.isTrusted(ctx.project())) return;
        JoustRunGuard guard = JoustRunGuard.capture(ctx.project());
        new Task.Backgroundable(ctx.project(), title(ctx), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                if (!guard.live() || unavailable(ctx) != null) return;
                try {
                    review(ctx, guard);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    JoustNotifications.show(ctx.project(), title(ctx), String.valueOf(ex.getMessage()), NotificationType.ERROR);
                }
            }
        }.queue();
    }

    private void review(Ctx ctx, JoustRunGuard guard) throws Exception {
        Path base = ctx.layout().base();
        JoustProjectLayout.SourceRoot root = ctx.gradle() != null
                ? new JoustProjectLayout.SourceRoot(ctx.gradle().dir(), ctx.gradle().jscRoot(), ctx.gradle().generatedRoot(), ctx.gradle(), null)
                : ctx.layout().sourceRoots().stream().filter(r -> r.npm() != null && r.npm().dir().equals(ctx.npm().dir())).findFirst()
                        .orElseThrow(() -> new Exception("The npm root of " + ctx.moduleName() + " is not in the project's scan."));
        Path contracts = root.verifiedContracts(base);
        if (contracts == null) throw new Exception("The contracts directory of " + ctx.moduleName() + " is not where its module says.");
        String contract = JoustAbiReview.Target.contractOf(contracts, ctx.fileReal());
        JoustAbiReview.Target target = new JoustAbiReview.Target(ctx.moduleName(), ctx.gradle() != null,
                ctx.gradle() != null ? ctx.linked().gradlePath() : null, ctx.dir(), contracts, ctx.fileReal(), contract);
        JoustAbiReview.Runner runner = ctx.gradle() != null ? gradleRunner(ctx, guard, title(ctx)) : npmRunner(ctx, guard, title(ctx));
        Path tmp = privateTmp();
        if (tmp == null) throw new Exception("IntelliAda's temporary directory is not usable (it must be outside every open project).");
        new JoustAbiReview(target, () -> JoustProjectLayout.scan(base, JoustPaths.Flavor.current()), runner,
                new JoustAbiDialogUi(ctx.project()), guard::live, tmp, () -> start(ctx)).run();
    }

    /** IntelliAda's own temporary directory for ABI exchanges, verified outside every open project (§12 E12). */
    static Path privateTmp() {
        Path dir = JoustLaunchPlanner.verifiedStorage(Path.of(PathManager.getSystemPath(), "intelliada", "joust-abi"),
                JoustForbiddenRoots.ofOpenProjects(), JoustPaths.Flavor.current());
        if (dir == null) return null;
        try {
            java.nio.file.Files.setPosixFilePermissions(dir, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException | java.io.IOException ignored) {
            // not a POSIX file system: the IDE's system directory is the user's own
        }
        return dir;
    }
}
