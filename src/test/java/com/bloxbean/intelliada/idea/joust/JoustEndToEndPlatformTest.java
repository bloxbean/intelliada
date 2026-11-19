package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.core.NodeVersionProbe;
import com.bloxbean.intelliada.idea.joust.lsp.JoustServerControl;
import com.bloxbean.intelliada.idea.joust.lsp.JoustStreamConnectionProvider;
import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.ServerStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * ADR-004 I1, I3, I4 and I20 end to end, in the IDE: IntelliAda's real settings, LSP4IJ and the user's real
 * {@code joust-lsp}. Opening a .jsc in a project full of planted executables starts exactly one process, with I3's
 * command line and no engine flag; nothing planted runs; a trust change restarts the server with the same command; no
 * {@code node --version} runs. Needs {@code JOUST_NODE} and {@code JOUST_LSP}; skipped otherwise.
 */
public class JoustEndToEndPlatformTest extends BasePlatformTestCase {

    private final List<List<String>> launches = new CopyOnWriteArrayList<>();
    private JoustToolsState.Data saved;

    @Override
    protected void tearDown() throws Exception {
        try {
            JoustStreamConnectionProvider.setLaunchRecorderForTests(null);
            if (saved != null) JoustToolsState.getInstance().loadState(saved);
            com.intellij.ide.trustedProjects.TrustedProjects.setProjectTrusted(getProject(), true);
        } finally {
            super.tearDown();
        }
    }

    private static java.util.Set<Long> descendants() {
        return ProcessHandle.current().descendants().filter(ProcessHandle::isAlive).map(ProcessHandle::pid)
                .collect(java.util.stream.Collectors.toSet());
    }

    public void testOpeningAJscStartsOnlyTheUsersServer() throws Exception {
        String node = JoustTestTools.env("JOUST_NODE", getName());
        String lsp = JoustTestTools.env("JOUST_LSP", getName());
        if (node == null || lsp == null) return;
        Path base = Files.createDirectories(Path.of(getProject().getBasePath())).toRealPath();
        Path jsc = base.resolve("contracts/app/Counter.jsc");
        Files.createDirectories(jsc.getParent());
        Files.writeString(jsc, "jsc 1\npackage app\n\ncontract Counter {\n  state { count: Int }\n  init() {\n    return { count: 0 }\n  }\n}\n");
        Files.writeString(base.resolve("joust.config.json"), "{\"contracts\":\"contracts\"}");
        Files.writeString(base.resolve("package.json"), "{\"name\":\"p\",\"devDependencies\":{\"@julclang/joust\":\"0.1.0-pre2\"}}");
        Path engine = Files.createDirectories(base.resolve("node_modules/@julclang/joust/engine"));
        Files.writeString(base.resolve("node_modules/@julclang/joust/package.json"), "{\"name\":\"@julclang/joust\",\"version\":\"0.1.0-pre2\"}");
        Files.writeString(engine.resolve("joust-toolchain.js"), "import('node:fs').then(f=>f.writeFileSync('" + base.resolve("SENTINEL-engine") + "','x'))\n");
        Files.write(engine.resolve("joust-toolchain.js.wasm"), new byte[]{0, 'a', 's', 'm', 1, 0, 0, 0});
        JoustTestFiles.executable(base.resolve("node_modules/.bin/joust-lsp"), "#!/bin/sh\ntouch '" + base.resolve("SENTINEL-bin") + "'\n");
        JoustTestFiles.executable(base.resolve("node"), "#!/bin/sh\ntouch '" + base.resolve("SENTINEL-node") + "'\n");
        JoustTestFiles.executable(base.resolve("gradlew"), "#!/bin/sh\ntouch '" + base.resolve("SENTINEL-gradle") + "'\n");

        JoustToolsState state = JoustToolsState.getInstance();
        saved = state.getState();
        JoustToolsState.Data d = new JoustToolsState.Data();
        d.nodePath = node;
        d.joustLspPath = lsp;
        state.loadState(d);
        state.setValidatedNode(JoustToolValidator.identityOf(Path.of(node).toRealPath()), "test");
        JoustStreamConnectionProvider.setLaunchRecorderForTests(launches::add);
        int probes = NodeVersionProbe.invocations();
        JoustProcessStarts starts = new JoustProcessStarts();
        java.util.Set<Long> before = descendants();
        // Untrusted first (T2): the same advisory command, nothing planted runs.
        com.intellij.ide.trustedProjects.TrustedProjects.setProjectTrusted(getProject(), false);

        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jsc);
        assertNotNull(vf);
        ApplicationManager.getApplication().invokeAndWait(() -> FileEditorManager.getInstance(getProject()).openFile(vf, true));
        PlatformTestUtil.waitWithEventsDispatching("the JOUST server starts", () -> JoustServerControl.status(getProject()) == ServerStatus.started, 60);
        assertEquals("one launch: " + launches, 1, launches.size());
        java.util.Set<Long> started = descendants();
        started.removeAll(before);
        assertEquals("one process at the JVM boundary (I1): " + started, 1, started.size());
        long firstPid = started.iterator().next();
        // A sync (VFS refresh and indexing) starts nothing (I1).
        com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base));
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        List<String> cmd = launches.get(0);
        assertEquals(List.of(Path.of(node).toRealPath().toString(),
                JoustToolValidator.resolveJoustLsp(lsp, List.of(base), com.bloxbean.intelliada.idea.joust.core.JoustPaths.Flavor.current()).realPath().toString(),
                "--stdio", "--storage", JoustStreamConnectionProvider.storageDir().toString()), cmd);

        // A real trust change restarts it with the same advisory command; the old process exits (I4, I20).
        com.intellij.ide.trustedProjects.TrustedProjects.setProjectTrusted(getProject(), true);
        PlatformTestUtil.waitWithEventsDispatching("the JOUST server restarts", () -> launches.size() >= 2
                && JoustServerControl.status(getProject()) == ServerStatus.started, 60);
        assertEquals("the restart uses the same advisory command", cmd, launches.get(1));
        PlatformTestUtil.waitWithEventsDispatching("the old process exits",
                () -> !ProcessHandle.of(firstPid).map(ProcessHandle::isAlive).orElse(false), 30);
        java.util.Set<Long> now = descendants();
        now.removeAll(before);
        assertEquals("still exactly one JOUST process: " + now, 1, now.size());

        Thread.sleep(3000);
        assertEquals("no node --version ran", probes, NodeVersionProbe.invocations());
        // Every process the JVM started, short-lived ones included (JFR, M1-CDX-10): the start and the restart only.
        java.util.List<String> commands = starts.commands();
        starts.close();
        assertEquals("two launches of joust-lsp: " + commands, 2, commands.size());
        for (String c : commands) assertTrue(c, c.contains("joust-lsp.js") && c.contains("--stdio"));
        try (Stream<Path> s = Files.walk(base)) {
            assertEquals("I1: nothing planted ran", List.of(), s.filter(p -> p.getFileName().toString().startsWith("SENTINEL")).toList());
        }
        ApplicationManager.getApplication().invokeAndWait(() -> FileEditorManager.getInstance(getProject()).closeFile(vf));
    }
}
