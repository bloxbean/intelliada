package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.NodeVersionProbe;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** M1-CDX-16: one deadline bounds the probe, output is capped, and a failing exit is a failure. */
public class JoustNodeProbeTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @org.junit.Before
    public void unixOnly() {
        // The fixtures are /bin/sh scripts (M1-CDX-20); the probe itself is platform-neutral.
        org.junit.Assume.assumeFalse(JoustTestTools.windows());
    }

    @Test
    public void answersAVersion() throws Exception {
        Path node = JoustTestFiles.executable(tmp.getRoot().toPath().resolve("ok/node"), "#!/bin/sh\necho v22.1.0\n");
        assertEquals("v22.1.0", NodeVersionProbe.probe(node));
    }

    @Test
    public void aSilentProcessKeepingStdoutOpenTimesOut() throws Exception {
        Path node = JoustTestFiles.executable(tmp.getRoot().toPath().resolve("hang/node"), "#!/bin/sh\nsleep 600\n");
        long t0 = System.nanoTime();
        try {
            NodeVersionProbe.probe(node);
            fail("a hanging probe returned");
        } catch (IOException expected) {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertTrue("bounded: " + ms + " ms", ms < 20_000);
        }
    }

    @Test
    public void aFailingExitIsAFailure() throws Exception {
        Path node = JoustTestFiles.executable(tmp.getRoot().toPath().resolve("bad/node"), "#!/bin/sh\necho v22.1.0\nexit 3\n");
        try {
            NodeVersionProbe.probe(node);
            fail("a failing probe was accepted");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("3"));
        }
    }

    /** M1-CDX-16: cancellation stops the child promptly, inside the deadline. */
    @Test
    public void cancellationStopsTheProbe() throws Exception {
        Path node = JoustTestFiles.executable(tmp.getRoot().toPath().resolve("slow/node"), "#!/bin/sh\nsleep 600\n");
        long t0 = System.nanoTime();
        try {
            NodeVersionProbe.probe(node, () -> System.nanoTime() - t0 > 300_000_000L);
            fail("a cancelled probe returned");
        } catch (IOException expected) {
            assertEquals("cancelled", expected.getMessage());
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 3_000);
        }
    }

    @Test
    public void outputIsCapped() throws Exception {
        Path node = JoustTestFiles.executable(tmp.getRoot().toPath().resolve("loud/node"),
                "#!/bin/sh\nyes v22.0.0 | head -c 5000000\n");
        String v = NodeVersionProbe.probe(node);
        assertTrue(v.length() <= 256);
    }
}
