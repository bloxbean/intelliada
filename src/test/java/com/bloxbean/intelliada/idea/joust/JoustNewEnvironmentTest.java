package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.wizard.JoustNewEnvironment;
import com.bloxbean.intelliada.idea.joust.wizard.JoustTemplate;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * ADR-004 D9 and I7 (§6.0 "M2: the wizard's child environment"): {@link JoustNewEnvironment} builds the environment of
 * {@code joust new}; it never inherits {@code PATH}, and D4's rule applies to the {@code node}, {@code npm} and
 * {@code java} the CLI will pick. Unix cases use real files and symlinks; Windows forms use a fake file view through the
 * flavor parameter. Nothing is executed.
 */
public class JoustNewEnvironmentTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path base;
    private Path nodeBin;
    private Path node;
    private Path joust;
    private Path jdk;
    private Path project;
    private Map<String, String> ide;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("Unix file system", File.separatorChar == '/');
        base = tmp.getRoot().toPath().toRealPath();
        // A Node install as npm lays it out: bin/node, and bin/npm -> ../lib/node_modules/npm/bin/npm-cli.js
        nodeBin = base.resolve("tools/node/bin");
        node = JoustTestFiles.executable(nodeBin.resolve("node"), "#!/bin/sh\n");
        Path npmCli = JoustTestFiles.executable(base.resolve("tools/node/lib/node_modules/npm/bin/npm-cli.js"), "#!/usr/bin/env node\n");
        Files.createSymbolicLink(nodeBin.resolve("npm"), npmCli);
        Path npxCli = JoustTestFiles.executable(base.resolve("tools/node/lib/node_modules/npm/bin/npx-cli.js"), "#!/usr/bin/env node\n");
        Files.createSymbolicLink(nodeBin.resolve("npx"), npxCli);
        joust = JoustTestFiles.executable(base.resolve("tools/joust/bin/joust"), "#!/bin/sh\n");
        jdk = base.resolve("jdks/jdk-25");
        JoustTestFiles.executable(jdk.resolve("bin/java"), "#!/bin/sh\n");
        // An open project, with tools planted in it, first on the user's PATH.
        project = Files.createDirectories(base.resolve("repo"));
        for (String n : new String[]{"node", "npm", "npx", "java", "joust"}) {
            JoustTestFiles.executable(project.resolve("bin/" + n), "#!/bin/sh\ntouch " + base.resolve("sentinel") + "\n");
        }
        ide = new LinkedHashMap<>();
        ide.put("PATH", project.resolve("bin") + ":" + nodeBin + ":/usr/bin:/bin");
        ide.put("HOME", base.resolve("home").toString());
        ide.put("TMPDIR", base.resolve("tmp").toString());
        ide.put("LANG", "en_US.UTF-8");
        ide.put("https_proxy", "http://proxy.example:3128");
        ide.put("NODE_OPTIONS", "--require " + project.resolve("evil.js"));
        ide.put("JAVA_TOOL_OPTIONS", "-javaagent:" + project.resolve("evil.jar"));
        ide.put("npm_config_registry", "http://evil.example");
        ide.put("NODE_PATH", project.toString());
        ide.put("CLASSPATH", project.toString());
        ide.put("JAVA_HOME", project.toString());
    }

    private JoustNewEnvironment.Result build(JoustTemplate t) {
        return build(t, node.toString(), joust.toString(), jdk.toString());
    }

    private JoustNewEnvironment.Result build(JoustTemplate t, String nodePath, String joustPath, String javaHome) {
        return JoustNewEnvironment.build(new JoustNewEnvironment.Inputs(joustPath, nodePath, javaHome, t, ide,
                List.of(project.toString())), JoustNewEnvironment.localFiles(), JoustPaths.Flavor.current());
    }

    private static List<String> systemDirs() throws IOException {
        List<String> out = new ArrayList<>();
        for (String d : new String[]{"/usr/bin", "/bin", "/usr/sbin", "/sbin"}) {
            if (Files.isDirectory(Path.of(d))) {
                String real = Path.of(d).toRealPath().toString();
                if (!out.contains(real)) out.add(real);
            }
        }
        return out;
    }

    @Test
    public void nodeTemplateGetsOnlyTheValidatedDirectories() throws Exception {
        JoustNewEnvironment.Result r = build(JoustTemplate.NODE);
        assertTrue(r.error(), r.ok());
        List<String> expected = new ArrayList<>(List.of(nodeBin.toString(), joust.getParent().toString()));
        expected.addAll(systemDirs());
        assertEquals(expected, r.path());
        assertEquals(String.join(":", expected), r.env().get("PATH"));
        assertEquals(jdk.toString(), r.env().get("JAVA_HOME"));
        assertEquals(node.toString(), r.node());
        assertEquals(base.resolve("tools/node/lib/node_modules/npm/bin/npm-cli.js").toString(), r.npm());
        assertEquals(jdk.resolve("bin/java").toString(), r.java());
        assertEquals(joust.toString(), r.joust());
        for (String dir : r.path()) {
            assertFalse("project dir on PATH: " + dir, JoustPaths.isInside(dir, project.toString(), JoustPaths.Flavor.current()));
        }
    }

    @Test
    public void theUsersPathWithAProjectDirectoryFirstIsNeverUsed() {
        // I7: the user's PATH starts with an absolute directory inside the project; the built PATH does not contain it.
        JoustNewEnvironment.Result r = build(JoustTemplate.WEB);
        assertTrue(r.error(), r.ok());
        assertFalse(r.env().get("PATH").contains(project.toString()));
        assertFalse(r.path().contains(project.resolve("bin").toString()));
        for (String v : r.env().values()) assertFalse("project path passed: " + v, v.contains(project.toString()));
    }

    @Test
    public void onlyAllowListedVariablesArePassed() {
        JoustNewEnvironment.Result r = build(JoustTemplate.NODE);
        assertTrue(r.error(), r.ok());
        Map<String, String> env = r.env();
        assertEquals(base.resolve("home").toString(), env.get("HOME"));
        assertEquals(base.resolve("tmp").toString(), env.get("TMPDIR"));
        assertEquals("en_US.UTF-8", env.get("LANG"));
        assertEquals("http://proxy.example:3128", env.get("https_proxy"));
        for (String k : new String[]{"NODE_OPTIONS", "JAVA_TOOL_OPTIONS", "npm_config_registry", "NODE_PATH", "CLASSPATH"}) {
            assertFalse(k + " passed", env.containsKey(k));
        }
        assertEquals("JAVA_HOME is the chosen JDK, not the IDE's", jdk.toString(), env.get("JAVA_HOME"));
    }

    @Test
    public void pathVariablesInsideAProjectAreDropped() {
        ide.put("TMPDIR", project.resolve("tmp").toString());
        ide.put("HOME", "relative/home");
        JoustNewEnvironment.Result r = build(JoustTemplate.GRADLE);
        assertTrue(r.error(), r.ok());
        assertFalse(r.env().containsKey("TMPDIR"));
        assertFalse(r.env().containsKey("HOME"));
    }

    @Test
    public void gradleTemplateLeavesNodeOut() throws Exception {
        JoustNewEnvironment.Result r = build(JoustTemplate.GRADLE);
        assertTrue(r.error(), r.ok());
        List<String> expected = new ArrayList<>(List.of(joust.getParent().toString()));
        expected.addAll(systemDirs());
        assertEquals(expected, r.path());
        assertNull(r.node());
        assertNull(r.npm());
        // and Gradle needs no Node at all
        assertTrue(build(JoustTemplate.GRADLE, null, joust.toString(), jdk.toString()).ok());
    }

    @Test
    public void npmSymlinkIntoAProjectIsRefused() throws Exception {
        Files.delete(nodeBin.resolve("npm"));
        Files.createSymbolicLink(nodeBin.resolve("npm"), project.resolve("bin/npm"));
        JoustNewEnvironment.Result r = build(JoustTemplate.NODE);
        assertFalse(r.ok());
        assertTrue(r.error(), r.error().contains("npm") && r.error().contains("inside an open project"));
        // The Node directory is not on a Gradle template's PATH, so Gradle is unaffected.
        assertTrue(build(JoustTemplate.GRADLE).ok());
    }

    @Test
    public void npmUnderNodeModulesBinIsRefused() throws Exception {
        Path bin = JoustTestFiles.executable(base.resolve("elsewhere/node_modules/.bin/npm"), "#!/bin/sh\n");
        Files.delete(nodeBin.resolve("npm"));
        Files.createSymbolicLink(nodeBin.resolve("npm"), bin);
        JoustNewEnvironment.Result r = build(JoustTemplate.WEB);
        assertFalse(r.ok());
        assertTrue(r.error(), r.error().contains("node_modules/.bin"));
    }

    @Test
    public void javaInAPermittedDirectoryPointingIntoAProjectIsRefused() throws Exception {
        Files.createSymbolicLink(joust.getParent().resolve("java"), project.resolve("bin/java"));
        for (JoustTemplate t : JoustTemplate.values()) {
            JoustNewEnvironment.Result r = build(t);
            assertFalse(t + " accepted", r.ok());
            assertTrue(r.error(), r.error().contains("java") && r.error().contains(joust.getParent().toString()));
        }
    }

    @Test
    public void nodeOrNpxBesideNodePointingIntoAProjectIsRefused() throws Exception {
        Files.delete(nodeBin.resolve("npx"));
        Files.createSymbolicLink(nodeBin.resolve("npx"), project.resolve("bin/npx"));
        assertFalse(build(JoustTemplate.NODE).ok());
        assertTrue("npx is not in a Gradle PATH", build(JoustTemplate.GRADLE).ok());
        Files.createSymbolicLink(joust.getParent().resolve("Node"), project.resolve("bin/node"));
        if (JoustPaths.Flavor.current() == JoustPaths.Flavor.MAC) {
            assertFalse("case-insensitive name on macOS", build(JoustTemplate.GRADLE).ok());
        }
    }

    @Test
    public void javaHomeJavaPointingIntoAProjectIsRefused() throws Exception {
        Files.delete(jdk.resolve("bin/java"));
        Files.createSymbolicLink(jdk.resolve("bin/java"), project.resolve("bin/java"));
        JoustNewEnvironment.Result r = build(JoustTemplate.GRADLE);
        assertFalse(r.ok());
        assertTrue(r.error(), r.error().contains("java") && r.error().contains("inside an open project"));
    }

    @Test
    public void jdkHomeInsideAProjectOrMissingIsRefused() throws Exception {
        Path inProject = project.resolve("jdk");
        JoustTestFiles.executable(inProject.resolve("bin/java"), "#!/bin/sh\n");
        assertFalse(build(JoustTemplate.GRADLE, null, joust.toString(), inProject.toString()).ok());
        Path link = Files.createSymbolicLink(base.resolve("jdk-link"), inProject);
        assertFalse(build(JoustTemplate.GRADLE, null, joust.toString(), link.toString()).ok());
        assertFalse(build(JoustTemplate.GRADLE, null, joust.toString(), base.resolve("nope").toString()).ok());
        assertFalse(build(JoustTemplate.GRADLE, null, joust.toString(), "jdks/jdk-25").ok());
        assertFalse(build(JoustTemplate.GRADLE, null, joust.toString(), null).ok());
    }

    @Test
    public void toolsInsideAProjectOrNotAbsoluteAreRefused() throws Exception {
        assertFalse(build(JoustTemplate.GRADLE, null, project.resolve("bin/joust").toString(), jdk.toString()).ok());
        assertFalse(build(JoustTemplate.GRADLE, null, "joust", jdk.toString()).ok());
        assertFalse(build(JoustTemplate.GRADLE, null, null, jdk.toString()).ok());
        Path cmd = JoustTestFiles.executable(base.resolve("tools/joust/bin/joust.cmd"), "@echo off\n");
        assertFalse(build(JoustTemplate.GRADLE, null, cmd.toString(), jdk.toString()).ok());
        Path joustLink = Files.createSymbolicLink(base.resolve("joust-link"), project.resolve("bin/joust"));
        assertFalse(build(JoustTemplate.GRADLE, null, joustLink.toString(), jdk.toString()).ok());
        assertFalse(build(JoustTemplate.NODE, project.resolve("bin/node").toString(), joust.toString(), jdk.toString()).ok());
        assertFalse(build(JoustTemplate.NODE, "node", joust.toString(), jdk.toString()).ok());
        assertFalse("Node is required for node", build(JoustTemplate.NODE, null, joust.toString(), jdk.toString()).ok());
        Path binJoust = JoustTestFiles.executable(base.resolve("x/node_modules/.bin/joust"), "#!/bin/sh\n");
        assertFalse(build(JoustTemplate.GRADLE, null, binJoust.toString(), jdk.toString()).ok());
    }

    @Test
    public void npmMustComeWithTheConfiguredNode() throws Exception {
        Files.delete(nodeBin.resolve("npm"));
        JoustNewEnvironment.Result r = build(JoustTemplate.NODE);
        assertFalse(r.ok());
        assertTrue(r.error(), r.error().contains("No npm was found beside the configured Node"));
        // even when the joust directory has one
        Path otherNpm = JoustTestFiles.executable(base.resolve("other/npm-cli.js"), "#!/bin/sh\n");
        Files.createSymbolicLink(joust.getParent().resolve("npm"), otherNpm);
        assertFalse(build(JoustTemplate.NODE).ok());
    }

    @Test
    public void aSymlinkedNodeRunsFromItsRealDirectory() throws Exception {
        Path shimDir = Files.createDirectories(base.resolve("usr-local-bin"));
        Path link = Files.createSymbolicLink(shimDir.resolve("node"), node);
        JoustNewEnvironment.Result r = build(JoustTemplate.NODE, link.toString(), joust.toString(), jdk.toString());
        assertTrue(r.error(), r.ok());
        assertEquals(nodeBin.toString(), r.path().get(0));
        assertFalse(r.path().contains(shimDir.toString()));
    }

    @Test
    public void aDanglingEntryIsIgnored() throws Exception {
        Files.createSymbolicLink(joust.getParent().resolve("java"), base.resolve("gone/java"));
        assertTrue(build(JoustTemplate.GRADLE).ok());
    }

    // ---- Windows forms, through the flavor parameter and a fake file view

    /** A case-insensitive file view of Windows paths: files, directories and symbolic links. */
    static final class WinFs implements JoustNewEnvironment.FileView {
        final Map<String, String> files = new HashMap<>();      // key -> spelled path
        final Map<String, String> dirs = new HashMap<>();
        final Map<String, String> links = new HashMap<>();      // key -> target
        final Map<String, String> linkNames = new HashMap<>();  // key -> spelled path
        final java.util.Set<String> notExecutable = new java.util.HashSet<>();

        static String key(String p) {
            String k = p.replace('/', '\\').toLowerCase(Locale.ROOT);
            if (k.startsWith("\\\\?\\unc\\")) k = "\\\\" + k.substring(8);
            else if (k.startsWith("\\\\?\\")) k = k.substring(4);
            while (k.endsWith("\\") && k.length() > 3) k = k.substring(0, k.length() - 1);
            return k;
        }

        WinFs dir(String p) {
            dirs.put(key(p), p);
            return this;
        }

        WinFs file(String p) {
            files.put(key(p), p);
            return this;
        }

        WinFs link(String p, String target) {
            links.put(key(p), target);
            linkNames.put(key(p), p);
            return this;
        }

        @Override
        public String realPath(String path) throws IOException {
            String k = key(path);
            for (int i = 0; i < 10 && links.containsKey(k); i++) k = key(links.get(k));
            if (files.containsKey(k)) return files.get(k);
            if (dirs.containsKey(k)) return dirs.get(k);
            throw new IOException("no such file: " + path);
        }

        @Override
        public boolean exists(String path) {
            try {
                realPath(path);
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public boolean isDirectory(String realPath) {
            return dirs.containsKey(key(realPath));
        }

        @Override
        public boolean isRegularFile(String realPath) {
            return files.containsKey(key(realPath));
        }

        @Override
        public boolean isExecutable(String realPath) {
            return files.containsKey(key(realPath)) && !notExecutable.contains(key(realPath));
        }

        @Override
        public List<String> list(String dir) {
            String d = key(dir) + "\\";
            List<String> out = new ArrayList<>();
            for (Map<String, String> m : List.of(files, dirs)) {
                for (String spelled : m.values()) add(out, d, spelled);
            }
            for (String spelled : linkNames.values()) add(out, d, spelled);
            return out;
        }

        private static void add(List<String> out, String dirKey, String path) {
            String k = key(path);
            if (k.startsWith(dirKey) && k.indexOf('\\', dirKey.length()) < 0) {
                String name = path.substring(path.replace('/', '\\').lastIndexOf('\\') + 1);
                if (!out.contains(name)) out.add(name);
            }
        }
    }

    private static final String NODE_DIR = "C:\\Program Files\\nodejs";

    private static WinFs windows() {
        WinFs fs = new WinFs()
                .dir(NODE_DIR).file(NODE_DIR + "\\node.exe").file(NODE_DIR + "\\npm").file(NODE_DIR + "\\npm.cmd")
                .file(NODE_DIR + "\\npx.cmd").dir(NODE_DIR + "\\node_modules\\npm\\bin")
                .dir("C:\\tools\\joust").file("C:\\tools\\joust\\joust.exe")
                .dir("C:\\jdks\\jdk-25").dir("C:\\jdks\\jdk-25\\bin").file("C:\\jdks\\jdk-25\\bin\\java.exe")
                .dir("C:\\Windows").dir("C:\\Windows\\System32").dir("C:\\Windows\\System32\\Wbem")
                .dir("C:\\Windows\\System32\\WindowsPowerShell\\v1.0")
                .dir("C:\\work\\repo").dir("C:\\work\\repo\\bin").file("C:\\work\\repo\\bin\\npm.js")
                .file("C:\\work\\repo\\bin\\java.exe")
                .dir("\\\\srv\\share\\repo").file("\\\\srv\\share\\repo\\java.exe");
        return fs;
    }

    private static JoustNewEnvironment.Result buildWin(WinFs fs, JoustTemplate t, String joustPath, String nodePath) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("Path", "C:\\work\\repo\\bin;" + NODE_DIR + ";C:\\Windows\\System32");
        env.put("SYSTEMROOT", "C:\\Windows");
        env.put("USERPROFILE", "C:\\Users\\u");
        env.put("TEMP", "C:\\work\\repo\\tmp");
        env.put("NODE_OPTIONS", "--require C:\\work\\repo\\evil.js");
        return JoustNewEnvironment.build(new JoustNewEnvironment.Inputs(joustPath, nodePath, "C:\\jdks\\jdk-25", t, env,
                List.of("C:\\work\\repo", "\\\\srv\\share\\repo")), fs, JoustPaths.Flavor.WINDOWS);
    }

    @Test
    public void windowsBuildsASemicolonPathFromSystemRoot() {
        JoustNewEnvironment.Result r = buildWin(windows(), JoustTemplate.NODE, "C:\\tools\\joust\\joust.exe", NODE_DIR + "\\node.exe");
        assertTrue(r.error(), r.ok());
        assertEquals(NODE_DIR + ";C:\\tools\\joust;C:\\Windows\\System32;C:\\Windows;C:\\Windows\\System32\\Wbem;"
                + "C:\\Windows\\System32\\WindowsPowerShell\\v1.0", r.env().get("PATH"));
        assertEquals("C:\\jdks\\jdk-25", r.env().get("JAVA_HOME"));
        assertEquals("C:\\jdks\\jdk-25\\bin\\java.exe", r.java());
        assertEquals(NODE_DIR + "\\npm", r.npm());
        assertEquals("C:\\Windows", r.env().get("SystemRoot"));
        assertEquals("C:\\Users\\u", r.env().get("USERPROFILE"));
        assertFalse("TEMP inside the project", r.env().containsKey("TEMP"));
        assertFalse(r.env().containsKey("NODE_OPTIONS"));
    }

    @Test
    public void windowsNpmLinkIntoAProjectIsRefusedInAnyForm() {
        for (String target : new String[]{"C:\\work\\repo\\bin\\npm.js", "c:/WORK/Repo/bin/npm.js", "\\\\?\\C:\\work\\repo\\bin\\npm.js"}) {
            WinFs fs = windows().link(NODE_DIR + "\\npm", target);
            fs.files.remove(WinFs.key(NODE_DIR + "\\npm"));
            JoustNewEnvironment.Result r = buildWin(fs, JoustTemplate.WEB, "C:\\tools\\joust\\joust.exe", NODE_DIR + "\\node.exe");
            assertFalse(target, r.ok());
            assertTrue(r.error(), r.error().contains("inside an open project"));
        }
    }

    @Test
    public void windowsShimNamesAreScannedInAnyCase() {
        WinFs fs = windows().link("C:\\tools\\joust\\NPX.CMD", "\\\\?\\C:\\work\\repo\\bin\\npm.js");
        JoustNewEnvironment.Result r = buildWin(fs, JoustTemplate.GRADLE, "C:\\tools\\joust\\joust.exe", null);
        assertFalse(r.ok());
        assertTrue(r.error(), r.error().contains("NPX.CMD"));
    }

    @Test
    public void windowsJavaIntoAUncProjectIsRefused() {
        WinFs fs = windows().link("C:\\tools\\joust\\java.exe", "\\\\?\\UNC\\srv\\share\\repo\\java.exe");
        JoustNewEnvironment.Result r = buildWin(fs, JoustTemplate.GRADLE, "C:\\tools\\joust\\joust.exe", null);
        assertFalse(r.ok());
        assertTrue(r.error(), r.error().contains("java.exe"));
        WinFs fs2 = windows().link("C:\\jdks\\jdk-25\\bin\\java.exe", "C:\\work\\repo\\bin\\java.exe");
        fs2.files.remove(WinFs.key("C:\\jdks\\jdk-25\\bin\\java.exe"));
        assertFalse(buildWin(fs2, JoustTemplate.GRADLE, "C:\\tools\\joust\\joust.exe", null).ok());
    }

    @Test
    public void windowsRefusesShimsRelativeAndProjectTools() {
        WinFs fs = windows().file("C:\\tools\\joust\\joust.cmd").file("C:\\tools\\joust\\joust.bat.");
        assertFalse(buildWin(fs, JoustTemplate.GRADLE, "C:\\tools\\joust\\joust.cmd", null).ok());
        assertFalse(buildWin(fs, JoustTemplate.GRADLE, "C:\\tools\\joust\\JOUST.BAT.", null).ok());
        assertFalse(buildWin(fs, JoustTemplate.GRADLE, "joust.exe", null).ok());
        assertFalse(buildWin(fs, JoustTemplate.GRADLE, "C:joust.exe", null).ok());
        assertFalse(buildWin(fs, JoustTemplate.NODE, "C:\\tools\\joust\\joust.exe", "node.exe").ok());
        WinFs fs2 = windows().file("C:\\work\\repo\\bin\\joust.exe");
        assertFalse(buildWin(fs2, JoustTemplate.GRADLE, "c:\\WORK\\repo\\bin\\joust.exe", null).ok());
        assertTrue(buildWin(windows(), JoustTemplate.GRADLE, "C:\\tools\\joust\\joust.exe", null).ok());
    }
}
