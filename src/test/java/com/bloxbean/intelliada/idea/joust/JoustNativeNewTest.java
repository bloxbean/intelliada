package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustCliVersionProbe;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.wizard.JoustLanguage;
import com.bloxbean.intelliada.idea.joust.wizard.JoustNewCommand;
import com.bloxbean.intelliada.idea.joust.wizard.JoustNewPlanner;
import com.bloxbean.intelliada.idea.joust.wizard.JoustNewRunner;
import com.bloxbean.intelliada.idea.joust.wizard.JoustTemplate;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * ADR-004 M2 exit criteria for the wizard (D9, I7), with the real native {@code joust}: set {@code JOUST_CLI} to its
 * absolute path, and {@code JOUST_NODE} to a Node 22+ binary for the Node and web templates. Without them the tests are
 * skipped with a {@code JOUST-SKIPPED} line; with {@code JOUST_REQUIRE_TOOLS=1} a missing tool, or an unreachable npm
 * registry, fails instead (M2-CDX-19, {@link JoustTestTools}). Optional: {@code JOUST_TEST_JAVA_HOME} (default: a JDK 25
 * under SDKMAN if present, else this JVM's home).
 *
 * <ul>
 *   <li>Gradle with {@code .jsc}, Gradle with Java, Node with {@code .jsc} and web with {@code .jsc} (no {@code -p}) are
 *   created into fresh directories by the command and environment the wizard builds. Node and web need npm and the
 *   network; without the network they are skipped with a message.</li>
 *   <li>Every run has an absolute directory of an open project <b>first on the user's PATH</b>, holding planted
 *   {@code node}, {@code npm}, {@code npx} and {@code java} that write a sentinel: no sentinel may appear (I7).</li>
 *   <li>A control run with the inherited environment shows the planted tools are reachable, so the sentinel check can
 *   fail.</li>
 * </ul>
 */
public class JoustNativeNewTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final JoustPaths.Flavor flavor = JoustPaths.Flavor.current();
    private Path cli;
    private Path base;
    private Path project;
    private Path sentinel;
    private String javaHome;
    private String nodePath;
    private Map<String, String> ideEnv;

    @Before
    public void setUp() throws Exception {
        String c = JoustTestTools.env("JOUST_CLI", "JoustNativeNewTest");
        Assume.assumeTrue("JOUST_CLI is not set: the native joust tests are skipped", c != null);
        Assume.assumeTrue("Unix only", File.separatorChar == '/');
        cli = Path.of(c);
        assertTrue("JOUST_CLI is not an executable file: " + c, Files.isExecutable(cli));
        base = tmp.getRoot().toPath().toRealPath();
        project = Files.createDirectories(base.resolve("open-project"));
        sentinel = base.resolve("SENTINEL");
        for (String n : new String[]{"node", "npm", "npx", "java"}) {
            JoustTestFiles.executable(project.resolve("bin/" + n), "#!/bin/sh\necho \"" + n + " $*\" >> '" + sentinel + "'\nexit 1\n");
        }
        javaHome = System.getenv("JOUST_TEST_JAVA_HOME");
        if (javaHome == null || javaHome.isBlank()) {
            Path sdkman = Path.of(System.getProperty("user.home"), ".sdkman/candidates/java/25.0.2-librca");
            javaHome = Files.isExecutable(sdkman.resolve("bin/java")) ? sdkman.toString() : System.getProperty("java.home");
        }
        nodePath = System.getenv("JOUST_NODE");   // required (or skipped) by the npm-template test only
        // The user's environment, with an absolute project directory first on PATH (the M2 criterion).
        ideEnv = new HashMap<>(System.getenv());
        ideEnv.put("PATH", project.resolve("bin") + ":" + System.getenv().getOrDefault("PATH", "/usr/bin:/bin"));
        ideEnv.put("NODE_OPTIONS", "--require " + project.resolve("evil.js"));
        ideEnv.put("JAVA_TOOL_OPTIONS", "-javaagent:" + project.resolve("evil.jar"));
    }

    private JoustNewPlanner.ToolSettings tools() throws Exception {
        Path cliReal = cli.toRealPath();
        boolean haveNode = nodePath != null && !nodePath.isBlank() && Files.isExecutable(Path.of(nodePath));
        return new JoustNewPlanner.ToolSettings(cli.toString(), JoustToolValidator.identityOf(cliReal),
                haveNode ? nodePath : null, haveNode ? JoustToolValidator.identityOf(Path.of(nodePath).toRealPath()) : null);
    }

    /** Plans with the wizard's planner, runs with the wizard's runner, and prints the exact command and environment. */
    private JoustNewRunner.Outcome create(JoustTemplate t, JoustLanguage l, String pkg, Path target) throws Exception {
        JoustNewPlanner.Plan plan = JoustNewPlanner.plan(new JoustNewPlanner.Request(t, l, pkg, target.toString(), javaHome),
                tools(), ideEnv, List.of(project), base, flavor);
        assertTrue(plan.error(), plan.ok());
        System.out.println("=== joust new (" + t.cliName() + ", " + l.cliName() + ")");
        System.out.println("command: " + JoustNewCommand.display(plan.command()));
        System.out.println("argv:    " + plan.command());
        System.out.println("cwd:     " + plan.workDir());
        System.out.println("env:     " + new java.util.TreeMap<>(plan.env()));
        for (String dir : plan.env().get("PATH").split(":")) {
            assertFalse("project dir on PATH: " + dir, JoustPaths.isInside(dir, project.toString(), flavor));
        }
        JoustNewRunner.Outcome o = JoustNewRunner.run(plan.command(), plan.env(), plan.workDir(), Duration.ofMinutes(5), () -> false);
        System.out.println("exit:    " + o.exitCode());
        System.out.println("output:\n" + o.output());
        return o;
    }

    @Test
    public void versionProbeAnswers() throws Exception {
        String line = JoustCliVersionProbe.probe(cli.toRealPath(), base.resolve("ide-tmp"), List.of(project));
        System.out.println("joust version: " + line);
        assertNotNull(line, JoustCliVersionProbe.versionOf(line));
    }

    @Test
    public void gradleJscAndGradleJava() throws Exception {
        Path jsc = base.resolve("projects/counter-jsc");
        Files.createDirectories(jsc.getParent());
        JoustNewRunner.Outcome o = create(JoustTemplate.GRADLE, JoustLanguage.JSC, "com.example.counter", jsc);
        assertTrue(o.output(), o.ok());
        for (String f : new String[]{"build.gradle", "settings.gradle", "gradlew", "gradle/wrapper/gradle-wrapper.properties",
                "src/main/jsc/com/example/counter/Counter.jsc", "src/main/jsc/com/example/counter/Counter.abi.json",
                "src/main/java/com/example/counter/CounterMain.java"}) {
            assertTrue("missing " + f, Files.isRegularFile(jsc.resolve(f)));
        }
        assertTrue(Files.readString(jsc.resolve("settings.gradle")).contains("counter-jsc"));
        assertTrue(Files.isExecutable(jsc.resolve("gradlew")));

        Path java = Files.createDirectories(base.resolve("projects/counter-java"));   // an existing, empty directory
        o = create(JoustTemplate.GRADLE, JoustLanguage.JAVA, "org.acme", java);
        assertTrue(o.output(), o.ok());
        assertTrue(Files.isRegularFile(java.resolve("src/main/java/org/acme/Counter.java")));
        assertFalse(Files.exists(java.resolve("src/main/jsc")));
        assertNoSentinel();
    }

    @Test
    public void nodeJscAndWebJsc() throws Exception {
        nodePath = JoustTestTools.env("JOUST_NODE", "JoustNativeNewTest.nodeJscAndWebJsc");
        Assume.assumeTrue("JOUST_NODE is not set", nodePath != null);
        assertTrue("JOUST_NODE is not an executable file: " + nodePath, Files.isExecutable(Path.of(nodePath)));
        if (!registryReachable()) {
            if (JoustTestTools.required()) {
                fail("The npm registry is not reachable, and the Node and web templates need it (JOUST_REQUIRE_TOOLS=1)");
            }
            System.out.println("JOUST-SKIPPED JoustNativeNewTest.nodeJscAndWebJsc: the npm registry is not reachable");
            Assume.assumeTrue("The npm registry is not reachable: the Node and web templates need the network; skipped", false);
        }
        Path node = base.resolve("projects/counter-node");
        Files.createDirectories(node.getParent());
        JoustNewRunner.Outcome o = create(JoustTemplate.NODE, JoustLanguage.JSC, null, node);
        assertTrue(o.output(), o.ok());
        for (String f : new String[]{"package.json", "joust.config.json", "contracts/app/Counter.jsc", "contracts/app/Counter.abi.json"}) {
            assertTrue("missing " + f, Files.isRegularFile(node.resolve(f)));
        }
        assertFalse("the node template has no page", Files.exists(node.resolve("page")));

        Path web = base.resolve("projects/counter-web");
        o = create(JoustTemplate.WEB, JoustLanguage.JSC, null, web);
        assertTrue(o.output(), o.ok());
        for (String f : new String[]{"package.json", "joust.config.json", "contracts/app/Counter.jsc", "page/index.html"}) {
            assertTrue("missing " + f, Files.isRegularFile(web.resolve(f)));
        }
        assertNoSentinel();
    }

    @Test
    public void theCliRefusesANonEmptyDirectoryAndMinusPForNode() throws Exception {
        // The CLI's own rules, which the wizard mirrors (F27): seen here through the real binary.
        Path full = Files.createDirectories(base.resolve("projects/full"));
        Files.writeString(full.resolve("keep.txt"), "x");
        JoustNewPlanner.Plan plan = JoustNewPlanner.plan(new JoustNewPlanner.Request(JoustTemplate.GRADLE, JoustLanguage.JAVA,
                null, base.resolve("projects/ok").toString(), javaHome), tools(), ideEnv, List.of(project), base, flavor);
        assertTrue(plan.error(), plan.ok());
        List<String> intoFull = new ArrayList<>(plan.command());
        intoFull.set(2, full.toString());
        JoustNewRunner.Outcome o = JoustNewRunner.run(intoFull, plan.env(), base, Duration.ofMinutes(1), () -> false);
        assertEquals(o.output(), 2, o.exitCode());
        assertTrue(o.output(), o.output().contains("is not empty"));
        List<String> nodeWithP = new ArrayList<>(List.of(plan.command().get(0), "new", base.resolve("projects/np").toString(),
                "-t", "node", "-l", "jsc", "-p", "com.example"));
        o = JoustNewRunner.run(nodeWithP, plan.env(), base, Duration.ofMinutes(1), () -> false);
        assertEquals(o.output(), 2, o.exitCode());
        assertTrue(o.output(), o.output().contains("-p/--package is for -t gradle only"));
        assertNoSentinel();
    }

    /**
     * The control: the same CLI with the <b>inherited</b> environment (the project directory first on PATH, no
     * JAVA_HOME) runs the planted {@code java} (Gradle's JDK check) and {@code npm} (the npm templates), so a sentinel
     * appears. This is what the built environment prevents.
     */
    @Test
    public void controlTheInheritedEnvironmentWouldRunThePlantedTools() throws Exception {
        Map<String, String> inherited = new HashMap<>(ideEnv);
        inherited.remove("JAVA_HOME");
        inherited.remove("NODE_OPTIONS");
        inherited.remove("JAVA_TOOL_OPTIONS");
        List<String> cmd = List.of(cli.toRealPath().toString(), "new", base.resolve("projects/control").toString(),
                "-t", "gradle", "-l", "java");
        JoustNewRunner.Outcome o = JoustNewRunner.run(cmd, inherited, base, Duration.ofMinutes(1), () -> false);
        System.out.println("control output:\n" + o.output());
        assertTrue("the planted java was not reached: the sentinel test could not fail", Files.exists(sentinel));
        assertTrue(Files.readString(sentinel).startsWith("java"));
        Files.delete(sentinel);
        cmd = List.of(cli.toRealPath().toString(), "new", base.resolve("projects/control-node").toString(), "-t", "node", "-l", "jsc");
        o = JoustNewRunner.run(cmd, inherited, base, Duration.ofMinutes(1), () -> false);
        System.out.println("control output (node):\n" + o.output());
        assertTrue("the planted npm was not reached", Files.exists(sentinel));
        assertTrue(Files.readString(sentinel).startsWith("npm"));
    }

    private void assertNoSentinel() throws Exception {
        assertFalse("a planted tool ran: " + (Files.exists(sentinel) ? Files.readString(sentinel) : ""), Files.exists(sentinel));
    }

    private static boolean registryReachable() {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create("https://registry.npmjs.org/create-joust").toURL().openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestMethod("HEAD");
            int code = c.getResponseCode();
            c.disconnect();
            return code > 0 && code < 500;
        } catch (Exception e) {
            System.out.println("npm registry not reachable: " + e);
            return false;
        }
    }
}
