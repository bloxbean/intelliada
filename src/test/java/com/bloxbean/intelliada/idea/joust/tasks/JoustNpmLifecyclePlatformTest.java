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
            JoustNpmTasks.beforePublishForTests = null;
            JoustNpmTasks.afterListingForTests = null;
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
        // Owns its shutdown: exits when the test writes its stop file, or after 60 s (M2-CDX-40, M2-DES-16, M2-SEC-13).
        Files.writeString(bin.resolve("joust.js"), "const fs = require('fs'); fs.writeFileSync('pid', String(process.pid));"
                + " setInterval(() => { if (fs.existsSync('stop')) process.exit(0); }, 100); setTimeout(() => process.exit(0), 60000);\n");
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
        try {
        long end = System.currentTimeMillis() + 30_000;
        while (!result.isDone() && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();   // the run saves editors on the EDT before it starts
            Thread.sleep(50);
        }
        assertTrue(result.isDone());
        long pid = Long.parseLong(Files.readString(pidFile).trim());
        assertTrue("the cleanup failed, so the child still lives", alive(pid));
        assertEquals("tracked while it lives", 1, tracker.runningCount());
        Files.writeString(web.resolve("stop"), "x");   // the fixture ends itself
        end = System.currentTimeMillis() + 10_000;
        while (tracker.runningCount() != 0 && System.currentTimeMillis() < end) Thread.sleep(20);
        assertEquals("released on its exit", 0, tracker.runningCount());
        } finally {
            Files.writeString(web.resolve("stop"), "x");
        }
    }

    /**
     * M2-CDX-3, L21: joust test runs a Node test runner as a child of the CLI. Losing trust ends both when the runner was
     * observed; when listing is refused throughout, the CLI is ended and the unobserved runner is the documented residual.
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
            // The runner owns its shutdown: it exits when the fixture's stop file appears, or after 60 s (M2-CDX-40).
            Files.writeString(bin.resolve("joust.js"), "const { spawn } = require('child_process');\n"
                    + "const c = spawn(process.execPath, ['-e', \"const fs = require('fs'); fs.writeFileSync('child', String(process.pid));"
                    + " setInterval(() => { if (fs.existsSync('stop')) process.exit(0); }, 100); setTimeout(() => process.exit(0), 60000)\"],"
                    + " { stdio: 'ignore' });\n"
                    + "require('fs').writeFileSync('pid', String(process.pid));\n"
                    + "c.on('exit', () => process.exit(0));\n");
            TrustedProjects.setProjectTrusted(getProject(), true);
            var root = JoustProjectLayout.scan(base, com.bloxbean.intelliada.idea.joust.core.JoustPaths.Flavor.current()).npmRoots().stream()
                    .filter(r -> r.dir().equals(web)).findFirst().orElseThrow();
            JoustRunGuard guard = JoustRunGuard.capture(getProject());
            var result = ApplicationManager.getApplication().executeOnPooledThread(() -> JoustNpmTasks.run(guard, root, List.of("test"), "test"));
            try {
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
            // Listing refused throughout: the runner was never observed, so IntelliAda, acting only by observed identity,
            // cannot end it (L21, the documented residual of §12 E13); the fixture's stop file ends it.
            if (!denied) assertFalse("the observed test runner is gone", alive(child));
            assertEquals("released", 0, JoustTaskTracker.getInstance(getProject()).runningCount());
            } finally {
                Files.writeString(web.resolve("stop"), "x");
            }
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

    /** ADR-004 §11 L21: a dead parent is never listed or signalled; with nothing left to observe, the run is released. */
    public void testADeadParentIsOnlyObserved() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        OSProcessHandler h = new OSProcessHandler(new GeneralCommandLine(node, "-e", "0"));
        JoustNpmTasks.Launch launch = new JoustNpmTasks.Launch(h);
        launch.ensureStarted();
        assertTrue(h.waitFor(10_000));
        assertTrue(h.getProcess().waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
        launch.killed = true;
        try (var starts = new com.bloxbean.intelliada.idea.joust.JoustProcessStarts()) {
            JoustNpmTasks.destroyTree(launch, h.getProcess());
            java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
            JoustNpmTasks.releaseWhenTreeGone(launch, () -> released.set(true));
            long end = System.currentTimeMillis() + 5_000;
            while (!released.get() && System.currentTimeMillis() < end) Thread.sleep(20);
            assertTrue("nothing left to observe: released (M2-CDX-38)", released.get());
            assertEquals("no process is started to signal or list by PID", List.of(), starts.commands());
        }
    }

    private JoustProjectLayout.NpmRoot npmRoot(String name, String cli) throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        var d = new JoustToolsState.Data();
        d.nodePath = node;
        JoustToolsState.getInstance().loadState(d);
        JoustToolsState.getInstance().setValidatedNode(JoustToolValidator.identityOf(Path.of(node).toRealPath()), "test");
        Path base = Files.createDirectories(Path.of(getProject().getBasePath())).toRealPath();
        Path web = Files.createDirectories(base.resolve(name));
        Files.writeString(web.resolve("joust.config.json"), "{}");
        Path bin = Files.createDirectories(web.resolve("node_modules/@julclang/joust/bin"));
        Files.writeString(bin.getParent().resolve("package.json"), "{\"name\":\"@julclang/joust\"}");
        Files.writeString(bin.resolve("joust.js"), cli);
        return JoustProjectLayout.scan(base, com.bloxbean.intelliada.idea.joust.core.JoustPaths.Flavor.current()).npmRoots().stream()
                .filter(r -> r.dir().equals(web)).findFirst().orElseThrow();
    }

    private void await(java.util.concurrent.Future<?> f, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (!f.isDone() && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(50);
        }
        assertTrue(f.isDone());
    }

    /** M2-CDX-3, L21: a CLI that keeps starting children: every child observed before the cancellation is gone, then released. */
    public void testChildrenStartedAfterTheSnapshotAreEndedToo() throws Exception {
        if (JoustTestTools.env("JOUST_NODE", getName()) == null || com.intellij.openapi.util.SystemInfo.isWindows) return;
        // Bounded on its own (at most 200 children, 60 s), and every process exits when the stop file appears (M2-CDX-39/40).
        var root = npmRoot("web-spawner", "const { spawn } = require('child_process'); const fs = require('fs');\n"
                + "fs.writeFileSync('pid', String(process.pid));\n"
                + "const child = \"const fs = require('fs'); setInterval(() => { if (fs.existsSync('stop')) process.exit(0); }, 100);"
                + " setTimeout(() => process.exit(0), 60000)\";\n"
                + "let n = 0; const t = setInterval(() => { if (fs.existsSync('stop')) process.exit(0); if (++n > 200) return;"
                + " const c = spawn(process.execPath, ['-e', child], { stdio: 'ignore' });"
                + " fs.appendFileSync('children', c.pid + '\\n'); }, 50);\n"
                + "setTimeout(() => process.exit(0), 60000);\n");
        Path web = root.dir();
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        var result = ApplicationManager.getApplication().executeOnPooledThread(() -> JoustNpmTasks.run(guard, root, List.of("test"), "test"));
        try {
        long end = System.currentTimeMillis() + 20_000;
        while ((!Files.exists(web.resolve("children")) || Files.readAllLines(web.resolve("children")).size() < 5) && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(50);
        }
        List<String> startedBefore = Files.readAllLines(web.resolve("children"));
        try (var starts = new com.bloxbean.intelliada.idea.joust.JoustProcessStarts()) {
            TrustedProjects.setProjectTrusted(getProject(), false);
            await(result, 30_000);
            end = System.currentTimeMillis() + 15_000;
            while (JoustTaskTracker.getInstance(getProject()).runningCount() != 0 && System.currentTimeMillis() < end) Thread.sleep(50);
            assertEquals("released", 0, JoustTaskTracker.getInstance(getProject()).runningCount());
            // L21: ended by observed identity only; nothing is started to signal or list processes by PID.
            for (String cmd : starts.commands()) {
                assertFalse(cmd, cmd.contains("/bin/kill") || cmd.matches("(?s).*\\bps\\b.*"));
            }
        }
        for (String pid : startedBefore) {
            assertFalse("child " + pid + " started before the cancellation outlived it", alive(Long.parseLong(pid.trim())));
        }
        assertFalse(alive(Long.parseLong(Files.readString(web.resolve("pid")).trim())));
        } finally {
            // A child forked after the final listing is the documented residual (§12 E13): the stop file ends it.
            Files.writeString(web.resolve("stop"), "x");
        }
    }

    /**
     * M2-CDX-3: a sample that confirmed a child while the parent lived but has not published it yet holds the release:
     * the run is released only once that child has exited.
     */
    public void testAnInFlightSampleHoldsTheRelease() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        Path dir = Files.createDirectories(Path.of(getProject().getBasePath()).resolve("in-flight")).toRealPath();
        java.util.concurrent.CountDownLatch paused = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch resume = new java.util.concurrent.CountDownLatch(1);
        JoustNpmTasks.beforePublishForTests = now -> {
            if (now.isEmpty() || paused.getCount() == 0) return;
            paused.countDown();
            try {
                resume.await(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        // The parent starts a child that owns its shutdown (the stop file, or 60 s), then exits after 1 s.
        OSProcessHandler h = new OSProcessHandler(new GeneralCommandLine(node, "-e",
                "const c = require('child_process').spawn(process.execPath, ['-e', \"const fs = require('fs');"
                        + " setInterval(() => { if (fs.existsSync('stop')) process.exit(0); }, 100); setTimeout(() => process.exit(0), 60000)\"],"
                        + " { stdio: 'ignore' }); c.unref(); setTimeout(() => process.exit(0), 1000);").withWorkDirectory(dir.toFile()));
        h.setShouldDestroyProcessRecursively(false);
        JoustNpmTasks.Launch launch = new JoustNpmTasks.Launch(h);
        try {
            launch.ensureStarted();   // the handler's waiter completes only once start-notified (M2-CDX-41)
            launch.sample();
            assertTrue("a sample listed the child", paused.await(20, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue("the parent exited meanwhile", h.getProcess().waitFor(20, java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
            // The release decision waits for the in-flight sample (it takes the same lock): ask from another thread.
            var asking = ApplicationManager.getApplication().executeOnPooledThread(
                    () -> JoustNpmTasks.releaseWhenTreeGone(launch, () -> released.set(true)));
            Thread.sleep(300);
            assertFalse("the decision waits for the sample", asking.isDone());
            assertFalse("not released while the confirmed child is unpublished", released.get());
            resume.countDown();
            Thread.sleep(500);
            assertFalse("not released while the child lives", released.get());
            assertEquals(1, launch.liveSeen().size());
            Files.writeString(dir.resolve("stop"), "x");
            long end = System.currentTimeMillis() + 15_000;
            while (!released.get() && System.currentTimeMillis() < end) Thread.sleep(50);
            assertTrue("released once the child exited", released.get());
            assertTrue("the handler saw the end", h.waitFor(10_000));
        } finally {
            resume.countDown();
            Files.writeString(dir.resolve("stop"), "x");
        }
    }

    /** A parent that starts a child owning its shutdown (the stop file, or 60 s), then lives for {@code parentMs}. */
    private static OSProcessHandler parentWithChild(String node, Path dir, int parentMs) throws Exception {
        String child = "const fs = require('fs'); fs.writeFileSync('child', String(process.pid));"
                + " setInterval(() => { if (fs.existsSync('stop')) process.exit(0); }, 100); setTimeout(() => process.exit(0), 60000)";
        OSProcessHandler h = new OSProcessHandler(new GeneralCommandLine(node, "-e",
                "const c = require('child_process').spawn(process.execPath, ['-e', " + jsString(child) + "], { stdio: 'ignore' }); c.unref();"
                        + " const fs = require('fs'); setInterval(() => { if (fs.existsSync('stop')) process.exit(0); }, 100);"
                        + " setTimeout(() => process.exit(0), " + parentMs + ");").withWorkDirectory(dir.toFile()));
        h.setShouldDestroyProcessRecursively(false);
        return h;
    }

    private static String jsString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * M2-CDX-3: the cancellation's own listing (destroyTree) holds the release too: confirmed while the parent lived but
     * not yet published, its child keeps the run; the run is released only after that child is gone.
     */
    public void testACancellationListingHoldsTheRelease() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        Path dir = Files.createDirectories(Path.of(getProject().getBasePath()).resolve("cancel-in-flight")).toRealPath();
        OSProcessHandler h = parentWithChild(node, dir, 1500);
        JoustNpmTasks.Launch launch = new JoustNpmTasks.Launch(h);
        java.util.concurrent.CountDownLatch paused = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch resume = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<ProcessHandle> confirmed = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            launch.ensureStarted();
            long end = System.currentTimeMillis() + 20_000;
            while (!Files.exists(dir.resolve("child")) && System.currentTimeMillis() < end) Thread.sleep(20);
            assertTrue(Files.exists(dir.resolve("child")) && h.getProcess().isAlive());
            JoustNpmTasks.beforePublishForTests = now -> {
                if (now.isEmpty() || paused.getCount() == 0) return;
                confirmed.set(now.get(0));
                paused.countDown();
                try {
                    resume.await(30, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            launch.killed = true;
            var cancelling = ApplicationManager.getApplication().executeOnPooledThread(() -> JoustNpmTasks.destroyTree(launch, h.getProcess()));
            assertTrue("the cancellation listed the child", paused.await(20, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue("the parent exited meanwhile", h.getProcess().waitFor(20, java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
            var asking = ApplicationManager.getApplication().executeOnPooledThread(
                    () -> JoustNpmTasks.releaseWhenTreeGone(launch, () -> released.set(true)));
            Thread.sleep(300);
            assertFalse("the decision waits for the cancellation's listing", asking.isDone());
            assertFalse(released.get());
            resume.countDown();
            end = System.currentTimeMillis() + 15_000;
            while (!released.get() && System.currentTimeMillis() < end) Thread.sleep(20);
            assertTrue("released", released.get());
            assertFalse("only after the confirmed child was gone", confirmed.get().isAlive());
            cancelling.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(h.waitFor(10_000));
        } finally {
            resume.countDown();
            Files.writeString(dir.resolve("stop"), "x");
        }
    }

    /** M2-CDX-32: a listing whose parent exits during the enumeration is discarded: none of its handles is kept or signalled. */
    public void testAListingRacingTheParentsExitIsDiscarded() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        Path dir = Files.createDirectories(Path.of(getProject().getBasePath()).resolve("racing-exit")).toRealPath();
        OSProcessHandler h = parentWithChild(node, dir, 60_000);
        JoustNpmTasks.Launch launch = new JoustNpmTasks.Launch(h);
        java.util.concurrent.atomic.AtomicReference<ProcessHandle> candidate = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch raced = new java.util.concurrent.CountDownLatch(1);
        // The parent ends during the enumeration: after the listing returned, before it is re-checked.
        JoustNpmTasks.afterListingForTests = now -> {
            if (now.isEmpty() || raced.getCount() == 0) return;
            candidate.set(now.get(0));
            h.getProcess().destroyForcibly();
            try {
                h.getProcess().waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            raced.countDown();
        };
        try {
            launch.ensureStarted();
            launch.sample();
            assertTrue("a listing raced the parent's exit", raced.await(20, java.util.concurrent.TimeUnit.SECONDS));
            Thread.sleep(600);   // later samples see the parent gone
            assertTrue("the candidate is a live process (only the discard keeps it from being ours)", candidate.get().isAlive());
            assertEquals("discarded: nothing kept", List.of(), launch.liveSeen());
            assertFalse("the listing proves nothing", launch.enumerated);
            assertTrue("treated as gone: the candidate is not ours to wait for", JoustNpmTasks.treeGone(launch));
            assertTrue(candidate.get().isAlive());   // never signalled
            assertTrue(h.waitFor(10_000));
        } finally {
            Files.writeString(dir.resolve("stop"), "x");
        }
    }

    /** M2-CDX-36: a run that ends normally does not kill a descendant finishing its own work; it waits for it. */
    public void testANormalEndOnlyObservesDescendants() throws Exception {
        if (JoustTestTools.env("JOUST_NODE", getName()) == null) return;
        var root = npmRoot("web-helper", "const { spawn } = require('child_process');\n"
                + "const c = spawn(process.execPath, ['-e', \"setTimeout(() => require('fs').writeFileSync('helper-done', 'x'), 1500)\"],"
                + " { stdio: 'ignore', detached: false });\n"
                + "require('fs').writeFileSync('helper', String(c.pid));\n"
                + "setTimeout(() => process.exit(0), 300);\n");
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        var result = ApplicationManager.getApplication().executeOnPooledThread(() -> JoustNpmTasks.run(guard, root, List.of("build"), "build"));
        await(result, 30_000);
        assertTrue(result.get().ok());
        long end = System.currentTimeMillis() + 10_000;
        while (!Files.exists(root.dir().resolve("helper-done")) && System.currentTimeMillis() < end) Thread.sleep(50);
        assertTrue("the helper finished its work: it was not killed", Files.exists(root.dir().resolve("helper-done")));
        end = System.currentTimeMillis() + 10_000;
        while (JoustTaskTracker.getInstance(getProject()).runningCount() != 0 && System.currentTimeMillis() < end) Thread.sleep(50);
        assertEquals("released once it exited", 0, JoustTaskTracker.getInstance(getProject()).runningCount());
    }

    /** M2-CDX-32: IntelliAda's handler never destroys recursively (IntelliJ's recursive path tree-kills by PID). */
    public void testTheHandlerDoesNotDestroyRecursively() throws Exception {
        if (JoustTestTools.env("JOUST_NODE", getName()) == null) return;
        var root = npmRoot("web-flag", "setTimeout(() => {}, 100);\n");
        java.util.concurrent.atomic.AtomicReference<Boolean> recursive = new java.util.concurrent.atomic.AtomicReference<>();
        JoustNpmTasks.setConsoleOpenerForTests(h -> {
            try {
                var m = OSProcessHandler.class.getDeclaredMethod("shouldDestroyProcessRecursively");
                m.setAccessible(true);
                recursive.set((Boolean) m.invoke(h));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        var result = ApplicationManager.getApplication().executeOnPooledThread(() -> JoustNpmTasks.run(guard, root, List.of("build"), "build"));
        await(result, 30_000);
        assertEquals(Boolean.FALSE, recursive.get());
    }
}
