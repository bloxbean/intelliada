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
        TrustedProjects.setProjectTrusted(getProject(), true);
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), JoustGradleTasks.class, new JoustGradleTasks() {
            @Override
            public Linked linked(Project project, Path moduleDir) {
                return moduleDir.equals(base) ? new Linked(base, ":") : new Linked(base, ":" + base.relativize(moduleDir));
            }

            @Override
            public JoustAbiReview.Result run(JoustRunGuard guard, Path rootDir, List<String> args, String title) {
                return new JoustAbiReview.Result(false, "", false);
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
        try {
            var settings = JoustGradleTasksImpl.settings(base, List.of(":generateJsc"), "Generate");
            var plain = ExternalSystemUtil.createExecutionEnvironment(getProject(), GradleConstants.SYSTEM_ID, settings, "Run");
            assertNotNull(plain);
            assertFalse("IntelliJ inherits the template's before-launch task (the finding)",
                    plain.getRunnerAndConfigurationSettings().getConfiguration().getBeforeRunTasks().isEmpty());
            var ours = JoustGradleTasksImpl.environment(getProject(), JoustGradleTasksImpl.settings(base, List.of(":generateJsc"), "Generate"));
            assertNotNull(ours);
            assertEquals(List.of(), ours.getRunnerAndConfigurationSettings().getConfiguration().getBeforeRunTasks());
            assertEquals(List.of(":generateJsc"), ((org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration)
                    ours.getRunnerAndConfigurationSettings().getConfiguration()).getSettings().getTaskNames());
        } finally {
            template.getConfiguration().setBeforeRunTasks(List.of());
        }
    }

    /** D8 step 1: the recorded JSC0002 output, split across chunks, names the module whose task failed. */
    public void testTheFailingModuleIsNamed() throws Exception {
        var listener = new JoustAbiFailureListener();
        var id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, getProject());
        feed(listener, id, "> Task :other:generateJsc FAILED\n" + log());
        listener.onEnd(id);
        waitForTitles(1);
        assertEquals(titles.toString(), 1, titles.size());
        assertTrue(titles.get(0), titles.get(0).startsWith("ABI change in Counter") && titles.get(0).contains(" in other."));
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
}
