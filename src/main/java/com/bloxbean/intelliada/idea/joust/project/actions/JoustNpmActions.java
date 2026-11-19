package com.bloxbean.intelliada.idea.joust.project.actions;

import com.bloxbean.intelliada.idea.joust.core.JoustRealPaths;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;

import java.util.List;

/**
 * ADR-004 D7's npm actions: the project's own CLI ({@code joust build}, {@code joust test},
 * {@code joust build --accept-lock} after a confirmation naming the file, and {@code joust fmt <file>}).
 */
public final class JoustNpmActions {
    private JoustNpmActions() {
    }

    abstract static class NpmAction extends JoustProjectAction {
        private final String label;

        NpmAction(String label) {
            this.label = label;
        }

        @Override
        boolean applies(Ctx ctx) {
            return ctx.npm() != null && ctx.npm().cliInstalled();
        }

        @Override
        String title(Ctx ctx) {
            return label + " (" + ctx.moduleName() + ")";
        }

        abstract List<String> args(Ctx ctx);

        @Override
        void perform(Ctx ctx, JoustRunGuard guard) throws Exception {
            reportResult(ctx, title(ctx), npmRunner(ctx, guard, title(ctx)).run(args(ctx)));
        }
    }

    /** {@code joust build}. */
    public static final class Build extends NpmAction {
        public Build() {
            super("JOUST Build");
        }

        @Override
        List<String> args(Ctx ctx) {
            return list("build");
        }
    }

    /** {@code joust test}. */
    public static final class RunTests extends NpmAction {
        public RunTests() {
            super("JOUST Run Tests");
        }

        @Override
        List<String> args(Ctx ctx) {
            return list("test");
        }
    }

    /** {@code joust build --accept-lock}, after a confirmation naming the file. */
    public static final class AcceptLock extends NpmAction {
        public AcceptLock() {
            super("Accept joust.lock");
        }

        @Override
        boolean confirm(Ctx ctx) {
            return ask(ctx.project(), "Run joust build --accept-lock in " + ctx.moduleName() + "? It rewrites "
                    + ctx.npm().dir().resolve("joust.lock") + ".", "Accept joust.lock", "Accept");
        }

        @Override
        List<String> args(Ctx ctx) {
            return list("build", "--accept-lock");
        }
    }

    /** {@code joust fmt <file>}: a user command that writes the file on disk (D6: formatting is not a server edit). */
    public static final class Format extends NpmAction {
        public Format() {
            super("JOUST Format");
        }

        @Override
        boolean applies(Ctx ctx) {
            return super.applies(ctx) && ctx.file() != null && ctx.file().getName().endsWith(".jsc")
                    && JoustRealPaths.isInside(ctx.fileReal(), ctx.npm().dir());
        }

        @Override
        String title(Ctx ctx) {
            return "JOUST Format (" + ctx.moduleName() + ")";
        }

        @Override
        List<String> args(Ctx ctx) {
            return list("fmt", ctx.npm().dir().relativize(ctx.fileReal()).toString());
        }

        @Override
        void perform(Ctx ctx, JoustRunGuard guard) throws Exception {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(() ->
                    com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveAllDocuments());
            super.perform(ctx, guard);
            com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(true, false, false, ctx.file());
        }
    }
}
