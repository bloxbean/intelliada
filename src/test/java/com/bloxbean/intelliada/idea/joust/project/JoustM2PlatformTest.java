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
}
