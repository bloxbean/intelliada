package com.bloxbean.intelliada.idea.joust.project.actions;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbi;
import com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;

/**
 * ADR-004 D7's Gradle actions: each runs one task of the owning module, addressed by its qualified path
 * ({@code :module:generateJsc}), through IntelliJ's Gradle integration. Hidden without IntelliJ's Gradle plugin; the
 * {@code .jsc} tasks only where the plugin registers them (a module with {@code src/main/jsc}, JoustPlugin).
 */
public class JoustGradleTaskAction extends JoustProjectAction {

    private final String task;
    private final String label;
    private final boolean writesLock;
    private final boolean needsJsc;

    JoustGradleTaskAction(String task, String label, boolean writesLock, boolean needsJsc) {
        this.task = task;
        this.label = label;
        this.writesLock = writesLock;
        this.needsJsc = needsJsc;
    }

    @Override
    boolean applies(Ctx ctx) {
        return ctx.gradle() != null && JoustGradleTasks.getInstance() != null && (!needsJsc || ctx.gradle().hasJsc());
    }

    @Override
    String unavailable(Ctx ctx) {
        return gradleUnavailable(ctx);
    }

    @Override
    String title(Ctx ctx) {
        return label + " (" + ctx.moduleName() + ")";
    }

    @Override
    boolean confirm(Ctx ctx) {
        return !writesLock || ask(ctx.project(), "Run acceptJoustLock in " + ctx.moduleName() + "? It rewrites "
                + ctx.gradle().dir().resolve("joust.lock") + ".", "Accept joust.lock", "Accept");
    }

    @Override
    void perform(Ctx ctx, JoustRunGuard guard) throws Exception {
        reportResult(ctx, guard, title(ctx), gradleRunner(ctx, guard, title(ctx)).run(list(JoustAbi.task(ctx.linked().gradlePath(), task))));
    }

    /** Build. */
    public static final class Build extends JoustGradleTaskAction {
        public Build() {
            super("build", "JOUST Build", false, false);
        }
    }

    /** Generate from .jsc ({@code generateJsc}). */
    public static final class Generate extends JoustGradleTaskAction {
        public Generate() {
            super("generateJsc", "Generate from .jsc", false, true);
        }
    }

    /** Check ABI ({@code checkJscAbi}). */
    public static final class CheckAbi extends JoustGradleTaskAction {
        public CheckAbi() {
            super("checkJscAbi", "Check ABI", false, true);
        }
    }

    /** Accept joust.lock ({@code acceptJoustLock}), after a confirmation naming the file. */
    public static final class AcceptLock extends JoustGradleTaskAction {
        public AcceptLock() {
            super("acceptJoustLock", "Accept joust.lock", true, false);
        }
    }

    /** Run tests ({@code test}). */
    public static final class RunTests extends JoustGradleTaskAction {
        public RunTests() {
            super("test", "JOUST Run Tests", false, false);
        }
    }
}
