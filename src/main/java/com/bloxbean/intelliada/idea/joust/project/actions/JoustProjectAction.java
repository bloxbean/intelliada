package com.bloxbean.intelliada.idea.joust.project.actions;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbi;
import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.lsp.JoustNotifications;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectService;
import com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks;
import com.bloxbean.intelliada.idea.joust.tasks.JoustNpmTasks;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
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
 * disabled, with the reason, in an untrusted project. The action is bound to a {@link JoustRunGuard} captured when it
 * is invoked: every launch and every continuation after a dialog checks it (I20).
 */
public abstract class JoustProjectAction extends AnAction {

    /** Where the action applies. Exactly one of {@code gradle} and {@code npm} is set; {@code linked} only for Gradle. */
    public record Ctx(Project project, JoustProjectLayout layout, JoustProjectLayout.GradleModule gradle, JoustProjectLayout.NpmRoot npm,
                      VirtualFile file, Path fileReal, JoustGradleTasks.Linked linked) {
        /** The module's path relative to the project, as plain text safe for titles. */
        public String moduleName() {
            Path base = layout.base();
            Path dir = gradle != null ? gradle.dir() : npm.dir();
            String rel = base.relativize(dir).toString();
            return PlainText.title(rel.isEmpty() ? String.valueOf(base.getFileName()) : rel, 80);
        }

        public Path dir() {
            return gradle != null ? gradle.dir() : npm.dir();
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Ctx ctx = context(e);
        boolean visible = ctx != null && applies(ctx);
        e.getPresentation().setVisible(visible);
        boolean trusted = visible && JoustTrust.isTrusted(ctx.project());
        String unavailable = visible && trusted ? unavailable(ctx) : null;
        e.getPresentation().setEnabled(trusted && unavailable == null);
        if (visible && !trusted) e.getPresentation().setDescription("Trust the project to run its build (ADR-004 D1)");
        else if (unavailable != null) e.getPresentation().setDescription(unavailable);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || !JoustTrust.isTrusted(project)) return;
        JoustRunGuard guard = JoustRunGuard.capture(project);   // bound now, while trusted (M2-CDX-2)
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        Path toolWindowDir = file == null && JoustGradleTasks.getInstance() != null ? JoustGradleTasks.getInstance().selectedModuleDir(e) : null;
        new Task.Backgroundable(project, PlainText.title(getTemplateText(), 120), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                if (!guard.live()) return;
                // The context is resolved here, off the EDT (M2-CDX-17): it reads the layout and the linked build.
                Ctx ctx = context(project, file, toolWindowDir);
                if (ctx == null || !applies(ctx) || unavailable(ctx) != null || !guard.live()) return;
                if (!confirm(ctx) || !guard.live()) return;
                try {
                    perform(ctx, guard);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    JoustNotifications.show(project, title(ctx), String.valueOf(ex.getMessage()), NotificationType.ERROR);
                }
            }
        }.queue();
    }

    abstract boolean applies(Ctx ctx);

    /** Why an applicable action cannot run here (shown as its description), or null. */
    String unavailable(Ctx ctx) {
        return null;
    }

    /** The action's title for this context, safe for HTML tooltips (M2-SEC-1). */
    abstract String title(Ctx ctx);

    /** A modal before running, where the action writes project files (accept the lock). Runs off the EDT; may block. */
    boolean confirm(Ctx ctx) {
        return true;
    }

    abstract void perform(Ctx ctx, JoustRunGuard guard) throws Exception;

    private static Ctx context(AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        Path dir = file == null && JoustGradleTasks.getInstance() != null ? JoustGradleTasks.getInstance().selectedModuleDir(e) : null;
        return context(project, file, dir);
    }

    /** The JOUST owner of the file, or of the module selected in the Gradle tool window; off the EDT. */
    public static Ctx context(Project project, VirtualFile file, Path toolWindowDir) {
        if (project == null || project.isDisposed()) return null;
        Path real;
        if (file != null) {
            if (!file.isInLocalFileSystem()) return null;
            try {
                real = file.toNioPath().toRealPath();
            } catch (java.io.IOException | RuntimeException ex) {
                return null;
            }
        } else if (toolWindowDir != null) {
            try {
                real = toolWindowDir.toRealPath();
            } catch (java.io.IOException | RuntimeException ex) {
                return null;
            }
        } else {
            return null;
        }
        JoustProjectLayout layout = JoustProjectService.getInstance(project).layout();
        if (layout == null || !layout.isJoust()) return null;
        JoustProjectLayout.Owner owner = layout.owner(real);
        if (owner == null) return null;
        JoustGradleTasks.Linked linked = null;
        if (owner.gradle() != null && JoustGradleTasks.getInstance() != null) {
            linked = JoustGradleTasks.getInstance().linked(project, owner.gradle().dir());
        }
        return new Ctx(project, layout, owner.gradle(), owner.npm(), file, real, linked);
    }

    public static Ctx context(Project project, VirtualFile file) {
        return context(project, file, null);
    }

    // ------------------------------------------------------------------ shared running

    /** Gradle tasks on the module's linked build (root and path resolved once, in the context). */
    static JoustAbiReview.Runner gradleRunner(Ctx ctx, JoustRunGuard guard, String title) {
        JoustGradleTasks gradle = JoustGradleTasks.getInstance();
        JoustGradleTasks.Linked linked = ctx.linked();
        return args -> gradle.run(guard, linked.rootDir(), args, title);
    }

    static String gradleUnavailable(Ctx ctx) {
        return ctx.linked() == null ? "Link the Gradle build first: " + ctx.moduleName() + " is not part of a linked Gradle project." : null;
    }

    static JoustAbiReview.Runner npmRunner(Ctx ctx, JoustRunGuard guard, String title) {
        return args -> {
            try {
                return JoustNpmTasks.run(guard, ctx.npm(), args, title);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                return new JoustAbiReview.Result(false, String.valueOf(e.getMessage()), false);
            }
        };
    }

    /**
     * D8 step 1 for npm output (Gradle output is seen by the Gradle listener): one notification per <b>reported
     * source</b>, resolved under the npm root that ran the build, and bound to that file (M2-CDX-20).
     */
    static List<Ctx> notifyAbiFailures(Ctx ctx, String output) {
        List<Ctx> notified = new java.util.ArrayList<>();
        JoustProjectLayout.SourceRoot root = ctx.layout().sourceRoots().stream().filter(r -> r.npm() == ctx.npm()).findFirst().orElse(null);
        if (root == null) return notified;
        java.util.Set<Path> seen = new java.util.HashSet<>();
        for (JoustAbi.Failure f : JoustAbi.failures(output)) {
            Path jsc = com.bloxbean.intelliada.idea.joust.project.JoustDiagnosticLinks.resolveIn(f.jscRel(), root, ctx.layout().base());
            if (jsc == null || !seen.add(jsc)) continue;
            VirtualFile vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jsc);
            Ctx forFile = vf == null ? null : context(ctx.project(), vf);
            if (forFile != null && forFile.npm() != null && forFile.npm().dir().equals(ctx.npm().dir())) {
                notifyAbiFailure(forFile, f);
                notified.add(forFile);
            }
        }
        return notified;
    }

    /** Where the module is, for people: the Gradle project path when linked, else its directory. */
    static String where(Ctx ctx) {
        return ctx.linked() != null ? "Gradle project " + PlainText.title(ctx.linked().gradlePath(), 120) : ctx.moduleName();
    }

    /**
     * The notification for one failure in the module of {@code ctx}, bound to {@code ctx}'s {@code .jsc}. Review ABI
     * change… is offered only when it can run there; otherwise the notification says why (M2-CDX-13).
     */
    public static void notifyAbiFailure(Ctx ctx, JoustAbi.Failure f) {
        String contract = PlainText.title(f.contract(), 120);
        JoustReviewAbiAction review = new JoustReviewAbiAction();
        String why = !review.applies(ctx) ? "Review ABI change… is not available for this file." : review.unavailable(ctx);
        var n = JoustNotifications.create("ABI change in " + contract, f.code() + ": the datum or a redeemer encoding of " + contract
                + " in " + where(ctx) + " changed. Deployed instances depend on it." + (why == null ? "" : "\n" + why),
                NotificationType.WARNING);
        if (why == null) n.addAction(NotificationAction.createSimpleExpiring("Review ABI change…", () -> review.start(ctx)));
        n.notify(ctx.project());
    }

    /** The notification when the owning module of a failure cannot be told (D8 step 2: no action, with the reason). */
    public static void notifyAmbiguousAbiFailure(Project project, JoustAbi.Failure f, List<String> modules) {
        JoustNotifications.show(project, "ABI change in " + PlainText.title(f.contract(), 120), f.code() + ": " + PlainText.title(f.jscRel(), 200)
                + " is in " + modules.size() + " modules (" + PlainText.title(String.join(", ", modules), 400) + "), and the build output"
                + " does not say which failed. Open the .jsc in its module and use Review ABI Change… there.", NotificationType.WARNING);
    }

    static void reportResult(Ctx ctx, String title, JoustAbiReview.Result r) {
        if (r.cancelled()) return;
        if (ctx.npm() != null) notifyAbiFailures(ctx, r.output());
        JoustNotifications.show(ctx.project(), title, r.ok() ? "Done." : "Failed. See the build output.",
                r.ok() ? NotificationType.INFORMATION : NotificationType.WARNING);
    }

    static List<String> list(String... a) {
        return List.of(a);
    }

    /** A yes/no modal on the EDT, from a background thread. */
    static boolean ask(Project project, String text, String title, String yes) {
        java.util.concurrent.atomic.AtomicBoolean ok = new java.util.concurrent.atomic.AtomicBoolean();
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(() -> ok.set(
                com.intellij.openapi.ui.Messages.showYesNoDialog(project, PlainText.html(text), title, yes, "Cancel",
                        com.intellij.openapi.ui.Messages.getWarningIcon()) == com.intellij.openapi.ui.Messages.YES));
        return ok.get();
    }
}
