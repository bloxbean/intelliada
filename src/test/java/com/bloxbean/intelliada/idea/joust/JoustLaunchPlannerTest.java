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
        node = JoustTestFiles.executable(base.resolve("tools/node"), "#!/bin/sh\necho v22.0.0\n");
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
        Files.writeString(node, "#!/bin/sh\necho evil\n");
        var plan = JoustLaunchPlanner.plan(new JoustLaunchPlanner.Settings(node.toString(), lspShim.toString(), identity),
                List.of(project), storage, flavor);
        assertFalse(plan.ok());
        assertTrue(plan.needsRevalidation());
        Files.setLastModifiedTime(node, FileTime.fromMillis(identity.modifiedMillis()));
        assertFalse(JoustLaunchPlanner.plan(new JoustLaunchPlanner.Settings(node.toString(), lspShim.toString(), identity),
                List.of(project), storage, flavor).ok());
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
    public void toolsInsideANewlyOpenedProjectAreRefused() {
        var plan = JoustLaunchPlanner.plan(new JoustLaunchPlanner.Settings(node.toString(), lspShim.toString(), identity),
                List.of(project, node.getParent()), storage, flavor);
        assertFalse(plan.ok());
    }
}
