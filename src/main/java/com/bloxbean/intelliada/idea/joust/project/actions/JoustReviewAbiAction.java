package com.bloxbean.intelliada.idea.joust.project.actions;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbi;
import com.bloxbean.intelliada.idea.joust.abi.JoustAbiDialogUi;
import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.lsp.JoustNotifications;
import com.bloxbean.intelliada.idea.joust.trust.JoustTrust;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import org.jetbrains.annotations.NotNull;

/**
 * ADR-004 D8: Review ABI change… for the contract of a {@code .jsc} file (its file name), in the module that owns it.
 * The flow itself is {@link JoustAbiReview}.
 */
public final class JoustReviewAbiAction extends JoustProjectAction {

    @Override
    boolean applies(Ctx ctx) {
        return ctx.file().getName().endsWith(".jsc") && (ctx.gradle() != null ? com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks.getInstance() != null
                : ctx.npm().cliInstalled());
    }

    @Override
    String title(Ctx ctx) {
        return "Review ABI change";
    }

    @Override
    void perform(Ctx ctx) throws Exception {
        run(ctx, ctx.file().getNameWithoutExtension());
    }

    /** Starts the review from a notification (the contract named by the failure). */
    void start(Ctx ctx, String contract) {
        new Task.Backgroundable(ctx.project(), title(ctx), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                if (!JoustTrust.isTrusted(ctx.project())) return;
                try {
                    JoustReviewAbiAction.this.run(ctx, contract);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    JoustNotifications.show(ctx.project(), title(ctx), String.valueOf(ex.getMessage()), NotificationType.ERROR);
                }
            }
        }.queue();
    }

    private void run(Ctx ctx, String contract) throws Exception {
        JoustAbi.requireContract(contract);
        JoustAbiReview.Target target;
        JoustAbiReview.Runner runner;
        if (ctx.gradle() != null) {
            runner = gradleRunner(ctx, title(ctx));
            String path = gradlePath(ctx);
            if (runner == null || path == null) return;
            target = new JoustAbiReview.Target(ctx.moduleName(), true, path, ctx.gradle().dir(), ctx.gradle().jscRoot());
        } else {
            runner = npmRunner(ctx);
            target = new JoustAbiReview.Target(ctx.moduleName(), false, null, ctx.npm().dir(), ctx.npm().contractsDir());
        }
        new JoustAbiReview(target, ctx.layout(), runner, new JoustAbiDialogUi(ctx.project())).run(contract);
    }
}
