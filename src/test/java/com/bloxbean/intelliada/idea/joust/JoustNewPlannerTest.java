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
        // A native executable stands in for the native joust: D4 now refuses scripts for every tool (M1-CDX-5).
        joust = JoustTestTools.nodeBinary(base.resolve("tools/joust/bin/joust"));
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
        return plan(t, l, pkg, target, s, Map.of("PATH", project + ":/usr/bin", "HOME", base.toString()), base);
    }

    private JoustNewPlanner.Plan plan(JoustTemplate t, JoustLanguage l, String pkg, Path target, JoustNewPlanner.ToolSettings s,
                                      Map<String, String> ideEnv, Path tempBase) {
        return JoustNewPlanner.plan(new JoustNewPlanner.Request(t, l, pkg, target.toString(), jdk.toString()), s,
                ideEnv, List.of(project), tempBase, flavor);
    }

    private void assertProjectEmpty() throws Exception {
        try (java.util.stream.Stream<Path> entries = Files.list(project)) {
            assertEquals("nothing was created inside the open project", List.of(), entries.toList());
        }
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
        // M2-CDX-11: a private directory under the given base is the working directory and the child's TMPDIR.
        assertEquals(base, p.workDir().getParent());
        assertTrue(p.workDir().getFileName().toString().startsWith("intelliada-joust-new-"));
        assertEquals(p.workDir().toString(), p.env().get("TMPDIR"));
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
        // Replaced by another native executable: still refused, by identity.
        Files.copy(Path.of(JoustTestTools.windows() ? "C:\\Windows\\System32\\hostname.exe" : "/bin/cat"), joust,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
        String line = JoustCliVersionProbe.probe(fake, base.resolve("ide-tmp"), List.of(project));
        assertEquals(before + 1, JoustCliVersionProbe.invocations());
        assertEquals("joust 9.9.9 (native) PATH=/usr/bin:/bin:/usr/sbin:/sbin NODE_OPTIONS=", line);
        assertEquals("9.9.9", JoustCliVersionProbe.versionOf(line));
        assertEquals("0.1.0-pre2", JoustCliVersionProbe.versionOf("joust 0.1.0-pre2 (native)"));
        assertNull(JoustCliVersionProbe.versionOf("v22.12.0"));
        assertNull(JoustCliVersionProbe.versionOf("Usage: something"));
        Path failing = JoustTestFiles.executable(base.resolve("tools/fake/bad"), "#!/bin/sh\nexit 3\n");
        try {
            JoustCliVersionProbe.probe(failing, base.resolve("ide-tmp"), List.of(project));
            fail("a failing joust was accepted");
        } catch (java.io.IOException expected) {
            // refused
        }
    }

    /** M2-CDX-17: a child that stays silent and never exits is stopped at the deadline; reading never blocks the probe. */
    @Test
    public void theVersionProbeStopsASilentChildAtItsDeadline() throws Exception {
        Path silent = JoustTestFiles.executable(base.resolve("tools/fake/silent"), "#!/bin/sh\nexec sleep 30\n");
        long start = System.nanoTime();
        try {
            JoustCliVersionProbe.probe(silent, base.resolve("ide-tmp"), List.of(project), 1_000);
            fail("a silent joust was accepted");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("did not finish"));
        }
        long ms = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue("the probe took " + ms + " ms", ms < 8_000);
        try (java.util.stream.Stream<Path> left = Files.list(base.resolve("ide-tmp"))) {
            assertEquals("the private directory is removed", 0, left.count());
        }
    }

    /** M2-CDX-11 (§12 E12): the probe's temporary base redirected into a project is refused before anything is created. */
    @Test
    public void theVersionProbeNeverCreatesItsDirectoryInsideAProject() throws Exception {
        Path fake = JoustTestFiles.executable(base.resolve("tools/fake/joust"), "#!/bin/sh\necho joust 9.9.9\n");
        Path link = Files.createSymbolicLink(base.resolve("ide-tmp-link"), project);
        for (Path tempBase : new Path[]{link, link.resolve("not/yet"), project.resolve("tmp")}) {
            try {
                JoustCliVersionProbe.probe(fake, tempBase, List.of(project));
                fail("a temporary base inside the project was used: " + tempBase);
            } catch (java.io.IOException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("open project"));
            }
            assertProjectEmpty();
        }
    }

    /** M2-CDX-11 (§12 E12): the wizard's private directory is never created in a project, however the base is spelled. */
    @Test
    public void thePlanRefusesATemporaryBaseThatResolvesIntoAProject() throws Exception {
        Path link = Files.createSymbolicLink(base.resolve("ide-tmp-link"), project);
        Map<String, String> env = Map.of("HOME", base.toString());
        for (Path tempBase : new Path[]{link, link.resolve("not/yet"), project}) {
            JoustNewPlanner.Plan p = plan(JoustTemplate.GRADLE, JoustLanguage.JSC, "app", base.resolve("t"), tools, env, tempBase);
            assertFalse("planned with a temporary base in the project: " + tempBase, p.ok());
            assertTrue(p.error(), p.error().contains("open project"));
            assertProjectEmpty();
        }
    }

    /** M2-CDX-11: inherited directory variables redirected into a project are not passed; TMPDIR is the private one. */
    @Test
    public void inheritedDirectoriesRedirectedIntoAProjectAreDropped() throws Exception {
        Path link = Files.createSymbolicLink(base.resolve("home-link"), project);
        Map<String, String> env = Map.of("HOME", link.toString(), "TMPDIR", link.toString());
        JoustNewPlanner.Plan p = plan(JoustTemplate.WEB, JoustLanguage.JSC, null, base.resolve("w"), tools, env, base);
        assertTrue(p.error(), p.ok());
        assertFalse(p.env().containsKey("HOME"));
        assertEquals(p.workDir().toString(), p.env().get("TMPDIR"));
        assertFalse(JoustPaths.isInside(p.workDir().toString(), project.toString(), flavor));
    }

    /** M2-CDX-10: a configured tool directory whose name holds the PATH separator is refused by the planner. */
    @Test
    public void aSeparatorInAConfiguredToolDirectoryIsRefused() throws Exception {
        Path odd = JoustTestTools.nodeBinary(base.resolve("tools/odd:" + project.resolve("bin") + "/joust"));
        JoustNewPlanner.ToolSettings s = new JoustNewPlanner.ToolSettings(odd.toString(), JoustToolValidator.identityOf(odd),
                null, null);
        JoustNewPlanner.Plan p = plan(JoustTemplate.GRADLE, JoustLanguage.JAVA, null, base.resolve("sep"), s);
        assertFalse("planned with " + odd, p.ok());
        assertTrue(p.error(), p.error().contains("path separator"));
        Path oddNode = JoustTestTools.nodeBinary(base.resolve("tools/n:ode/bin/node"));
        Files.createSymbolicLink(oddNode.resolveSibling("npm"), base.resolve("tools/node/lib/node_modules/npm/bin/npm-cli.js"));
        s = new JoustNewPlanner.ToolSettings(joust.toString(), tools.joustCli(), oddNode.toString(), JoustToolValidator.identityOf(oddNode));
        p = plan(JoustTemplate.NODE, JoustLanguage.JSC, null, base.resolve("sep2"), s);
        assertFalse(p.ok());
        assertTrue(p.error(), p.error().contains("path separator"));
        // And when accepted, the child's PATH splits into exactly the validated directories, in order.
        p = plan(JoustTemplate.NODE, JoustLanguage.JSC, null, base.resolve("ok"), tools);
        assertTrue(p.error(), p.ok());
        assertEquals(p.tools().path(), List.of(p.env().get("PATH").split(":", -1)));
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
