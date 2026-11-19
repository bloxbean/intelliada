package com.bloxbean.intelliada.idea.joust.project;

import com.bloxbean.intelliada.idea.joust.project.actions.JoustGradleTaskAction;
import com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** ADR-004 M2 (D1, D7, I2, I18, I20) in the IDE. */
public class JoustM2PlatformTest extends BasePlatformTestCase {

    private Path base;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        base = Files.createDirectories(Path.of(getProject().getBasePath())).toRealPath();
        try (var old = Files.list(base)) {
            for (Path p : old.toList()) com.intellij.openapi.util.io.FileUtil.delete(p);   // the light project's directory is shared
        }
        Path src = Path.of(getClass().getClassLoader().getResource("joust-m2/SOURCE.txt").toURI()).getParent().resolve("gradle-jsc");
        try (var s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path t = base.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base).refresh(false, true);
        com.bloxbean.intelliada.idea.joust.project.JoustProjectService.getInstance(getProject()).invalidate();
        TrustedProjects.setProjectTrusted(getProject(), true);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            TrustedProjects.setProjectTrusted(getProject(), true);
        } finally {
            super.tearDown();
        }
    }

    private VirtualFile vf(String rel) {
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base.resolve(rel));
    }

    public void testGeneratedJavaIsMarkedAndLeadsToItsJsc() throws Exception {
        VirtualFile probe = vf("build/generated/sources/jsc/java/main/org/acme/Counter.java");
        assertFalse("a cold cache never scans inside the filter (M2-DES-8)", GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(probe, getProject()));
        JoustProjectService.getInstance(getProject()).layout();
        VirtualFile java = vf("build/generated/sources/jsc/java/main/org/acme/Counter.java");
        assertTrue(GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(java, getProject()));
        assertFalse(GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(vf("src/main/jsc/org/acme/Counter.jsc"), getProject()));
        assertNotNull(new JoustGeneratedBannerProvider().collectNotificationData(getProject(), java));
        assertNull(new JoustGeneratedBannerProvider().collectNotificationData(getProject(), vf("build.gradle")));
        var target = JoustGeneratedBannerProvider.targetFor(getProject(), java, 30);
        assertNotNull(target);
        assertEquals(base.resolve("src/main/jsc/org/acme/Counter.jsc"), target.jsc());
    }

    public void testConsoleLinksOpenTheJsc() throws Exception {
        String line = "org/acme/Counter.jsc:17:12: error[JSC0331]: State needs note";
        var filter = new JoustConsoleFilterProvider.JoustDiagnosticFilter(getProject());
        var result = filter.applyFilter(line, line.length());
        assertNotNull(result);
        var item = result.getResultItems().get(0);
        assertEquals("org/acme/Counter.jsc", line.substring(item.getHighlightStartOffset(), item.getHighlightEndOffset()));
        var opened = ((JoustConsoleFilterProvider.JoustLinkInfo) item.getHyperlinkInfo()).open(getProject());
        for (int i = 0; i < 100 && !opened.isDone(); i++) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(50);
        }
        assertTrue(opened.get());
        assertEquals("Counter.jsc", FileEditorManager.getInstance(getProject()).getSelectedFiles()[0].getName());
        assertNull(filter.applyFilter("../../etc/passwd.jsc:1:1: error[JSC0502]: x", 50));
    }



    public void testLosingTrustCancelsRunningTasks() {
        JoustTaskTracker tracker = JoustTaskTracker.getInstance(getProject());
        AtomicBoolean cancelled = new AtomicBoolean();
        int g = tracker.generation();
        JoustTaskTracker.Handle handle = () -> cancelled.set(true);
        tracker.register(handle);
        TrustedProjects.setProjectTrusted(getProject(), false);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertTrue("I20: cancelled on loss of trust", cancelled.get());
        assertFalse("late results are discarded", tracker.current(g));
        assertEquals("still tracked until its owner says its work ended (M2-CDX-3)", 1, tracker.runningCount());
        tracker.done(handle);
        assertEquals(0, tracker.runningCount());
    }

    /** D8, I8: IntelliJ's Gradle runner hands the bound accept arguments to Gradle unchanged and in order. */
    public void testAcceptArgumentsStayOneTaskForGradle() throws Exception {
        String diff = new String(getClass().getClassLoader().getResourceAsStream("joust-m2/jscAbiDiff-out.json").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        var d = com.bloxbean.intelliada.idea.joust.abi.JoustAbi.parse(diff);
        var args = com.bloxbean.intelliada.idea.joust.abi.JoustAbi.gradleAcceptArgs(":app", d);
        var cl = org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine.Companion.parse(args);
        // GradleExecutionHelper.setupBuildLauncherArguments passes getTasks().getTokens() to BuildLauncher.forTasks, in
        // order, as it does for ':test --tests X'. The bound arguments reach Gradle unchanged, with no build options.
        assertEquals(args, cl.getTasks().getTokens());
        assertEquals(java.util.List.of(), cl.getOptions().getTokens());
        assertEquals(":app:acceptJscAbi", cl.getTasks().iterator().next().getName());
    }



    /** D7 for npm (M2-CDX-14): generated Java under build/joust/generated is marked and leads to its .jsc. */
    public void testNpmGeneratedJavaIsMarkedAndLeadsToItsJsc() throws Exception {
        Path src = Path.of(getClass().getClassLoader().getResource("joust-m2/SOURCE.txt").toURI()).getParent().resolve("npm-jsc");
        Path web = base.resolve("web");
        try (var s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path t = web.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base).refresh(false, true);
        JoustProjectService.getInstance(getProject()).layout();
        VirtualFile java = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(web.resolve("build/joust/generated/app/Counter.java"));
        assertTrue(GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(java, getProject()));
        assertNotNull(new JoustGeneratedBannerProvider().collectNotificationData(getProject(), java));
        var target = JoustGeneratedBannerProvider.targetFor(getProject(), java, 26);
        assertNotNull(target);
        assertEquals(web.resolve("contracts/app/Counter.jsc"), target.jsc());
    }

    /** I20, M2-DES-3: closing the project kills its running npm CLI and discards the result. */
    public void testClosingTheProjectKillsARunningNpmCli() throws Exception {
        String node = com.bloxbean.intelliada.idea.joust.JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        var state = com.bloxbean.intelliada.idea.joust.settings.JoustToolsState.getInstance();
        var saved = state.getState();
        Path dir = Files.createTempDirectory("joust-close").toRealPath();
        try {
            var d = new com.bloxbean.intelliada.idea.joust.settings.JoustToolsState.Data();
            d.nodePath = node;
            state.loadState(d);
            state.setValidatedNode(com.bloxbean.intelliada.idea.joust.core.JoustToolValidator.identityOf(Path.of(node).toRealPath()), "test");
            Files.writeString(dir.resolve("joust.config.json"), "{}");
            Path bin = Files.createDirectories(dir.resolve("node_modules/@julclang/joust/bin"));
            Files.writeString(bin.getParent().resolve("package.json"), "{\"name\":\"@julclang/joust\"}");
            Files.writeString(bin.resolve("joust.js"), "require('fs').writeFileSync('pid', String(process.pid)); setTimeout(() => {}, 120000);\n");
            TrustedProjects.setProjectTrusted(dir, true);
            com.intellij.openapi.project.Project p = PlatformTestUtil.loadAndOpenProject(dir, getTestRootDisposable());
            var root = com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout.scan(dir,
                    com.bloxbean.intelliada.idea.joust.core.JoustPaths.Flavor.current()).npmRoots().get(0);
            var guard = com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard.capture(p);
            var result = ApplicationManager.getApplication().executeOnPooledThread(
                    () -> com.bloxbean.intelliada.idea.joust.tasks.JoustNpmTasks.run(guard, root, java.util.List.of("build"), "build"));
            Path pid = dir.resolve("pid");
            for (int i = 0; i < 200 && !Files.exists(pid); i++) {
                PlatformTestUtil.dispatchAllEventsInIdeEventQueue();   // the CLI starts after the editors are saved on the EDT
                Thread.sleep(50);
            }
            long id = Long.parseLong(Files.readString(pid).trim());
            PlatformTestUtil.forceCloseProjectWithoutSaving(p);
            assertTrue("cancelled", result.get(20, java.util.concurrent.TimeUnit.SECONDS).cancelled());
            for (int i = 0; i < 100 && ProcessHandle.of(id).map(ProcessHandle::isAlive).orElse(false); i++) Thread.sleep(50);
            assertFalse("killed on close", ProcessHandle.of(id).map(ProcessHandle::isAlive).orElse(false));
        } finally {
            state.loadState(saved);
        }
    }
}
