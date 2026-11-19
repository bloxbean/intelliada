package com.bloxbean.intelliada.idea.joust.project.actions;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbi;
import com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ADR-004 D7's Gradle actions: each runs one task of the owning module, addressed by its qualified path
 * ({@code :module:generateJsc}), through IntelliJ's Gradle integration. Hidden without IntelliJ's Gradle plugin.
 */
public class JoustGradleTaskAction extends JoustProjectAction {

    private final String task;
    private final String label;
    private final boolean writesLock;

    JoustGradleTaskAction(String task, String label, boolean writesLock) {
        this.task = task;
        this.label = label;
        this.writesLock = writesLock;
    }

    @Override
    boolean applies(Ctx ctx) {
        return ctx.gradle() != null && JoustGradleTasks.getInstance() != null;
    }

    @Override
    String title(Ctx ctx) {
        return label + " (" + ctx.moduleName() + ")";
    }

    @Override
    boolean confirm(Ctx ctx) {
        if (!writesLock) return true;
        AtomicBoolean ok = new AtomicBoolean();
        ApplicationManager.getApplication().invokeAndWait(() -> ok.set(Messages.showYesNoDialog(ctx.project(),
                "Run acceptJoustLock in " + ctx.moduleName() + "? It rewrites " + ctx.gradle().dir().resolve("joust.lock") + ".",
                "Accept joust.lock", "Accept", "Cancel", Messages.getWarningIcon()) == Messages.YES));
        return ok.get();
    }

    @Override
    void perform(Ctx ctx) throws Exception {
        var runner = gradleRunner(ctx, title(ctx));
        String path = gradlePath(ctx);
        if (runner == null || path == null) return;
        reportResult(ctx, title(ctx), runner.run(list(JoustAbi.task(path, task))));
    }

    /** Build. */
    public static final class Build extends JoustGradleTaskAction {
        public Build() {
            super("build", "JOUST Build", false);
        }
    }

    /** Generate from .jsc ({@code generateJsc}). */
    public static final class Generate extends JoustGradleTaskAction {
        public Generate() {
            super("generateJsc", "Generate from .jsc", false);
        }
    }

    /** Check ABI ({@code checkJscAbi}). */
    public static final class CheckAbi extends JoustGradleTaskAction {
        public CheckAbi() {
            super("checkJscAbi", "Check ABI", false);
        }
    }

    /** Accept joust.lock ({@code acceptJoustLock}), after a confirmation naming the file. */
    public static final class AcceptLock extends JoustGradleTaskAction {
        public AcceptLock() {
            super("acceptJoustLock", "Accept joust.lock", true);
        }
    }

    /** Run tests ({@code test}). */
    public static final class RunTests extends JoustGradleTaskAction {
        public RunTests() {
            super("test", "JOUST Run Tests", false);
        }
    }
}
