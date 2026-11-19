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
        Path src = Path.of(getClass().getClassLoader().getResource("joust-m2/SOURCE.txt").toURI()).getParent().resolve("gradle-jsc");
        try (var s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path t = base.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base).refresh(false, true);
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
        ApplicationManager.getApplication().invokeAndWait(() -> item.getHyperlinkInfo().navigate(getProject()));
        assertEquals("Counter.jsc", FileEditorManager.getInstance(getProject()).getSelectedFiles()[0].getName());
        assertNull(filter.applyFilter("../../etc/passwd.jsc:1:1: error[JSC0502]: x", 50));
    }

    public void testActionsAreDisabledInAnUntrustedProjectAndStartNothing() throws Exception {
        VirtualFile jsc = vf("src/main/jsc/org/acme/Counter.jsc");
        var action = new JoustGradleTaskAction.Generate();
        var ctx = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, getProject()).add(CommonDataKeys.VIRTUAL_FILE, jsc).build();
        AnActionEvent e = TestActionEvent.createTestEvent(action, ctx);
        action.update(e);
        assertTrue("shown for a JOUST module", e.getPresentation().isVisible());
        assertTrue(e.getPresentation().isEnabled());
        TrustedProjects.setProjectTrusted(getProject(), false);
        AnActionEvent e2 = TestActionEvent.createTestEvent(action, ctx);
        action.update(e2);
        assertTrue(e2.getPresentation().isVisible());
        assertFalse("disabled when untrusted (D1, I2)", e2.getPresentation().isEnabled());
        try (var starts = new com.bloxbean.intelliada.idea.joust.JoustProcessStarts()) {
            action.actionPerformed(TestActionEvent.createTestEvent(action, ctx));
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(1000);
            assertEquals("nothing started", java.util.List.of(), starts.commands());
        }
        assertEquals(0, JoustTaskTracker.getInstance(getProject()).runningCount());
    }

    public void testLosingTrustCancelsRunningTasks() {
        JoustTaskTracker tracker = JoustTaskTracker.getInstance(getProject());
        AtomicBoolean cancelled = new AtomicBoolean();
        int g = tracker.generation();
        tracker.register(() -> cancelled.set(true));
        TrustedProjects.setProjectTrusted(getProject(), false);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertTrue("I20: cancelled on loss of trust", cancelled.get());
        assertFalse("late results are discarded", tracker.current(g));
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

    /** I20 with a real process: losing trust while the project's npm CLI runs kills it and discards its result. */
    public void testLosingTrustKillsARunningNpmBuild() throws Exception {
        String node = com.bloxbean.intelliada.idea.joust.JoustTestTools.env("JOUST_NODE", getName());
        if (node == null) return;
        var state = com.bloxbean.intelliada.idea.joust.settings.JoustToolsState.getInstance();
        var saved = state.getState();
        try {
            var d = new com.bloxbean.intelliada.idea.joust.settings.JoustToolsState.Data();
            d.nodePath = node;
            state.loadState(d);
            state.setValidatedNode(com.bloxbean.intelliada.idea.joust.core.JoustToolValidator.identityOf(Path.of(node).toRealPath()), "test");
            Path web = Files.createDirectories(base.resolve("web"));
            Files.writeString(web.resolve("joust.config.json"), "{}");
            Path pkg = Files.createDirectories(web.resolve("node_modules/@julclang/joust/bin"));
            Files.writeString(pkg.getParent().resolve("package.json"), "{\"name\":\"@julclang/joust\",\"version\":\"0.1.0-pre2\"}");
            Files.writeString(pkg.resolve("joust.js"), "require('fs').writeFileSync('pid', String(process.pid)); setTimeout(() => {}, 120000);\n");
            var layout = JoustProjectLayout.scan(base, com.bloxbean.intelliada.idea.joust.core.JoustPaths.Flavor.current());
            var root = layout.npmRoots().stream().filter(r -> r.dir().equals(web)).findFirst().orElseThrow();
            assertTrue(root.cliInstalled());
            var result = ApplicationManager.getApplication().executeOnPooledThread(
                    () -> com.bloxbean.intelliada.idea.joust.tasks.JoustNpmTasks.run(getProject(), root, java.util.List.of("build")));
            Path pidFile = web.resolve("pid");
            for (int i = 0; i < 200 && !Files.exists(pidFile); i++) Thread.sleep(50);
            assertTrue("the CLI started", Files.exists(pidFile));
            long pid = Long.parseLong(Files.readString(pidFile).trim());
            assertTrue(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
            TrustedProjects.setProjectTrusted(getProject(), false);
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            var r = result.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue("the late result is discarded", r.cancelled());
            for (int i = 0; i < 100 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false); i++) Thread.sleep(50);
            assertFalse("the process was killed", ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        } finally {
            state.loadState(saved);
        }
    }

    /** D8 step 1 for any Gradle run: the recorded JSC0002 output, split across chunks, gives one notification. */
    public void testAGradleRunsAbiFailureIsNotified() throws Exception {
        java.util.List<String> titles = new java.util.concurrent.CopyOnWriteArrayList<>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(com.intellij.notification.Notifications.TOPIC,
                new com.intellij.notification.Notifications() {
                    @Override
                    public void notify(@org.jetbrains.annotations.NotNull com.intellij.notification.Notification n) {
                        titles.add(n.getTitle());
                    }
                });
        String log = new String(getClass().getClassLoader().getResourceAsStream("joust-m2/generateJsc-JSC0002.log").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        var listener = new com.bloxbean.intelliada.idea.joust.tasks.gradle.JoustAbiFailureListener();
        var id = com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId.create(
                org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID,
                com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK, getProject());
        for (int i = 0; i < log.length(); i += 17) {
            listener.onTaskOutput(id, log.substring(i, Math.min(log.length(), i + 17)), com.intellij.execution.process.ProcessOutputType.STDERR);
        }
        listener.onEnd(id);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertEquals(titles.toString(), 1, titles.stream().filter(t -> t.startsWith("ABI change in Counter")).count());
        // A crafted line pointing outside the project gives nothing.
        assertEquals(0, com.bloxbean.intelliada.idea.joust.tasks.gradle.JoustAbiFailureListener.notifyFailures(getProject(),
                java.util.List.of("../../etc/passwd.jsc:1:1: error[JSC0002]: the ABI manifest passwd.abi.json differs")));
    }
}
