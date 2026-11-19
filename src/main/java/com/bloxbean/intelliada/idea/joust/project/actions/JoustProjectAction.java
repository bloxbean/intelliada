package com.bloxbean.intelliada.idea.joust.project.actions;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbi;
import com.bloxbean.intelliada.idea.joust.abi.JoustAbiDialogUi;
import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustRealPaths;
import com.bloxbean.intelliada.idea.joust.lsp.JoustNotifications;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectService;
import com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks;
import com.bloxbean.intelliada.idea.joust.tasks.JoustNpmTasks;
import com.bloxbean.intelliada.idea.joust.trust.JoustTrust;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * ADR-004 D7: a JOUST project action. Each one runs the project's own toolchain (its Gradle build through IntelliJ, or
 * its npm CLI), so it is enabled only when the project is trusted and the user invoked it (D1, I2); it is shown
 * disabled, with the reason, in an untrusted project.
 */
public abstract class JoustProjectAction extends AnAction {

    /** Where the action applies. Exactly one of {@code gradle} and {@code npm} is set. */
    public record Ctx(Project project, JoustProjectLayout layout, JoustProjectLayout.GradleModule gradle, JoustProjectLayout.NpmRoot npm,
                      VirtualFile file, Path fileReal) {
        public String moduleName() {
            Path base = layout.base();
            Path dir = gradle != null ? gradle.dir() : npm.dir();
            String rel = base.relativize(dir).toString();
            return rel.isEmpty() ? base.getFileName().toString() : rel;
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Ctx ctx = context(e.getProject(), e.getData(CommonDataKeys.VIRTUAL_FILE));
        boolean visible = ctx != null && applies(ctx);
        e.getPresentation().setVisible(visible);
        boolean trusted = visible && JoustTrust.isTrusted(ctx.project());
        e.getPresentation().setEnabled(trusted);
        if (visible && !trusted) e.getPresentation().setDescription("Trust the project to run its build (ADR-004 D1)");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Ctx ctx = context(e.getProject(), e.getData(CommonDataKeys.VIRTUAL_FILE));
        if (ctx == null || !applies(ctx) || !JoustTrust.isTrusted(ctx.project())) return;
        if (!confirm(ctx)) return;
        new Task.Backgroundable(ctx.project(), title(ctx), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                if (!JoustTrust.isTrusted(ctx.project())) return;   // trust lost in the meantime (I20)
                try {
                    perform(ctx);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    JoustNotifications.show(ctx.project(), title(ctx), String.valueOf(ex.getMessage()), NotificationType.ERROR);
                }
            }
        }.queue();
    }

    abstract boolean applies(Ctx ctx);

    abstract String title(Ctx ctx);

    /** A modal before running, where the action writes project files (accept the lock). */
    boolean confirm(Ctx ctx) {
        return true;
    }

    abstract void perform(Ctx ctx) throws Exception;

    /** The JOUST module or npm root that owns the file, from the cached layout. */
    public static Ctx context(Project project, VirtualFile file) {
        if (project == null || project.isDisposed() || file == null || !file.isInLocalFileSystem()) return null;
        JoustProjectLayout layout = JoustProjectService.getInstance(project).layout();
        if (layout == null || !layout.isJoust()) return null;
        Path real;
        try {
            real = file.toNioPath().toRealPath();
        } catch (java.io.IOException | RuntimeException ex) {
            return null;
        }
        JoustProjectLayout.GradleModule g = null;
        for (var m : layout.gradleModules()) {
            if (JoustRealPaths.isInside(real, m.dir()) && (g == null || m.dir().getNameCount() > g.dir().getNameCount())) g = m;
        }
        if (g != null) return new Ctx(project, layout, g, null, file, real);
        JoustProjectLayout.NpmRoot n = null;
        for (var r : layout.npmRoots()) {
            if (JoustRealPaths.isInside(real, r.dir()) && (n == null || r.dir().getNameCount() > n.dir().getNameCount())) n = r;
        }
        return n == null ? null : new Ctx(project, layout, null, n, file, real);
    }

    // ------------------------------------------------------------------ shared running

    /** Runs Gradle tasks on the module's linked build; null (after telling the user) when it is not linked. */
    static JoustAbiReview.Runner gradleRunner(Ctx ctx, String title) {
        JoustGradleTasks gradle = JoustGradleTasks.getInstance();
        if (gradle == null) return null;
        JoustGradleTasks.Linked linked = gradle.linked(ctx.project(), ctx.gradle().dir());
        if (linked == null) {
            JoustNotifications.show(ctx.project(), title, "Link the Gradle build first: " + ctx.moduleName() + " is not part of a linked Gradle project.",
                    NotificationType.WARNING);
            return null;
        }
        return args -> gradle.run(ctx.project(), linked.rootDir(), args, title);
    }

    static String gradlePath(Ctx ctx) {
        JoustGradleTasks gradle = JoustGradleTasks.getInstance();
        var linked = gradle == null ? null : gradle.linked(ctx.project(), ctx.gradle().dir());
        return linked == null ? null : linked.gradlePath();
    }

    static JoustAbiReview.Runner npmRunner(Ctx ctx) {
        return args -> {
            try {
                return JoustNpmTasks.run(ctx.project(), ctx.npm(), args);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                return new JoustAbiReview.Result(false, String.valueOf(e.getMessage()), false);
            }
        };
    }

    /** D8 step 1: an ABI failure in build output becomes a notification naming the contract and its module. */
    static void notifyAbiFailures(Ctx ctx, String output) {
        for (JoustAbi.Failure f : JoustAbi.failures(output)) notifyAbiFailure(ctx, f);
    }

    /** The notification for one failure, with Review ABI change…. */
    public static void notifyAbiFailure(Ctx ctx, JoustAbi.Failure f) {
        {
            var n = JoustNotifications.create("ABI change in " + f.contract(), f.code() + ": the datum or a redeemer encoding of "
                    + f.contract() + " in " + ctx.moduleName() + " changed. Deployed instances depend on it.", NotificationType.WARNING);
            n.addAction(NotificationAction.createSimpleExpiring("Review ABI change…", () -> new JoustReviewAbiAction().start(ctx, f.contract())));
            n.notify(ctx.project());
        }
    }

    static void reportResult(Ctx ctx, String title, JoustAbiReview.Result r) {
        if (r.cancelled()) return;
        // Gradle runs are seen by JoustAbiFailureListener, like any other Gradle run; npm runs are reported here.
        if (ctx.npm() != null) notifyAbiFailures(ctx, r.output());
        JoustNotifications.show(ctx.project(), title, r.ok() ? "Done." : "Failed. See the build output.",
                r.ok() ? NotificationType.INFORMATION : NotificationType.WARNING);
    }

    static List<String> list(String... a) {
        return List.of(a);
    }

    static JoustPaths.Flavor flavor() {
        return JoustPaths.Flavor.current();
    }
}
