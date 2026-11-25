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
                        .executeOnPooledThread(() -> svc.launch(guard, Path.of(fixture), NO_READS, ends::add)), 120_000);
        assertTrue(r.process().isAlive());
        assertTrue(Files.isDirectory(r.snapshot().root()));
        assertEquals("the JOUST version is read before launch", r.joustVersion() != null, true);
        TrustedProjects.setProjectTrusted(getProject(), false);
        assertTrue("revoking trust stops the host", r.process().waitFor(20, TimeUnit.SECONDS));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (Files.exists(r.snapshot().root()) && System.nanoTime() < deadline) Thread.sleep(50);
        assertFalse("the snapshot is deleted when the host ends", Files.exists(r.snapshot().root()));
        assertNull(svc.running(Path.of(fixture)));
        assertFalse("a stopped guard cannot start another host", guard.live());
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
                        svc.launch(guard, Path.of(fixture), NO_READS, e -> { });
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
