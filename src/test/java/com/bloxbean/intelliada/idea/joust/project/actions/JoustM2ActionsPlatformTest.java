package com.bloxbean.intelliada.idea.joust.project.actions;

import com.bloxbean.intelliada.idea.joust.JoustProcessStarts;
import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.project.JoustConsoleFilterProvider;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks;
import com.bloxbean.intelliada.idea.joust.tasks.JoustNpmTasks;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * ADR-004 M2 actions (D1, D7, I2, I20) with runnable targets: a Gradle module served by a recording stand-in for
 * IntelliJ's Gradle integration, and an npm root whose CLI (a Node script) records its arguments.
 */
public class JoustM2ActionsPlatformTest extends BasePlatformTestCase {

    private Path base;
    private Path web;
    private final List<List<String>> gradleRuns = new CopyOnWriteArrayList<>();
    private TestDialog savedDialog;
    private JoustToolsState.Data savedTools;

    /** IntelliJ's Gradle integration, replaced: the module dirs are linked; runs are recorded only for a live guard. */
    private final class RecordingGradle implements JoustGradleTasks {
        @Override
        public Linked linked(Project project, Path moduleDir) {
            if (moduleDir.equals(base)) return new Linked(base, ":");
            return moduleDir.startsWith(base) ? new Linked(base, ":" + base.relativize(moduleDir).toString().replace('/', ':')) : null;
        }

