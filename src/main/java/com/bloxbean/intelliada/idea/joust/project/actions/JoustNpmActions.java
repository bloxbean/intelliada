package com.bloxbean.intelliada.idea.joust.project.actions;

import com.bloxbean.intelliada.idea.joust.core.JoustRealPaths;

/** ADR-004 D7's npm actions: the project's own CLI, {@code joust build} and {@code joust fmt <file>}. */
public final class JoustNpmActions {
    private JoustNpmActions() {
    }

    /** {@code joust build}. */
    public static final class Build extends JoustProjectAction {
        @Override
        boolean applies(Ctx ctx) {
            return ctx.npm() != null && ctx.npm().cliInstalled();
        }

        @Override
        String title(Ctx ctx) {
            return "JOUST Build (" + ctx.moduleName() + ")";
        }

        @Override
        void perform(Ctx ctx) throws Exception {
            reportResult(ctx, title(ctx), npmRunner(ctx).run(list("build")));
        }
    }

    /** {@code joust fmt <file>}: a user command that writes the file on disk (D6: formatting is not a server edit). */
    public static final class Format extends JoustProjectAction {
        @Override
        boolean applies(Ctx ctx) {
            return ctx.npm() != null && ctx.npm().cliInstalled() && ctx.file().getName().endsWith(".jsc")
                    && JoustRealPaths.isInside(ctx.fileReal(), ctx.npm().dir());
        }

        @Override
        String title(Ctx ctx) {
            return "JOUST Format " + ctx.file().getName();
        }

        @Override
        void perform(Ctx ctx) throws Exception {
            String rel = ctx.npm().dir().relativize(ctx.fileReal()).toString();
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(() ->
                    com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveAllDocuments());
            reportResult(ctx, title(ctx), npmRunner(ctx).run(list("fmt", rel)));
            com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(true, false, false, ctx.file());
        }
    }
}
