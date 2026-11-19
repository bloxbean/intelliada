package com.bloxbean.intelliada.idea.joust.settings;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.core.JoustCliVersionProbe;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * M2-CDX-17 (round 2): Settings | Tools | JOUST takes the native joust's identity (a SHA-256 of a binary that can be
 * hundreds of MB) in the background task with {@code joust version}, never on the EDT; a file that changes between the
 * identity and the probe is refused, as for Node (M1-DES-9).
 */
public class JoustCliProbeIdentityPlatformTest extends BasePlatformTestCase {

    private Path dir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        dir = Files.createTempDirectory("joust-cli-probe-").toRealPath();
    }

    private Path script(String name, String body) throws IOException {
        Path p = dir.resolve("tools").resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, body);
        assertTrue(p.toFile().setExecutable(true));
        return p;
    }

    private JoustToolsConfigurable.ProbedCli inBackground(Path joust) throws Exception {
        // A plain background thread (the IDE's pooled executor would log the expected refusal as an error).
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            Future<JoustToolsConfigurable.ProbedCli> f = pool.submit(
                    () -> JoustToolsConfigurable.probeCli(joust, dir.resolve("ide-tmp"), List.of(dir.resolve("project"))));
            return f.get(30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof Exception x) throw x;
            throw e;
        } finally {
            pool.shutdownNow();
        }
    }

    public void testTheIdentityIsTakenWithTheProbeOffTheEdt() throws Exception {
        if (JoustTestTools.windows()) return;
        Path joust = script("joust", "#!/bin/sh\necho 'joust 9.9.9 (native)'\n");
        JoustToolsConfigurable.ProbedCli r = inBackground(joust);
        assertEquals("joust 9.9.9 (native)", r.line());
        assertEquals(JoustToolValidator.identityOf(joust), r.identity());
    }

    public void testAJoustThatChangesDuringTheProbeIsRefused() throws Exception {
        if (JoustTestTools.windows()) return;
        Path joust = script("joust", "#!/bin/sh\necho 'joust 9.9.9 (native)'\nprintf '# changed\\n' >> \"$0\"\n");
        try {
            inBackground(joust);
            fail("a joust that changed between its identity and its probe was accepted");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("changed while it was being checked"));
        }
    }

    public void testTheProbeAndItsHashNeverRunOnTheEdt() throws Exception {
        if (JoustTestTools.windows()) return;
        assertTrue("this platform test runs on the EDT", ApplicationManager.getApplication().isDispatchThread());
        Path joust = script("joust", "#!/bin/sh\necho 'joust 9.9.9 (native)'\n");
        int probes = JoustCliVersionProbe.invocations();
        Throwable refused = null;
        try {
            JoustToolsConfigurable.probeCli(joust, dir.resolve("ide-tmp"), List.of());
        } catch (Throwable t) {
            refused = t;
        }
        assertNotNull("probeCli ran on the EDT", refused);
        assertFalse(String.valueOf(refused), refused instanceof IOException);
        assertEquals("nothing was run from the EDT", probes, JoustCliVersionProbe.invocations());
    }
}
