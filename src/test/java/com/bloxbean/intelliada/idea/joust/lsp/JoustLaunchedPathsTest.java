package com.bloxbean.intelliada.idea.joust.lsp;

import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** M1-CDX-29: launched paths belong to each live provider, by identity, not to LSP4IJ's command-based equality. */
public class JoustLaunchedPathsTest {

    @Test
    public void stoppingOneOfTwoIdenticalServersKeepsTheOther() throws Exception {
        Path sleep = Path.of("/bin/sleep");
        Assume.assumeTrue("needs /bin/sleep", Files.isExecutable(sleep));
        List<String> cmd = List.of(sleep.toString(), "30");
        Path tool = Path.of("/opt/old-install/node");
        JoustStreamConnectionProvider a = JoustStreamConnectionProvider.startedForTests(cmd, List.of(tool));
        JoustStreamConnectionProvider b = JoustStreamConnectionProvider.startedForTests(cmd, List.of(tool));
        try {
            assertEquals("LSP4IJ considers them equal", a, b);
            assertTrue(a.isAlive() && b.isAlive());
            a.stop();
            assertTrue(b.isAlive());
            assertEquals("the surviving server's original tool is still known", Set.of(tool), JoustStreamConnectionProvider.launchedPaths());
        } finally {
            a.stop();
            b.stop();
        }
        assertEquals(Set.of(), JoustStreamConnectionProvider.launchedPaths());
    }
}
