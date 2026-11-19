package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustCliVersionProbe;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.core.NodeVersionProbe;
import com.bloxbean.intelliada.idea.joust.wizard.JoustLanguage;
import com.bloxbean.intelliada.idea.joust.wizard.JoustNewPlanner;
import com.bloxbean.intelliada.idea.joust.wizard.JoustTemplate;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** ADR-004 D9: what the wizard decides before running {@code joust new}: tool identities, the target, the plan. */
public class JoustNewPlannerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final JoustPaths.Flavor flavor = JoustPaths.Flavor.current();
    private Path base;
    private Path joust;
    private Path node;
    private Path jdk;
    private Path project;
    private JoustNewPlanner.ToolSettings tools;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("Unix file system", File.separatorChar == '/');
        base = tmp.getRoot().toPath().toRealPath();
        joust = JoustTestFiles.executable(base.resolve("tools/joust/bin/joust"), "#!/bin/sh\necho joust 0.1.0-pre2 native\n");
        node = JoustTestTools.nodeBinary(base.resolve("tools/node/bin/node"));
        Path npmCli = JoustTestFiles.executable(base.resolve("tools/node/lib/node_modules/npm/bin/npm-cli.js"), "#!/bin/sh\n");
        Files.createSymbolicLink(base.resolve("tools/node/bin/npm"), npmCli);
        jdk = base.resolve("jdk");
        JoustTestFiles.executable(jdk.resolve("bin/java"), "#!/bin/sh\n");
        project = Files.createDirectories(base.resolve("open-project"));
        tools = new JoustNewPlanner.ToolSettings(joust.toString(), JoustToolValidator.identityOf(joust),
                node.toString(), JoustToolValidator.identityOf(node));
    }

    private JoustNewPlanner.Plan plan(JoustTemplate t, JoustLanguage l, String pkg, Path target, JoustNewPlanner.ToolSettings s) {
        return JoustNewPlanner.plan(new JoustNewPlanner.Request(t, l, pkg, target.toString(), jdk.toString()), s,
                Map.of("PATH", project + ":/usr/bin", "HOME", base.toString()), List.of(project), base, flavor);
    }

    @Test
    public void plansTheCommandOnTheValidatedRealPath() throws Exception {
        int nodeProbes = NodeVersionProbe.invocations();
        int cliProbes = JoustCliVersionProbe.invocations();
        Path target = base.resolve("new/counter");
        JoustNewPlanner.Plan p = plan(JoustTemplate.GRADLE, JoustLanguage.JSC, "com.example", target, tools);
        assertTrue(p.error(), p.ok());
        assertEquals(List.of(joust.toString(), "new", target.toString(), "-t", "gradle", "-l", "jsc", "-p", "com.example"), p.command());
        assertEquals(jdk.toString(), p.env().get("JAVA_HOME"));
        assertFalse(p.env().get("PATH").contains(project.toString()));
        assertEquals(base, p.workDir());
        assertEquals("planning runs nothing", nodeProbes, NodeVersionProbe.invocations());
        assertEquals("planning runs nothing", cliProbes, JoustCliVersionProbe.invocations());
    }

    @Test
    public void aSymlinkedJoustIsRunByItsRealPath() throws Exception {
        Path link = Files.createSymbolicLink(base.resolve("joust-link"), joust);
        JoustNewPlanner.ToolSettings s = new JoustNewPlanner.ToolSettings(link.toString(), tools.joustCli(), null, null);
        JoustNewPlanner.Plan p = plan(JoustTemplate.GRADLE, JoustLanguage.JAVA, null, base.resolve("c"), s);
        assertTrue(p.error(), p.ok());
        assertEquals(joust.toString(), p.command().get(0));
    }

    @Test
    public void nodeAndWebNeverGetThePackage() {
        for (JoustTemplate t : new JoustTemplate[]{JoustTemplate.NODE, JoustTemplate.WEB}) {
            JoustNewPlanner.Plan p = plan(t, JoustLanguage.JSC, "com.example", base.resolve("n-" + t.cliName()), tools);
            assertTrue(p.error(), p.ok());
            assertFalse(p.command().contains("-p"));
            assertEquals(t.cliName(), p.command().get(4));
        }
    }

    @Test
    public void refusesAnUnvalidatedOrChangedJoust() throws Exception {
        JoustNewPlanner.ToolSettings unvalidated = new JoustNewPlanner.ToolSettings(joust.toString(), null, null, null);
        JoustNewPlanner.Plan p = plan(JoustTemplate.GRADLE, JoustLanguage.JAVA, null, base.resolve("a"), unvalidated);
        assertFalse(p.ok());
        assertTrue(p.error(), p.error().contains("has not been validated"));
        Files.writeString(joust, "#!/bin/sh\necho replaced\n");
        p = plan(JoustTemplate.GRADLE, JoustLanguage.JAVA, null, base.resolve("b"), tools);
        assertFalse(p.ok());
        assertTrue(p.error(), p.error().contains("changed since it was validated"));
        JoustNewPlanner.ToolSettings none = new JoustNewPlanner.ToolSettings("", null, null, null);
        assertFalse(plan(JoustTemplate.GRADLE, JoustLanguage.JAVA, null, base.resolve("c"), none).ok());
    }

    @Test
    public void aChangedNodeMattersOnlyToTheNpmTemplates() throws Exception {
        Files.writeString(node, "#!/bin/sh\necho other\n");
        assertTrue(plan(JoustTemplate.GRADLE, JoustLanguage.JAVA, null, base.resolve("g"), tools).ok());
        JoustNewPlanner.Plan p = plan(JoustTemplate.WEB, JoustLanguage.JSC, null, base.resolve("w"), tools);
        assertFalse(p.ok());
        assertTrue(p.error(), p.error().contains("Node"));
    }

    @Test
    public void theTargetMustBeNewOrEmpty() throws Exception {
        Path empty = Files.createDirectories(base.resolve("empty"));
        assertTrue(plan(JoustTemplate.GRADLE, JoustLanguage.JAVA, null, empty, tools).ok());
        Path full = Files.createDirectories(base.resolve("full"));
        Files.writeString(full.resolve(".idea"), "x");
        JoustNewPlanner.Plan p = plan(JoustTemplate.GRADLE, JoustLanguage.JAVA, null, full, tools);
        assertFalse(p.ok());
        assertTrue(p.error(), p.error().contains("not empty"));
        Path file = Files.writeString(base.resolve("afile"), "x");
        assertFalse(plan(JoustTemplate.GRADLE, JoustLanguage.JAVA, null, file, tools).ok());
    }

    @Test
    public void theVersionProbeRunsWithABuiltEnvironmentInAnEmptyDirectory() throws Exception {
        Path fake = JoustTestFiles.executable(base.resolve("tools/fake/joust"),
                "#!/bin/sh\necho \"joust 9.9.9 (native) PATH=$PATH NODE_OPTIONS=$NODE_OPTIONS\"\nls -A\n");
        int before = JoustCliVersionProbe.invocations();
        String line = JoustCliVersionProbe.probe(fake);
        assertEquals(before + 1, JoustCliVersionProbe.invocations());
        assertEquals("joust 9.9.9 (native) PATH=/usr/bin:/bin:/usr/sbin:/sbin NODE_OPTIONS=", line);
        assertEquals("9.9.9", JoustCliVersionProbe.versionOf(line));
        assertEquals("0.1.0-pre2", JoustCliVersionProbe.versionOf("joust 0.1.0-pre2 (native)"));
        assertNull(JoustCliVersionProbe.versionOf("v22.12.0"));
        assertNull(JoustCliVersionProbe.versionOf("Usage: something"));
        Path failing = JoustTestFiles.executable(base.resolve("tools/fake/bad"), "#!/bin/sh\nexit 3\n");
        try {
            JoustCliVersionProbe.probe(failing);
            fail("a failing joust was accepted");
        } catch (java.io.IOException expected) {
            // refused
        }
    }

    @Test
    public void preflightChecksThePackageAndTheTools() {
        Map<String, String> env = Map.of("HOME", base.toString());
        assertNull(JoustNewPlanner.preflight(new JoustNewPlanner.Request(JoustTemplate.GRADLE, JoustLanguage.JSC, "app", null,
                jdk.toString()), tools, env, List.of(project), flavor));
        assertNotNull(JoustNewPlanner.preflight(new JoustNewPlanner.Request(JoustTemplate.GRADLE, JoustLanguage.JSC, "a.class",
                null, jdk.toString()), tools, env, List.of(project), flavor));
        assertNotNull(JoustNewPlanner.preflight(new JoustNewPlanner.Request(JoustTemplate.GRADLE, JoustLanguage.JSC, "app",
                null, null), tools, env, List.of(project), flavor));
        // The joust is inside an open project: refused.
        assertNotNull(JoustNewPlanner.preflight(new JoustNewPlanner.Request(JoustTemplate.GRADLE, JoustLanguage.JSC, "app",
                null, jdk.toString()), tools, env, List.of(base.resolve("tools")), flavor));
    }
}
