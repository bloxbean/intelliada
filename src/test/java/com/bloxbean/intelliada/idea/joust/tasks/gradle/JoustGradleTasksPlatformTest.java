package com.bloxbean.intelliada.idea.joust.tasks.gradle;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunManager;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** ADR-004 M2: the Gradle side (M2-CDX-4 before-launch tasks; D8 step 1 notifications per module, M2-DES-5, M2-CDX-13/15). */
public class JoustGradleTasksPlatformTest extends BasePlatformTestCase {

    private Path base;
    private final List<String> titles = new CopyOnWriteArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        base = Files.createDirectories(Path.of(getProject().getBasePath())).toRealPath();
        try (var old = Files.list(base)) {
            for (Path p : old.toList()) com.intellij.openapi.util.io.FileUtil.delete(p);   // the light project's directory is shared
        }
        Path src = Path.of(getClass().getClassLoader().getResource("joust-m2/SOURCE.txt").toURI()).getParent().resolve("gradle-jsc");
        for (Path dst : new Path[]{base, base.resolve("other")}) {
            try (var s = Files.walk(src)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    Path t = dst.resolve(src.relativize(p).toString());
                    if (Files.isDirectory(p)) Files.createDirectories(t);
                    else Files.copy(p, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base).refresh(false, true);
        com.bloxbean.intelliada.idea.joust.project.JoustProjectService.getInstance(getProject()).invalidate();
        TrustedProjects.setProjectTrusted(getProject(), true);
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), JoustGradleTasks.class, new JoustGradleTasks() {
            @Override
            public Linked linked(Project project, Path moduleDir) {
                if (moduleDir.endsWith("unlinked")) return null;
                if (moduleDir.endsWith("other2")) return new Linked(moduleDir, ":");   // an independent build, also ":"
                return moduleDir.equals(base) ? new Linked(base, ":") : new Linked(base, ":" + base.relativize(moduleDir));
            }

            @Override
            public JoustAbiReview.Result run(JoustRunGuard guard, Path rootDir, List<String> args, String title) {
                return new JoustAbiReview.Result(false, "", false);
            }

            @Override
            public Path linkedRootOf(Project project, Path path) {
                Path p = path.toAbsolutePath().normalize();
                if (p.startsWith(base.resolve("other2"))) return base.resolve("other2");
                return p.startsWith(base) ? base : null;
            }
        }, getTestRootDisposable());
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification n) {
                titles.add(n.getTitle() + " | " + n.getContent());
            }
        });
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            TrustedProjects.setProjectTrusted(getProject(), true);
        } finally {
            super.tearDown();
        }
    }

    private String log() throws Exception {
        return new String(getClass().getClassLoader().getResourceAsStream("joust-m2/generateJsc-JSC0002.log").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private void feed(JoustAbiFailureListener l, ExternalSystemTaskId id, String text) {
        for (int i = 0; i < text.length(); i += 17) {
            l.onTaskOutput(id, text.substring(i, Math.min(text.length(), i + 17)), com.intellij.execution.process.ProcessOutputType.STDERR);
        }
    }

    private void waitForTitles(int n) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (titles.size() < n && System.currentTimeMillis() < end) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(50);
        }
    }

    /** M2-CDX-4: IntelliJ copies the template's before-launch tasks; IntelliAda's environment has none. */
    public void testNoBeforeLaunchTaskFromTheTemplate() {
        var type = GradleExternalTaskConfigurationType.getInstance();
        var template = RunManager.getInstance(getProject()).getConfigurationTemplate(type.getFactory());
        BeforeRunTask<?> planted = new BeforeRunTask<>(Key.create("joust.test.before")) {
        };
        planted.setEnabled(true);
        template.getConfiguration().setBeforeRunTasks(List.of(planted));
        var t = (org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration) template.getConfiguration();
        t.setDebugAllEnabled(true);
        t.setRunAsTest(true);
        t.setDefaultTargetName("planted-target");
        t.setDebugServerProcess(true);
        t.setReattachDebugProcess(true);
        try {
            var settings = JoustGradleTasksImpl.settings(base, List.of(":generateJsc"), "Generate");
            var plain = ExternalSystemUtil.createExecutionEnvironment(getProject(), GradleConstants.SYSTEM_ID, settings, "Run");
            assertNotNull(plain);
            assertFalse("IntelliJ inherits the template's before-launch task (the finding)",
                    plain.getRunnerAndConfigurationSettings().getConfiguration().getBeforeRunTasks().isEmpty());
            var ours = JoustGradleTasksImpl.environment(getProject(), JoustGradleTasksImpl.settings(base, List.of(":generateJsc"), "Generate"));
            assertNotNull(ours);
            assertEquals(List.of(), ours.getRunnerAndConfigurationSettings().getConfiguration().getBeforeRunTasks());
            var c = (org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration) ours.getRunnerAndConfigurationSettings().getConfiguration();
            assertEquals(List.of(":generateJsc"), c.getSettings().getTaskNames());
            var p = (org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration) plain.getRunnerAndConfigurationSettings().getConfiguration();
            assertEquals("IntelliJ inherits the template's run target (M2-SEC-6)", "planted-target", p.getDefaultTargetName());
            assertNull("no run target from the template", c.getDefaultTargetName());
            assertFalse(c.isDebugAllEnabled());
            assertFalse(c.isRunAsTest());
            assertFalse(c.isDebugServerProcess());
            assertFalse(c.isReattachDebugProcess());
        } finally {
            template.getConfiguration().setBeforeRunTasks(List.of());
            t.setDebugAllEnabled(false);
            t.setRunAsTest(false);
            t.setDefaultTargetName(null);
            t.setDebugServerProcess(false);
            t.setReattachDebugProcess(false);
        }
    }

    /** D8 step 1: the recorded JSC0002 output, split across chunks, names the module whose task failed. */
    public void testTheFailingModuleIsNamed() throws Exception {
        var listener = new JoustAbiFailureListener();
        var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, getProject());
        // The recorded log names the root's task; this run is the other module's.
        String other = log().lines().filter(l -> l.contains("error[JSC0002]")).findFirst().orElseThrow();
        feed(listener, id, "> Task :other:generateJsc FAILED\n" + other + "\nExecution failed for task ':other:generateJsc'.\n");
        listener.onEnd(id);
        waitForTitles(1);
        assertEquals(titles.toString(), 1, titles.size());
        assertTrue(titles.get(0), titles.get(0).startsWith("ABI change in Counter") && titles.get(0).contains("Counter in Gradle project :other changed"));
    }

    /** M2-DES-5: the same source in two modules with no failed task in the output: named, with no action. */
    public void testAnAmbiguousFailureNamesTheModules() throws Exception {
        assertEquals(1, JoustAbiFailureListener.notifyFailures(getProject(), List.of(log().lines()
                .filter(l -> l.contains("JSC0002")).findFirst().orElseThrow()), Set.of()));
        waitForTitles(1);
        assertTrue(titles.toString(), titles.get(0).contains("is in 2 modules"));
    }

    /** M2-CDX-15: an over-long line is dropped through its newline; the next line is still read. */
    public void testAnOverLongLineIsDropped() throws Exception {
        var listener = new JoustAbiFailureListener();
        var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, getProject());
        feed(listener, id, "x".repeat(5_000) + " error[JSC0002]: " + "y".repeat(5_000) + "\n> Task :generateJsc FAILED\n" + log());
        listener.onEnd(id);
        waitForTitles(1);
        assertEquals(titles.toString(), 1, titles.size());
        assertTrue(titles.get(0), titles.get(0).contains(" in "));
    }

    /** An untrusted project gets no notification; a line outside the project gives nothing. */
    public void testNothingWhenUntrustedOrOutside() throws Exception {
        assertEquals(0, JoustAbiFailureListener.notifyFailures(getProject(),
                List.of("../../etc/passwd.jsc:1:1: error[JSC0002]: the ABI manifest passwd.abi.json differs"), Set.of()));
        TrustedProjects.setProjectTrusted(getProject(), false);
        assertEquals(0, JoustAbiFailureListener.notifyFailures(getProject(), List.of(log().lines()
                .filter(l -> l.contains("JSC0002")).findFirst().orElseThrow()), Set.of(":generateJsc")));
    }

    /** M2-CDX-3: a timed-out or interrupted wait cancels the run (and so its task) before returning. */
    public void testATimedOutOrInterruptedWaitCancelsTheRun() throws Exception {
        var tracker = com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker.getInstance(getProject());
        var run = tracker.start(JoustRunGuard.capture(getProject()));
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        run.attach(() -> cancelled.set(true));
        assertNull(JoustGradleTasksImpl.await(run, new java.util.concurrent.CompletableFuture<>(), 50, java.util.concurrent.TimeUnit.MILLISECONDS));
        assertTrue("cancelled on timeout", cancelled.get());
        tracker.done(run);
        var run2 = tracker.start(JoustRunGuard.capture(getProject()));
        java.util.concurrent.atomic.AtomicBoolean cancelled2 = new java.util.concurrent.atomic.AtomicBoolean();
        run2.attach(() -> cancelled2.set(true));
        Thread.currentThread().interrupt();
        try {
            JoustGradleTasksImpl.await(run2, new java.util.concurrent.CompletableFuture<>(), 10, java.util.concurrent.TimeUnit.SECONDS);
            fail("interrupted");
        } catch (InterruptedException expected) {
            assertTrue("cancelled on interrupt", cancelled2.get());
        }
        tracker.done(run2);
    }

    /** M2-CDX-3: output from a run that started before trust was revoked is not published after trust returns. */
    public void testAFailureFromAnEarlierGenerationIsNotPublished() throws Exception {
        var listener = new JoustAbiFailureListener();
        var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, getProject());
        feed(listener, id, "> Task :other:generateJsc FAILED\n" + log().lines().filter(l -> l.contains("JSC0002")).findFirst().orElseThrow() + "\n");
        TrustedProjects.setProjectTrusted(getProject(), false);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        TrustedProjects.setProjectTrusted(getProject(), true);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        listener.onEnd(id);
        Thread.sleep(500);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertEquals(titles.toString(), 0, titles.size());
    }

    /** M2-CDX-13: the diagnostic belongs to the task whose output it was in, not to every failed task of the invocation. */
    public void testTheDiagnosticBelongsToItsOwnTask() throws Exception {
        var listener = new JoustAbiFailureListener();
        var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, getProject());
        String diag = log().lines().filter(l -> l.contains("JSC0002")).findFirst().orElseThrow();
        feed(listener, id, "> Task :other:generateJsc\n" + diag + "\n> Task :other:generateJsc FAILED\n> Task :test FAILED\n"
                + "Execution failed for task ':test'.\n");
        listener.onEnd(id);
        waitForTitles(1);
        assertEquals(titles.toString(), 1, titles.size());
        assertTrue(titles.get(0), titles.get(0).contains("Gradle project :other"));
    }

    /** M2-CDX-13: an unlinked module's notification says why Review is unavailable and offers no action. */
    public void testAnUnlinkedModuleGetsTheReasonNotAnAction() throws Exception {
        Path src = base.resolve("other");
        Path unlinked = base.resolve("unlinked");
        Files.move(src, unlinked);
        Files.delete(base.resolve("src/main/jsc/org/acme/Counter.jsc"));   // only the unlinked module has the source
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base).refresh(false, true);
        assertEquals(1, JoustAbiFailureListener.notifyFailures(getProject(), List.of(log().lines()
                .filter(l -> l.contains("JSC0002")).findFirst().orElseThrow()), Set.of(":unlinked:generateJsc")));
        waitForTitles(1);
        assertTrue(titles.toString(), titles.stream().anyMatch(t -> t.contains("Link the Gradle build first")));
    }

    private JoustGradleTasksImpl.Binding binding(com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker.Run run) {
        var env = JoustGradleTasksImpl.environment(getProject(), JoustGradleTasksImpl.settings(base, List.of(":generateJsc"), "Generate"));
        assertNotNull(env);
        return new JoustGradleTasksImpl.Binding(getProject(), env, run);
    }

    private com.intellij.execution.ExecutionListener bus() {
        return getProject().getMessageBus().syncPublisher(com.intellij.execution.ExecutionManager.EXECUTION_TOPIC);
    }

    /** M2-DES-10, M2-CDX-22: the run's handler is bound from processStarting or, failing that, processStarted. */
    public void testTheHandlerIsBoundFromEitherStartEvent() throws Exception {
        var tracker = com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker.getInstance(getProject());
        for (String event : new String[]{"starting", "started", "started-after-exit"}) {
            var run = tracker.start(JoustRunGuard.capture(getProject()));
            var b = binding(run);
            var h = new com.intellij.execution.process.NopProcessHandler();
            h.startNotify();
            if (event.equals("started-after-exit")) {
                h.notifyTextAvailable("org/acme/Counter.jsc:1:1: error[JSC0002]: x\n", com.intellij.execution.process.ProcessOutputTypes.STDOUT);
                h.destroyProcess();
                h.waitFor(5_000);
            }
            if (event.equals("starting")) bus().processStarting("Run", b.env(), h);
            else bus().processStarted("Run", b.env(), h);
            assertTrue(event, b.bound());
            if (!event.equals("started-after-exit")) {
                h.notifyTextAvailable("org/acme/Counter.jsc:1:1: error[JSC0002]: x\n", com.intellij.execution.process.ProcessOutputTypes.STDOUT);
                h.destroyProcess();
                assertTrue(event + ": output captured", b.out.toString().contains("JSC0002"));
            }
            assertTrue(event + ": the exit is seen", b.done.get(5, java.util.concurrent.TimeUnit.SECONDS) != null);
            b.release(true);
            tracker.done(run);
        }
    }

    /** M2-CDX-3: revoked after submission, before the handler: the handler that arrives later is destroyed at once. */
    public void testAHandlerArrivingAfterCancellationIsDestroyed() throws Exception {
        var tracker = com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker.getInstance(getProject());
        var run = tracker.start(JoustRunGuard.capture(getProject()));
        var b = binding(run);
        TrustedProjects.setProjectTrusted(getProject(), false);   // cancels the run through the tracker
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertTrue(run.cancelled());
        assertTrue(b.done.isDone());
        b.release(true);   // IntelliAda stops waiting; the binding keeps listening, with no time limit
        b.releaseWhenEnded(tracker);
        Thread.sleep(1_500);   // well after IntelliAda let go
        assertTrue("still listening for the submitted run's handler", b.listening());
        assertEquals("still tracked before its handler arrives (M2-CDX-3)", 1, tracker.runningCount());
        var h = new com.intellij.execution.process.NopProcessHandler();
        h.startNotify();
        bus().processStarting("Run", b.env(), h);
        assertTrue("the late handler is destroyed", h.waitFor(5_000));
        assertTrue(h.isProcessTerminated());
        assertFalse("and then it stops listening", b.listening());
        long end = System.currentTimeMillis() + 5_000;
        while (tracker.runningCount() != 0 && System.currentTimeMillis() < end) Thread.sleep(20);
        assertEquals("released once its handler ended", 0, tracker.runningCount());
    }

    /** M2-CDX-3: a run revoked and re-trusted before its first output is not published. */
    public void testAGenerationIsTakenAtTaskStart() throws Exception {
        var listener = new JoustAbiFailureListener();
        var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, getProject());
        listener.onStart(base.toString(), id);
        TrustedProjects.setProjectTrusted(getProject(), false);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        TrustedProjects.setProjectTrusted(getProject(), true);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        feed(listener, id, "> Task :other:generateJsc\n" + log().lines().filter(l -> l.contains("JSC0002")).findFirst().orElseThrow() + "\n");
        listener.onEnd(id);
        Thread.sleep(500);
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        assertEquals(titles.toString(), 0, titles.size());
    }

    /** M2-CDX-13: the failing task is known and its source is gone: the copy in another module gets no action. */
    public void testAKnownFailingModuleIsNeverSwappedForAnother() throws Exception {
        Files.delete(base.resolve("src/main/jsc/org/acme/Counter.jsc"));   // :generateJsc failed for it, then it went away
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base).refresh(false, true);
        var listener = new JoustAbiFailureListener();
        var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, getProject());
        feed(listener, id, "> Task :generateJsc\n" + log().lines().filter(l -> l.contains("JSC0002")).findFirst().orElseThrow() + "\n");
        listener.onEnd(id);
        waitForTitles(1);
        assertEquals(titles.toString(), 1, titles.size());
        assertTrue(titles.get(0), titles.get(0).contains("failed in Gradle project :") && titles.get(0).contains("not in the module now"));
        assertFalse(titles.get(0).contains(":other"));
    }

    /** M2-CDX-13: two independent linked builds both named ":": the run's build root decides, not the path alone. */
    public void testTheRunsBuildRootIsMatchedToo() throws Exception {
        Path other2 = base.resolve("other2");
        Files.move(base.resolve("other"), other2);
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base).refresh(false, true);
        Files.delete(other2.resolve("src/main/jsc/org/acme/Counter.jsc"));   // the failing build's source went away
        String diag = log().lines().filter(l -> l.contains("JSC0002")).findFirst().orElseThrow();
        assertEquals(1, JoustAbiFailureListener.notifyFailures(getProject(), List.of(new JoustAbiFailureListener.Diagnostic(diag, ":generateJsc")),
                Set.of(), com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker.getInstance(getProject()).generation(), other2.toString()));
        waitForTitles(1);
        assertTrue(titles.toString(), titles.get(0).contains("not in the module now"));
    }

    /** M2-CDX-24: diagnostics after thousands of ordinary lines are still parsed. */
    public void testFailuresAfterManyLinesAreParsed() throws Exception {
        String npmLog = new String(getClass().getClassLoader().getResourceAsStream("joust-m2/npm-build-JSC0002.log").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        String verbose = "line\n".repeat(2_100) + npmLog;
        var capture = new com.bloxbean.intelliada.idea.joust.tasks.JoustOutputCapture(1 << 20);
        capture.append(verbose);
        assertEquals(1, com.bloxbean.intelliada.idea.joust.abi.JoustAbi.failures(capture.toString()).size());
    }

    /** M2-CDX-13: a run started from a module directory belongs to its linked build; the module's task is named. */
    public void testARunStartedFromAModuleDirectory() throws Exception {
        var listener = new JoustAbiFailureListener();
        var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, getProject());
        listener.onStart(base.resolve("other").toString(), id);
        feed(listener, id, "> Task :other:generateJsc\n" + log().lines().filter(l -> l.contains("JSC0002")).findFirst().orElseThrow() + "\n");
        listener.onEnd(id);
        waitForTitles(1);
        assertEquals(titles.toString(), 1, titles.size());
        assertTrue(titles.get(0), titles.get(0).contains("Gradle project :other changed"));
    }

    /** M2-CDX-13: a known build root with no task header: a copy in a different linked build gets no action. */
    public void testAnotherBuildsCopyNeverGetsTheAction() throws Exception {
        Path other2 = base.resolve("other2");
        Files.move(base.resolve("other"), other2);
        Files.delete(other2.resolve("src/main/jsc/org/acme/Counter.jsc"));
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base).refresh(false, true);
        com.bloxbean.intelliada.idea.joust.project.JoustProjectService.getInstance(getProject()).invalidate();
        var listener = new JoustAbiFailureListener();
        var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, getProject());
        listener.onStart(other2.toString(), id);
        feed(listener, id, log().lines().filter(l -> l.contains("JSC0002")).findFirst().orElseThrow() + "\n");
        listener.onEnd(id);
        waitForTitles(1);
        assertEquals(titles.toString(), 1, titles.size());
        assertTrue(titles.get(0), titles.get(0).contains("Build again") || titles.get(0).contains("not in the module now"));
    }

    /** M2-CDX-13: a stale linked-project entry is skipped on its own; it never discards a valid match. */
    public void testAStaleLinkIsSkippedOnItsOwn() throws Exception {
        var settings = org.jetbrains.plugins.gradle.settings.GradleSettings.getInstance(getProject());
        var stale = new org.jetbrains.plugins.gradle.settings.GradleProjectSettings();
        stale.setExternalProjectPath(base.resolve("gone-away").toString());
        var real = new org.jetbrains.plugins.gradle.settings.GradleProjectSettings();
        real.setExternalProjectPath(base.toString());
        settings.linkProject(stale);
        settings.linkProject(real);
        try {
            assertEquals(base, new JoustGradleTasksImpl().linkedRootOf(getProject(), base.resolve("other")));
        } finally {
            settings.unlinkExternalProject(stale.getExternalProjectPath());
            settings.unlinkExternalProject(real.getExternalProjectPath());
        }
    }

    /** M2-CDX-13: a run whose start path is no linked build credits no module: no action. */
    public void testAnUnattributableStartGetsNoAction() throws Exception {
        var listener = new JoustAbiFailureListener();
        var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, getProject());
        listener.onStart("/nowhere/linked", id);
        feed(listener, id, "> Task :other:generateJsc\n" + log().lines().filter(l -> l.contains("JSC0002")).findFirst().orElseThrow() + "\n");
        listener.onEnd(id);
        waitForTitles(1);
        assertEquals(titles.toString(), 1, titles.size());
        assertTrue(titles.get(0), titles.get(0).contains("started at /nowhere/linked"));
    }

    /** M2-CDX-13: a headerless failure whose build holds a linked and an unlinked copy is ambiguous: no action. */
    public void testALinkedAndAnUnlinkedCopyAreAmbiguous() throws Exception {
        JoustM2ActionsCopy.copy(Path.of(getClass().getClassLoader().getResource("joust-m2/SOURCE.txt").toURI()).getParent().resolve("gradle-jsc"),
                base.resolve("unlinked"));
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base).refresh(false, true);
        com.bloxbean.intelliada.idea.joust.project.JoustProjectService.getInstance(getProject()).invalidate();
        Files.delete(base.resolve("src/main/jsc/org/acme/Counter.jsc"));   // only :other (linked) and unlinked/ hold it
        var listener = new JoustAbiFailureListener();
        var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, getProject());
        listener.onStart(base.toString(), id);
        feed(listener, id, log().lines().filter(l -> l.contains("JSC0002")).findFirst().orElseThrow() + "\n");
        listener.onEnd(id);
        waitForTitles(1);
        assertEquals(titles.toString(), 1, titles.size());
        assertTrue(titles.get(0), titles.get(0).contains("is in 2 modules") && titles.get(0).contains("unlinked"));
    }

    /** Copies a fixture tree. */
    static final class JoustM2ActionsCopy {
        static void copy(Path src, Path dst) throws Exception {
            try (var s = Files.walk(src)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    Path t = dst.resolve(src.relativize(p).toString());
                    if (Files.isDirectory(p)) Files.createDirectories(t);
                    else Files.copy(p, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** M2-CDX-13: the failing task is known, and its module holds the source but is not linked: "Link the Gradle build first". */
    public void testAKnownUnlinkedFailingModuleIsToldToLink() throws Exception {
        JoustM2ActionsCopy.copy(Path.of(getClass().getClassLoader().getResource("joust-m2/SOURCE.txt").toURI()).getParent().resolve("gradle-jsc"),
                base.resolve("unlinked"));
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base).refresh(false, true);
        com.bloxbean.intelliada.idea.joust.project.JoustProjectService.getInstance(getProject()).invalidate();
        var listener = new JoustAbiFailureListener();
        var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, getProject());
        listener.onStart(base.toString(), id);
        feed(listener, id, "> Task :unlinked:generateJsc\n" + log().lines().filter(l -> l.contains("JSC0002")).findFirst().orElseThrow() + "\n");
        listener.onEnd(id);
        waitForTitles(1);
        assertEquals(titles.toString(), 1, titles.size());
        assertTrue(titles.get(0), titles.get(0).contains("Link the Gradle build first") && titles.get(0).contains("unlinked"));
        assertFalse(titles.get(0).contains("Build again"));
    }

    /** M2-CDX-13: as below, when the unlinked copy is the only one left: still named, still no link advice. */
    public void testALinkedFailingModuleIsNotToldToLinkEvenWhenOnlyAnUnlinkedCopyRemains() throws Exception {
        Files.delete(base.resolve("src/main/jsc/org/acme/Counter.jsc"));
        testALinkedFailingModuleIsNotToldToLink();
    }

    /** M2-CDX-13: a linked failing module whose source is gone is named, with no link advice for an unrelated unlinked copy. */
    public void testALinkedFailingModuleIsNotToldToLink() throws Exception {
        JoustM2ActionsCopy.copy(Path.of(getClass().getClassLoader().getResource("joust-m2/SOURCE.txt").toURI()).getParent().resolve("gradle-jsc"),
                base.resolve("unlinked"));
        Files.delete(base.resolve("other/src/main/jsc/org/acme/Counter.jsc"));   // :other failed, then its source went away
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base).refresh(false, true);
        com.bloxbean.intelliada.idea.joust.project.JoustProjectService.getInstance(getProject()).invalidate();
        var listener = new JoustAbiFailureListener();
        var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, getProject());
        listener.onStart(base.toString(), id);
        feed(listener, id, "> Task :other:generateJsc\n" + log().lines().filter(l -> l.contains("JSC0002")).findFirst().orElseThrow() + "\n");
        listener.onEnd(id);
        waitForTitles(1);
        assertEquals(titles.toString(), 1, titles.size());
        assertTrue(titles.get(0), titles.get(0).contains(":other") && titles.get(0).contains("not in the module now"));
        assertFalse(titles.get(0).contains("Link the Gradle build"));
    }
}
