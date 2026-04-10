package com.bloxbean.intelliada.idea.nodeint.yano;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for YanoProcessManager without requiring a running Yano instance.
 */
class YanoProcessManagerTest {

    @Test
    void testInitialStatusIsStopped() {
        YanoProcessManager manager = new YanoProcessManager("/tmp/yano");
        assertEquals(YanoProcessManager.YanoStatus.STOPPED, manager.getStatus());
    }

    @Test
    void testDefaultPort() {
        YanoProcessManager manager = new YanoProcessManager("/tmp/yano");
        assertEquals(7070, manager.getPort());
    }

    @Test
    void testCustomPort() {
        YanoProcessManager manager = new YanoProcessManager("/tmp/yano", 9090);
        assertEquals(9090, manager.getPort());
    }

    @Test
    void testBaseUrl() {
        YanoProcessManager manager = new YanoProcessManager("/tmp/yano", 8080);
        assertEquals("http://localhost:8080", manager.getBaseUrl());
    }

    @Test
    void testBaseUrlDefaultPort() {
        YanoProcessManager manager = new YanoProcessManager("/tmp/yano");
        assertEquals("http://localhost:7070", manager.getBaseUrl());
    }

    @Test
    void testIsHealthyWhenNotRunning() {
        YanoProcessManager manager = new YanoProcessManager("/tmp/nonexistent");
        assertFalse(manager.isHealthy());
    }

    @Test
    void testIsProcessAliveWhenNotStarted() {
        YanoProcessManager manager = new YanoProcessManager("/tmp/yano");
        assertFalse(manager.isProcessAlive());
    }

    @Test
    void testStopWhenAlreadyStopped(@TempDir Path tempDir) {
        YanoProcessManager manager = new YanoProcessManager(tempDir.toString());
        var future = manager.stopYano();
        assertTrue(future.join());
        assertEquals(YanoProcessManager.YanoStatus.STOPPED, manager.getStatus());
    }

    @Test
    void testStartWithMissingBinaries(@TempDir Path tempDir) {
        // No yano-node.jar or yano-node binary in temp dir
        YanoProcessManager manager = new YanoProcessManager(tempDir.toString());
        var future = manager.startYano();

        // Should fail because neither binary nor jar exists
        boolean result = future.join();
        assertFalse(result);
    }

    @Test
    void testStartWithFakeJar(@TempDir Path tempDir) throws Exception {
        // Create a fake yano-node.jar (just an empty file)
        File fakeJar = tempDir.resolve("yano-node.jar").toFile();
        fakeJar.createNewFile();

        YanoProcessManager manager = new YanoProcessManager(tempDir.toString());
        // Start will try to run java -jar on the fake jar, which will fail
        var future = manager.startYano();

        // Should eventually fail because the fake jar isn't runnable
        // But it shouldn't throw an exception
        assertNotNull(future);
    }

    @Test
    void testYanoStatusEnumValues() {
        YanoProcessManager.YanoStatus[] values = YanoProcessManager.YanoStatus.values();
        assertEquals(5, values.length);
        assertNotNull(YanoProcessManager.YanoStatus.STOPPED);
        assertNotNull(YanoProcessManager.YanoStatus.STARTING);
        assertNotNull(YanoProcessManager.YanoStatus.RUNNING);
        assertNotNull(YanoProcessManager.YanoStatus.STOPPING);
        assertNotNull(YanoProcessManager.YanoStatus.ERROR);
    }
}
