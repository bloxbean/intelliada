package com.bloxbean.intelliada.idea.joust.tasks;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** M2-CDX-3: a handler detached from a live child is no proof of its exit; only the process's own exit releases a run. */
public class JoustNpmLifecyclePlatformTest extends BasePlatformTestCase {

    private JoustToolsState.Data saved;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        saved = JoustToolsState.getInstance().getState();
        TrustedProjects.setProjectTrusted(getProject(), true);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            JoustNpmTasks.setConsoleOpenerForTests(null);
            JoustNpmTasks.killHookForTests = null;
            JoustToolsState.getInstance().loadState(saved);
            TrustedProjects.setProjectTrusted(getProject(), true);
        } finally {
            super.tearDown();
        }
    }

    private static boolean alive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    /** kill() on a handler that let go of its live child still ends the child, and says so only after it exited. */
    public void testKillEndsADetachedChild() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        OSProcessHandler h = new OSProcessHandler(new GeneralCommandLine(node, "-e", "setTimeout(() => {}, 120000)"));
        JoustNpmTasks.Launch launch = new JoustNpmTasks.Launch(h);
        launch.ensureStarted();
        long pid = h.getProcess().pid();
        h.detachProcess();
        assertTrue("the handler reports done", h.waitFor(5_000));
        assertTrue("while the child lives", alive(pid));
        assertTrue(JoustNpmTasks.kill(launch));
        assertFalse(alive(pid));
    }

    /** A run whose handler is detached while its child lives: the child is ended before the run leaves the tracker. */
    public void testADetachedRunIsEndedBeforeRelease() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        var d = new JoustToolsState.Data();
        d.nodePath = node;
        JoustToolsState.getInstance().loadState(d);
        JoustToolsState.getInstance().setValidatedNode(JoustToolValidator.identityOf(Path.of(node).toRealPath()), "test");
        Path base = Files.createDirectories(Path.of(getProject().getBasePath())).toRealPath();
        Path web = Files.createDirectories(base.resolve("web-detach"));
        Files.writeString(web.resolve("joust.config.json"), "{}");
        Path bin = Files.createDirectories(web.resolve("node_modules/@julclang/joust/bin"));
        Files.writeString(bin.getParent().resolve("package.json"), "{\"name\":\"@julclang/joust\"}");
        Files.writeString(bin.resolve("joust.js"), "require('fs').writeFileSync('pid', String(process.pid)); setTimeout(() => {}, 120000);\n");
        var root = JoustProjectLayout.scan(base, com.bloxbean.intelliada.idea.joust.core.JoustPaths.Flavor.current()).npmRoots().stream()
                .filter(r -> r.dir().equals(web)).findFirst().orElseThrow();
        Path pidFile = web.resolve("pid");
        JoustNpmTasks.setConsoleOpenerForTests(h -> new Thread(() -> {
            // Detach only once the child has written its PID, so the race with Node's start cannot decide (M2-CDX-30).
            long end = System.currentTimeMillis() + 20_000;
            while ((!h.isStartNotified() || !Files.exists(pidFile)) && System.currentTimeMillis() < end) Thread.onSpinWait();
            h.detachProcess();   // the handler lets go of a live child
        }).start());
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        var result = ApplicationManager.getApplication().executeOnPooledThread(() -> JoustNpmTasks.run(guard, root, List.of("build"), "build"));
        long end = System.currentTimeMillis() + 30_000;
        while (!result.isDone() && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(50);
        }
        var r = result.get();   // a runner exception fails the test
        assertFalse("reported as a failure, not a cancellation", r.cancelled() || r.ok());
        long pid = Long.parseLong(Files.readString(pidFile).trim());
        assertFalse("the child was ended before the run returned", alive(pid));
        assertEquals("released after its exit", 0, JoustTaskTracker.getInstance(getProject()).runningCount());
    }

    /** M2-CDX-3: cleanup throws while the child lives: the run stays tracked until the process itself exits. */
    public void testAThrowingCleanupStillWaitsForTheExit() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        var d = new JoustToolsState.Data();
        d.nodePath = node;
        JoustToolsState.getInstance().loadState(d);
        JoustToolsState.getInstance().setValidatedNode(JoustToolValidator.identityOf(Path.of(node).toRealPath()), "test");
        Path base = Files.createDirectories(Path.of(getProject().getBasePath())).toRealPath();
        Path web = Files.createDirectories(base.resolve("web-throw"));
        Files.writeString(web.resolve("joust.config.json"), "{}");
        Path bin = Files.createDirectories(web.resolve("node_modules/@julclang/joust/bin"));
        Files.writeString(bin.getParent().resolve("package.json"), "{\"name\":\"@julclang/joust\"}");
        Files.writeString(bin.resolve("joust.js"), "require('fs').writeFileSync('pid', String(process.pid)); setTimeout(() => {}, 120000);\n");
        var root = JoustProjectLayout.scan(base, com.bloxbean.intelliada.idea.joust.core.JoustPaths.Flavor.current()).npmRoots().stream()
                .filter(r -> r.dir().equals(web)).findFirst().orElseThrow();
        Path pidFile = web.resolve("pid");
        JoustNpmTasks.killHookForTests = () -> {
            throw new SecurityException("Operation not permitted (sysctl failed)");
        };
        JoustNpmTasks.setConsoleOpenerForTests(h -> {
            long end = System.currentTimeMillis() + 20_000;
            while (!Files.exists(pidFile) && System.currentTimeMillis() < end) Thread.onSpinWait();
            throw new IllegalStateException("the console failed to open");
        });
        var tracker = JoustTaskTracker.getInstance(getProject());
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        var result = ApplicationManager.getApplication().executeOnPooledThread(() -> JoustNpmTasks.run(guard, root, List.of("build"), "build"));
        long end = System.currentTimeMillis() + 30_000;
        while (!result.isDone() && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();   // the run saves editors on the EDT before it starts
            Thread.sleep(50);
        }
        assertTrue(result.isDone());
        long pid = Long.parseLong(Files.readString(pidFile).trim());
        assertTrue("the cleanup failed, so the child still lives", alive(pid));
        assertEquals("tracked while it lives", 1, tracker.runningCount());
        ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
        end = System.currentTimeMillis() + 10_000;
        while (tracker.runningCount() != 0 && System.currentTimeMillis() < end) Thread.sleep(20);
        assertEquals("released on its exit", 0, tracker.runningCount());
    }
}