        @Override
        public JoustAbiReview.Result run(JoustRunGuard guard, Path rootDir, List<String> args, String title) {
            JoustTaskTracker.Run run = JoustTaskTracker.getInstance(guard.project()).start(guard);
            if (run == null) return new JoustAbiReview.Result(false, "", true);
            try {
                gradleRuns.add(args);
                return new JoustAbiReview.Result(false, "", false);
            } finally {
                JoustTaskTracker.getInstance(guard.project()).done(run);
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        base = Files.createDirectories(Path.of(getProject().getBasePath())).toRealPath();
        Path src = Path.of(getClass().getClassLoader().getResource("joust-m2/SOURCE.txt").toURI()).getParent().resolve("gradle-jsc");
        copy(src, base);
        web = Files.createDirectories(base.resolve("web"));
        Files.writeString(web.resolve("joust.config.json"), "{\"contracts\":\"contracts\"}");
        Files.createDirectories(web.resolve("contracts/app"));
        Files.writeString(web.resolve("contracts/app/Counter.jsc"), "jsc 1\n");
        Path cli = Files.createDirectories(web.resolve("node_modules/@julclang/joust/bin"));
        Files.writeString(cli.getParent().resolve("package.json"), "{\"name\":\"@julclang/joust\",\"version\":\"0.1.0-pre2\"}");
        Files.writeString(cli.resolve("joust.js"), "require('fs').appendFileSync('calls.log', JSON.stringify(process.argv.slice(2)) + '\\n');\n");
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base).refresh(false, true);
        TrustedProjects.setProjectTrusted(getProject(), true);
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), JoustGradleTasks.class, new RecordingGradle(), getTestRootDisposable());
        savedDialog = TestDialogManager.setTestDialog(TestDialog.OK);
        savedTools = JoustToolsState.getInstance().getState();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            TestDialogManager.setTestDialog(savedDialog);
            JoustToolsState.getInstance().loadState(savedTools);
            TrustedProjects.setProjectTrusted(getProject(), true);
        } finally {
            super.tearDown();
        }
    }

    static void copy(Path src, Path dst) throws Exception {
        try (var s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path t = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private VirtualFile vf(Path p) {
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(p);
    }

    private AnActionEvent event(AnAction a, VirtualFile file) {
        return TestActionEvent.createTestEvent(a, SimpleDataContext.builder().add(CommonDataKeys.PROJECT, getProject())
                .add(CommonDataKeys.VIRTUAL_FILE, file).build());
    }

    private static void waitFor(BooleanSupplier c, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (!c.getAsBoolean() && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(50);
        }
    }

    private boolean configureNode() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return false;
        var d = new JoustToolsState.Data();
        d.nodePath = node;
        JoustToolsState.getInstance().loadState(d);
        JoustToolsState.getInstance().setValidatedNode(JoustToolValidator.identityOf(Path.of(node).toRealPath()), "test");
        return true;
    }

    private List<String> npmCalls() throws Exception {
        Path log = web.resolve("calls.log");
        return Files.exists(log) ? Files.readAllLines(log) : List.of();
    }

    /** I2 and D7 for every Gradle action: enabled and running its qualified task when trusted; disabled, and nothing, when not. */
    public void testEveryGradleActionIsTrustGatedWithARunnableTarget() throws Exception {
        VirtualFile jsc = vf(base.resolve("src/main/jsc/org/acme/Counter.jsc"));
        Object[][] actions = {{new JoustGradleTaskAction.Build(), ":build"}, {new JoustGradleTaskAction.Generate(), ":generateJsc"},
                {new JoustGradleTaskAction.CheckAbi(), ":checkJscAbi"}, {new JoustGradleTaskAction.AcceptLock(), ":acceptJoustLock"},
                {new JoustGradleTaskAction.RunTests(), ":test"}, {new JoustReviewAbiAction(), ":jscAbiDiff"}};
        for (Object[] row : actions) {
            AnAction a = (AnAction) row[0];
            String task = (String) row[1];
            TrustedProjects.setProjectTrusted(getProject(), false);
            AnActionEvent u = event(a, jsc);
            a.update(u);
            assertTrue(task + " is shown", u.getPresentation().isVisible());
            assertFalse(task + " is disabled when untrusted", u.getPresentation().isEnabled());
            gradleRuns.clear();
            try (var starts = new JoustProcessStarts()) {
                a.actionPerformed(event(a, jsc));
                waitFor(() -> false, 600);
                assertEquals(task + ": no process", List.of(), starts.commands());
            }
            assertEquals(task + ": nothing ran untrusted", List.of(), gradleRuns);
            TrustedProjects.setProjectTrusted(getProject(), true);
            AnActionEvent t = event(a, jsc);
            a.update(t);
            assertTrue(task + " is enabled when trusted", t.getPresentation().isEnabled());
            a.actionPerformed(event(a, jsc));
            waitFor(() -> !gradleRuns.isEmpty(), 10_000);
            assertFalse(task + " ran", gradleRuns.isEmpty());
            assertEquals(task, gradleRuns.get(0).get(0));
        }
    }

    /** I2 and D7 for every npm action, with the project's own CLI (a recording Node script). */
    public void testEveryNpmActionIsTrustGatedWithARunnableTarget() throws Exception {
        if (!configureNode()) return;
        VirtualFile jsc = vf(web.resolve("contracts/app/Counter.jsc"));
        Object[][] actions = {{new JoustNpmActions.Build(), "[\"build\"]"}, {new JoustNpmActions.RunTests(), "[\"test\"]"},
                {new JoustNpmActions.AcceptLock(), "[\"build\",\"--accept-lock\"]"},
                {new JoustNpmActions.Format(), "[\"fmt\",\"contracts/app/Counter.jsc\"]"},
                {new JoustReviewAbiAction(), "[\"abi\",\"diff\",\"app.Counter\",\"--out\""}};
        for (Object[] row : actions) {
            AnAction a = (AnAction) row[0];
            String expected = (String) row[1];
            Files.deleteIfExists(web.resolve("calls.log"));
            TrustedProjects.setProjectTrusted(getProject(), false);
            AnActionEvent u = event(a, jsc);
            a.update(u);
            assertTrue(expected + " is shown", u.getPresentation().isVisible());
            assertFalse(expected + " is disabled when untrusted", u.getPresentation().isEnabled());
            try (var starts = new JoustProcessStarts()) {
                a.actionPerformed(event(a, jsc));
                waitFor(() -> false, 600);
                assertEquals(expected + ": no process", List.of(), starts.commands());
            }
            assertEquals(List.of(), npmCalls());
            TrustedProjects.setProjectTrusted(getProject(), true);
            a.update(event(a, jsc));
            a.actionPerformed(event(a, jsc));
            waitFor(() -> {
                try {
                    return !npmCalls().isEmpty();
                } catch (Exception e) {
                    return false;
                }
            }, 15_000);
            List<String> calls = npmCalls();
            assertEquals(expected, 1, calls.size());
            assertTrue(calls.get(0), calls.get(0).startsWith(expected));
        }
    }

    /** M2-CDX-2: a guard does not come back to life when trust is granted again. */
    public void testAGuardEndsWithTrustAndStaysEnded() {
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        assertTrue(guard.live());
        TrustedProjects.setProjectTrusted(getProject(), false);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertFalse(guard.live());
        TrustedProjects.setProjectTrusted(getProject(), true);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertFalse("not revived", guard.live());
        assertNull("nothing may start for it", JoustTaskTracker.getInstance(getProject()).start(guard));
        assertTrue(JoustRunGuard.capture(getProject()).live());
    }

    /** M2-CDX-3: a cancellation before the real work exists is applied the moment it is attached. */
    public void testACancellationBeforeStartIsHonoured() {
        JoustTaskTracker tracker = JoustTaskTracker.getInstance(getProject());
        JoustTaskTracker.Run run = tracker.start(JoustRunGuard.capture(getProject()));
        assertNotNull(run);
        tracker.cancelAll();
        java.util.concurrent.atomic.AtomicBoolean killed = new java.util.concurrent.atomic.AtomicBoolean();
        run.attach(() -> killed.set(true));
        assertTrue(killed.get());
        tracker.done(run);
    }

    /** I20 with a real process started under a guard: losing trust kills it and discards its result. */
    public void testLosingTrustKillsARunningNpmCli() throws Exception {
        if (!configureNode()) return;
        Files.writeString(web.resolve("node_modules/@julclang/joust/bin/joust.js"),
                "require('fs').writeFileSync('pid', String(process.pid)); setTimeout(() => {}, 120000);\n");
        var layout = JoustProjectLayout.scan(base, com.bloxbean.intelliada.idea.joust.core.JoustPaths.Flavor.current());
        var root = layout.npmRoots().stream().filter(r -> r.dir().equals(web)).findFirst().orElseThrow();
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        var result = ApplicationManager.getApplication().executeOnPooledThread(() -> JoustNpmTasks.run(guard, root, List.of("build"), "build"));
        Path pidFile = web.resolve("pid");
        waitFor(() -> Files.exists(pidFile), 10_000);
        long pid = Long.parseLong(Files.readString(pidFile).trim());
        assertTrue(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        TrustedProjects.setProjectTrusted(getProject(), false);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertTrue("the late result is discarded", result.get(20, java.util.concurrent.TimeUnit.SECONDS).cancelled());
        waitFor(() -> !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), 5_000);
        assertFalse("killed", ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }

    /** M2-SEC-1: a module named like markup gives titles with no angle brackets (they reach an HTML tooltip). */
    public void testTitlesFromProjectNamesCannotFormTags() throws Exception {
        Path evil = Files.createDirectories(base.resolve("<img src=x>"));
        copy(Path.of(getClass().getClassLoader().getResource("joust-m2/SOURCE.txt").toURI()).getParent().resolve("gradle-jsc"), evil);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base).refresh(false, true);
        com.bloxbean.intelliada.idea.joust.project.JoustProjectService.getInstance(getProject()).layout();
        VirtualFile jsc = vf(evil.resolve("src/main/jsc/org/acme/Counter.jsc"));
        JoustProjectAction.Ctx ctx = ApplicationManager.getApplication().executeOnPooledThread(
                () -> JoustProjectAction.context(getProject(), jsc)).get();
        assertNotNull(ctx);
        for (JoustProjectAction a : new JoustProjectAction[]{new JoustGradleTaskAction.Build(), new JoustReviewAbiAction()}) {
            String title = a.title(ctx);
            assertFalse(title, title.contains("<") || title.contains(">"));
            assertTrue(title, title.contains("\u2039img src=x\u203A"));
        }
    }

    /** M2-CDX-9: a link whose target is replaced between rendering and clicking does not open. */
    public void testALinkReResolvesWhenClicked() throws Exception {
        Path jscFile = base.resolve("src/main/jsc/org/acme/Counter.jsc");
        String line = "org/acme/Counter.jsc:17:12: error[JSC0331]: x";
        var result = new JoustConsoleFilterProvider.JoustDiagnosticFilter(getProject()).applyFilter(line, line.length());
        assertNotNull(result);
        var info = (JoustConsoleFilterProvider.JoustLinkInfo) result.getResultItems().get(0).getHyperlinkInfo();
        Path outside = Files.writeString(Files.createTempFile("elsewhere", ".jsc"), "x");
        String original = Files.readString(jscFile);
        Files.delete(jscFile);
        Files.createSymbolicLink(jscFile, outside);
        try {
            var opened = info.open(getProject());
            waitFor(opened::isDone, 5_000);
            assertFalse(opened.get());
        } finally {
            Files.delete(jscFile);
            Files.writeString(jscFile, original);
        }
        var again = info.open(getProject());
        waitFor(again::isDone, 5_000);
        assertTrue("the restored file opens", again.get());
    }
}
