package com.bloxbean.intelliada.idea.joust.host;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * ADR-005 I20, D11 (tool-gated on {@code JOUST_HOST_FIXTURE}, a built JOUST Gradle Counter): a host started from a
 * trusted project's action is stopped, and its snapshot deleted, when trust is revoked; a declined confirmation starts
 * nothing and leaves no snapshot.
 */
public class JoustHostServicePlatformTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        JoustHostService.resetConfirmationsForTests();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            JoustHostService.confirmForTests = null;
            JoustHostService.resetConfirmationsForTests();
            TrustedProjects.setProjectTrusted(getProject(), true);
        } finally {
            super.tearDown();
        }
    }

    /** initialize's parameters without build (the service fills it from the snapshot). */
    static com.fasterxml.jackson.databind.node.ObjectNode init() {
        var n = HostJson.object();
        n.putArray("protocol").add(1);
        n.putObject("network").put("networkId", 0).put("protocolMagic", 42).put("mainnet", false);
        n.putObject("clock").put("zeroSlot", 0).put("zeroTimeMs", 1700000000000L).put("slotLengthMs", 1000);
        n.putObject("time").put("maxTtlSlots", 900).put("settlementSlots", 60).put("maxNowAheadSlots", 900);
        n.putObject("limits").put("callDeadlineMs", 60000).put("readsPerCall", 256).put("readBytesPerCall", 33554432);
        n.put("restoring", false);
        return n;
    }

    private static final HostSession.Reads NO_READS = (id, p) -> {
        throw new IllegalStateException("no reads");
    };

    public void testRevokingTrustStopsTheHostAndDeletesItsSnapshot() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "testRevokingTrustStopsTheHostAndDeletesItsSnapshot");
        if (fixture == null) return;
        TrustedProjects.setProjectTrusted(getProject(), true);
        JoustHostService.confirmForTests = (project, dir, spec, version) -> true;
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        List<String> ends = new CopyOnWriteArrayList<>();
        JoustHostService svc = JoustHostService.getInstance(getProject());
        // launch() confirms on the EDT, which is this test's thread: wait while dispatching events.
        JoustHostService.Running r = com.intellij.testFramework.PlatformTestUtil.waitForFuture(
                com.intellij.openapi.application.ApplicationManager.getApplication()
                        .executeOnPooledThread(() -> svc.launch(guard, Path.of(fixture), init(), NO_READS, ends::add)), 120_000);
        assertTrue(r.process().isAlive());
        assertTrue(Files.isDirectory(r.snapshot().root()));
        assertEquals("the JOUST version is read before launch", r.joustVersion() != null, true);
        assertEquals("handed out only after a verified initialize", 1, r.initialize().path("protocol").intValue());
        TrustedProjects.setProjectTrusted(getProject(), false);
        assertTrue("revoking trust stops the host", r.process().waitFor(20, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (Files.exists(r.snapshot().root()) && System.nanoTime() < deadline) Thread.sleep(50);
        assertFalse("the snapshot is deleted when the host ends", Files.exists(r.snapshot().root()));
        assertNull(svc.running(Path.of(fixture)));
        assertFalse("a stopped guard cannot start another host", guard.live());
    }

    public void testConcurrentStartsGiveOneHostAndTheTrackerReturnsToBaseline() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "testConcurrentStartsGiveOneHostAndTheTrackerReturnsToBaseline");
        if (fixture == null) return;
        TrustedProjects.setProjectTrusted(getProject(), true);
        JoustHostService.confirmForTests = (project, dir, spec, version) -> true;
        var tracker = com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker.getInstance(getProject());
        int baseline = tracker.runningCount();
        JoustHostService svc = JoustHostService.getInstance(getProject());
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        var app = com.intellij.openapi.application.ApplicationManager.getApplication();
        java.util.concurrent.Callable<Object> start = () -> {
            try {
                return svc.launch(guard, Path.of(fixture), init(), NO_READS, e -> { });
            } catch (JoustHostService.NotStarted e) {
                return e.getMessage();
            }
        };
        var f1 = app.executeOnPooledThread(start);
        var f2 = app.executeOnPooledThread(start);
        Object a = com.intellij.testFramework.PlatformTestUtil.waitForFuture(f1, 120_000);
        Object b = com.intellij.testFramework.PlatformTestUtil.waitForFuture(f2, 120_000);
        java.util.Set<Long> pids = new java.util.HashSet<>();
        for (Object o : new Object[]{a, b}) {
            if (o instanceof JoustHostService.Running r) pids.add(r.process().pid());
        }
        assertEquals("one host per module (M31A-CDX-3): " + a + " / " + b, 1, pids.size());
        JoustHostService.Running r = svc.running(Path.of(fixture));
        assertNotNull(r);
        svc.stop(Path.of(fixture));
        assertTrue(r.process().waitFor(20, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (tracker.runningCount() != baseline && System.nanoTime() < deadline) Thread.sleep(50);
        assertEquals("the tracker releases the run once the process exits (M31A-CDX-8)", baseline, tracker.runningCount());
        assertFalse(r.session().violated());
    }

    public void testClosingTheProjectStopsTheHostAndDeletesItsSnapshot() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "testClosingTheProjectStopsTheHostAndDeletesItsSnapshot");
        if (fixture == null) return;
        TrustedProjects.setProjectTrusted(getProject(), true);
        JoustHostService.confirmForTests = (project, dir, spec, version) -> true;
        JoustHostService svc = JoustHostService.getInstance(getProject());
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        JoustHostService.Running r = com.intellij.testFramework.PlatformTestUtil.waitForFuture(
                com.intellij.openapi.application.ApplicationManager.getApplication()
                        .executeOnPooledThread(() -> svc.launch(guard, Path.of(fixture), init(), NO_READS, e -> { })), 120_000);
        svc.dispose();   // what project close does to the service (I20)
        assertTrue(r.process().waitFor(20, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (Files.exists(r.snapshot().root()) && System.nanoTime() < deadline) Thread.sleep(50);
        assertFalse(Files.exists(r.snapshot().root()));
    }

    public void testADeclinedConfirmationStartsNothing() throws Exception {
        String fixture = JoustTestTools.env("JOUST_HOST_FIXTURE", "testADeclinedConfirmationStartsNothing");
        if (fixture == null) return;
        TrustedProjects.setProjectTrusted(getProject(), true);
        JoustHostService.confirmForTests = (project, dir, spec, version) -> false;
        JoustHostService svc = JoustHostService.getInstance(getProject());
        JoustRunGuard guard = JoustRunGuard.capture(getProject());
        var future = com.intellij.openapi.application.ApplicationManager.getApplication()
                .executeOnPooledThread(() -> {
                    try {
                        svc.launch(guard, Path.of(fixture), init(), NO_READS, e -> { });
                        return "launched";
                    } catch (JoustHostService.NotStarted e) {
                        return "not started";
                    }
                });
        assertEquals("a declined start launches nothing", "not started",
                com.intellij.testFramework.PlatformTestUtil.waitForFuture(future, 120_000));
        assertNull(svc.running(Path.of(fixture)));
        try (var list = Files.list(svc.snapshotsBase())) {
            assertEquals("no snapshot is left", 0, list.count());
        }
    }
}
