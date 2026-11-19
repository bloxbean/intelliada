package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustLaunch;
import com.bloxbean.intelliada.idea.joust.core.JoustLaunchPlanner;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.core.NodeIdentity;
import com.bloxbean.intelliada.idea.joust.core.NodeVersionProbe;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.Assert.*;

/** ADR-004 I3, I4, I26 and CDX-25: the exact command line, no engine flag, and an identity re-check that executes nothing. */
public class JoustLaunchPlannerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final JoustPaths.Flavor flavor = JoustPaths.Flavor.current();
    private Path node;
    private Path lspShim;
    private Path lspJs;
    private Path project;
    private Path storage;
    private NodeIdentity identity;

    @Before
    public void setUp() throws Exception {
        Path base = tmp.getRoot().toPath().toRealPath();
        node = JoustTestTools.nodeBinary(base.resolve("tools/node"));
        lspJs = JoustTestFiles.joustPackage(base.resolve("tools/npm"), "@julclang/joust", "0.1.0-pre2");
        lspShim = base.resolve("tools/npm/bin/joust-lsp");
        project = Files.createDirectories(base.resolve("repo"));
        storage = base.resolve("system/intelliada/joust-lsp");
        identity = JoustToolValidator.identityOf(node.toRealPath());
    }

    @Test
    public void commandIsExactlyI3() throws Exception {
        int probes = NodeVersionProbe.invocations();
        var plan = JoustLaunchPlanner.plan(new JoustLaunchPlanner.Settings(node.toString(), lspShim.toString(), identity),
                List.of(project), storage, flavor);
        assertTrue(plan.error(), plan.ok());
        assertEquals(List.of(node.toRealPath().toString(), lspJs.toRealPath().toString(), "--stdio", "--storage", storage.toString()),
                plan.launch().command());
        assertEquals(lspJs.toRealPath().getParent(), plan.launch().workingDirectory());
        for (String never : JoustLaunch.NEVER) assertFalse(never, plan.launch().command().contains(never));
        assertFalse(plan.launch().command().stream().anyMatch(a -> a.startsWith("--project-engine") || a.startsWith("--engine")));
        assertEquals("planning at start executes nothing", probes, NodeVersionProbe.invocations());
    }

    @Test
    public void changedNodeIsRefusedWithoutRunningIt() throws Exception {
        int probes = NodeVersionProbe.invocations();
        // Same length, same mtime: only the SHA-256 differs, so the hash check alone must refuse it (M1-DES-10).
        byte[] bytes = Files.readAllBytes(node);
        bytes[bytes.length / 2] ^= 0x5A;
        Files.write(node, bytes);
        Files.setLastModifiedTime(node, FileTime.fromMillis(identity.modifiedMillis()));
        assertEquals(identity.size(), Files.size(node));
        assertEquals(identity.modifiedMillis(), Files.getLastModifiedTime(node).toMillis());
        var plan = JoustLaunchPlanner.plan(new JoustLaunchPlanner.Settings(node.toString(), lspShim.toString(), identity),
                List.of(project), storage, flavor);
        assertFalse(plan.ok());
        assertTrue(plan.needsRevalidation());
        assertEquals(probes, NodeVersionProbe.invocations());
    }

    @Test
    public void unvalidatedNodeIsRefused() {
        var plan = JoustLaunchPlanner.plan(new JoustLaunchPlanner.Settings(node.toString(), lspShim.toString(), null),
                List.of(project), storage, flavor);
        assertFalse(plan.ok());
        assertTrue(plan.needsRevalidation());
    }

    @Test
    public void storageInsideAProjectIsRefused() {
        var plan = JoustLaunchPlanner.plan(new JoustLaunchPlanner.Settings(node.toString(), lspShim.toString(), identity),
                List.of(project), project.resolve(".joust"), flavor);
        assertFalse(plan.ok());
    }

    @Test
    public void storageThroughASymlinkIntoAProjectIsRefusedWithoutWriting() throws Exception {
        Path system = tmp.getRoot().toPath().toRealPath().resolve("system2");
        Files.createDirectories(system);
        Files.createSymbolicLink(system.resolve("intelliada"), project);
        List<String> before = listing(project);
        var plan = JoustLaunchPlanner.plan(new JoustLaunchPlanner.Settings(node.toString(), lspShim.toString(), identity),
                List.of(project), system.resolve("intelliada/joust-lsp"), flavor);
        assertFalse(plan.ok());
        assertEquals("the project is unchanged", before, listing(project));
    }

    @Test
    public void storageIsPassedAsItsRealPath() throws Exception {
        Path real = Files.createDirectories(tmp.getRoot().toPath().toRealPath().resolve("realsys"));
        Path link = Files.createSymbolicLink(tmp.getRoot().toPath().toRealPath().resolve("linksys"), real);
        var plan = JoustLaunchPlanner.plan(new JoustLaunchPlanner.Settings(node.toString(), lspShim.toString(), identity),
                List.of(project), link.resolve("joust-lsp"), flavor);
        assertTrue(plan.error(), plan.ok());
        assertEquals(real.resolve("joust-lsp").toString(), plan.launch().command().get(4));
    }

    private static List<String> listing(Path dir) throws Exception {
        try (var s = Files.walk(dir)) {
            return s.map(Path::toString).sorted().toList();
        }
    }

    @Test
    public void toolsInsideANewlyOpenedProjectAreRefused() {
        var plan = JoustLaunchPlanner.plan(new JoustLaunchPlanner.Settings(node.toString(), lspShim.toString(), identity),
                List.of(project, node.getParent()), storage, flavor);
        assertFalse(plan.ok());
    }
}
