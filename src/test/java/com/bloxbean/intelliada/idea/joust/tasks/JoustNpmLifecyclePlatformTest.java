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
            JoustNpmTasks.enumerationDeniedForTests = false;
            JoustNpmTasks.treeKillFailsForTests = false;
            JoustTaskTracker.getInstance(getProject()).forgetAllForTests();
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
        assertTrue("finished within the deadline (M2-CDX-31)", result.isDone());
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

    /**
     * M2-CDX-3: joust test runs a Node test runner as a child of the CLI. Losing trust ends both, also when listing
     * descendants is refused; the run is released only after both have exited.
     */
    public void testANestedTestRunnerIsEndedWithItsParent() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        for (boolean denied : new boolean[]{false, true}) {
            JoustNpmTasks.enumerationDeniedForTests = denied;
            var d = new JoustToolsState.Data();
            d.nodePath = node;
            JoustToolsState.getInstance().loadState(d);
            JoustToolsState.getInstance().setValidatedNode(JoustToolValidator.identityOf(Path.of(node).toRealPath()), "test");
            Path base = Files.createDirectories(Path.of(getProject().getBasePath())).toRealPath();
            Path web = Files.createDirectories(base.resolve("web-nested-" + denied));
            Files.writeString(web.resolve("joust.config.json"), "{}");
            Path bin = Files.createDirectories(web.resolve("node_modules/@julclang/joust/bin"));
            Files.writeString(bin.getParent().resolve("package.json"), "{\"name\":\"@julclang/joust\"}");
            // Like joust test: the CLI starts a Node child (the test runner) and waits for it.
            Files.writeString(bin.resolve("joust.js"), "const { spawn } = require('child_process');\n"
                    + "const c = spawn(process.execPath, ['-e', \"require('fs').writeFileSync('child', String(process.pid)); setTimeout(() => {}, 120000)\"],"
                    + " { stdio: 'ignore' });\n"
                    + "require('fs').writeFileSync('pid', String(process.pid));\n"
                    + "c.on('exit', () => process.exit(0));\n");
            TrustedProjects.setProjectTrusted(getProject(), true);
            var root = JoustProjectLayout.scan(base, com.bloxbean.intelliada.idea.joust.core.JoustPaths.Flavor.current()).npmRoots().stream()
                    .filter(r -> r.dir().equals(web)).findFirst().orElseThrow();
            JoustRunGuard guard = JoustRunGuard.capture(getProject());
            var result = ApplicationManager.getApplication().executeOnPooledThread(() -> JoustNpmTasks.run(guard, root, List.of("test"), "test"));
            Path pidFile = web.resolve("pid");
            Path childFile = web.resolve("child");
            long end = System.currentTimeMillis() + 20_000;
            while ((!Files.exists(pidFile) || !Files.exists(childFile)) && System.currentTimeMillis() < end) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                Thread.sleep(50);
            }
            Thread.sleep(600);   // let the descendants be recorded
            long parent = Long.parseLong(Files.readString(pidFile).trim());
            long child = Long.parseLong(Files.readString(childFile).trim());
            assertTrue(alive(parent) && alive(child));
            TrustedProjects.setProjectTrusted(getProject(), false);
            end = System.currentTimeMillis() + 30_000;
            while ((!result.isDone() || JoustTaskTracker.getInstance(getProject()).runningCount() != 0) && System.currentTimeMillis() < end) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
                Thread.sleep(50);
            }
            assertTrue(result.isDone());
            assertFalse("denied=" + denied + ": the CLI is gone", alive(parent));
            assertFalse("denied=" + denied + ": the test runner is gone", alive(child));
            assertEquals("released after both exited", 0, JoustTaskTracker.getInstance(getProject()).runningCount());
        }
    }

    /** M2-CDX-32, M2-DES-13, M2-SEC-9: an observed descendant is kept as its handle; once it exited, nothing is killed in its name. */
    public void testAnExitedDescendantIsNeverKilledByPid() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        OSProcessHandler h = new OSProcessHandler(new GeneralCommandLine(node, "-e", "setTimeout(() => {}, 120000)"));
        JoustNpmTasks.Launch launch = new JoustNpmTasks.Launch(h);
        launch.ensureStarted();
        Process shortLived = new ProcessBuilder(node, "-e", "0").start();
        ProcessHandle observed = shortLived.toHandle();
        launch.seen.add(observed);
        shortLived.waitFor();
        assertFalse("its own handle knows it exited, whoever has its PID now", observed.isAlive());
        assertEquals(List.of(), launch.liveSeen());
        assertTrue(JoustNpmTasks.kill(launch));
    }

    /** M2-CDX-3: listing refused from launch and the tree kill failing: the run stays tracked; the tree cannot be shown gone. */
    public void testAnUnknowableTreeKeepsTheRunTracked() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        JoustNpmTasks.enumerationDeniedForTests = true;
        JoustNpmTasks.treeKillFailsForTests = true;
        var d = new JoustToolsState.Data();
        d.nodePath = node;
        JoustToolsState.getInstance().loadState(d);
        JoustToolsState.getInstance().setValidatedNode(JoustToolValidator.identityOf(Path.of(node).toRealPath()), "test");
        Path base = Files.createDirectories(Path.of(getProject().getBasePath())).toRealPath();
        Path web = Files.createDirectories(base.resolve("web-unknowable"));
        Files.writeString(web.resolve("joust.config.json"), "{}");
        Path bin = Files.createDirectories(web.resolve("node_modules/@julclang/joust/bin"));
        Files.writeString(bin.getParent().resolve("package.json"), "{\"name\":\"@julclang/joust\"}");
        Files.writeString(bin.resolve("joust.js"), "const { spawn } = require('child_process');\n"
                + "const c = spawn(process.execPath, ['-e', \"require('fs').writeFileSync('child', String(process.pid)); setTimeout(() => {}, 120000)\"],"
                + " { stdio: 'ignore' });\n"
                + "require('fs').writeFileSync('pid', String(process.pid));\n"
                + "c.on('exit', () => process.exit(0));\n");
        var root = JoustProjectLayout.scan(base, com.bloxbean.intelliada.idea.joust.core.JoustPaths.Flavor.current()).npmRoots().stream()
                .filter(r -> r.dir().equals(web)).findFirst().orElseThrow();
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        var result = ApplicationManager.getApplication().executeOnPooledThread(() -> JoustNpmTasks.run(guard, root, List.of("test"), "test"));
        long end = System.currentTimeMillis() + 20_000;
        while ((!Files.exists(web.resolve("pid")) || !Files.exists(web.resolve("child"))) && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(50);
        }
        long child = Long.parseLong(Files.readString(web.resolve("child")).trim());
        TrustedProjects.setProjectTrusted(getProject(), false);
        end = System.currentTimeMillis() + 30_000;
        while (!result.isDone() && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(50);
        }
        assertTrue(result.isDone());
        Thread.sleep(1_000);
        assertEquals("the tree was never known: the run stays tracked", 1, JoustTaskTracker.getInstance(getProject()).runningCount());
        ProcessHandle.of(child).ifPresent(ProcessHandle::destroyForcibly);   // test cleanup only
    }
}
