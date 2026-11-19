package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.settings.JoustToolDetector;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** ADR-004 D4's Detect…: directory reads only, version-manager shims skipped. */
public class JoustLaunchOptionsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void detectorReadsLayoutsAndSkipsShims() throws Exception {
        Path home = tmp.getRoot().toPath().toRealPath();
        Path nvmNode = JoustTestFiles.executable(home.resolve(".nvm/versions/node/v22.12.0/bin/node"), "x");
        JoustTestFiles.executable(home.resolve(".volta/bin/node"), "shim");
        JoustTestFiles.executable(home.resolve(".asdf/shims/node"), "shim");
        Path pathDir = Files.createDirectories(home.resolve("custom/bin"));
        Path pathNode = JoustTestFiles.executable(pathDir.resolve("node"), "x");
        String path = String.join(File.pathSeparator, home.resolve(".volta/bin").toString(), home.resolve(".asdf/shims").toString(),
                pathDir.toString(), "relative/bin");
        List<Path> found = JoustToolDetector.nodeCandidates(Map.of("PATH", path), home, JoustPaths.Flavor.current());
        assertTrue(found.toString(), found.contains(nvmNode));
        assertTrue(found.contains(pathNode));
        assertFalse(found.toString(), found.stream().anyMatch(p -> p.toString().contains(".volta/bin") || p.toString().contains(".asdf/shims")));
    }
}
