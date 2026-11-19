package com.bloxbean.intelliada.idea.joust.wizard;

import com.bloxbean.intelliada.idea.joust.JoustTestTools;
import com.bloxbean.intelliada.idea.joust.core.JoustCliVersionProbe;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.bloxbean.intelliada.idea.joust.wizard.gradle.JoustGradleBuildLinker;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.project.TestProjectManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * ADR-004 D9 in the IDE: the wizard is registered, the Gradle linker comes from the optional dependency, the package is
 * for Gradle only, and Create without a validated native {@code joust} runs nothing and creates no project. With
 * {@code JOUST_CLI} set, Create runs the real {@code joust new} into the new, empty project directory, and the opened
 * project asks for its Gradle build to be linked; without it the test is skipped, or fails under
 * {@code JOUST_REQUIRE_TOOLS=1} (M2-CDX-19).
 */
public class JoustWizardPlatformTest extends BasePlatformTestCase {

    private JoustToolsState.Data saved;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        JoustToolsState.Data d = JoustToolsState.getInstance().getState();
        saved = new JoustToolsState.Data();
        saved.joustCliPath = d.joustCliPath;
        saved.joustCliRealPath = d.joustCliRealPath;
        saved.joustCliSha256 = d.joustCliSha256;
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            JoustToolsState.Data d = JoustToolsState.getInstance().getState();
            d.joustCliPath = saved.joustCliPath;
            JoustToolsState.getInstance().setValidatedJoustCli(null, null);
        } finally {
            super.tearDown();
        }
    }

    public void testTheWizardIsRegistered() {
        assertTrue(ModuleBuilder.getAllBuilders().stream().anyMatch(b -> b instanceof JoustModuleBuilder));
    }

    public void testTheGradleLinkerComesFromTheOptionalDependency() {
        assertTrue("intelliada-gradle.xml registers the linker when com.intellij.gradle is present",
                JoustBuildLinker.getInstance() instanceof JoustGradleBuildLinker);
    }

    public void testThePackageIsForGradleOnlyAndTheNoteMentionsTheNetwork() {
        JoustModuleBuilder b = new JoustModuleBuilder();
        List<WizardInputField<?>> fields = b.getAdditionalFields();
        assertEquals(5, fields.size());
        @SuppressWarnings("unchecked")
        JComboBox<JoustTemplate> template = (JComboBox<JoustTemplate>) fields.get(1).getComponent();
        JComponent pkg = fields.get(2).getComponent();
        JLabel note = (JLabel) fields.get(4).getComponent();
        assertTrue(pkg.isEnabled());
        template.setSelectedItem(JoustTemplate.NODE);
        assertFalse("-p is refused for node (F27)", pkg.isEnabled());
        assertTrue(note.getText(), note.getText().contains("network"));
        template.setSelectedItem(JoustTemplate.WEB);
        assertFalse(pkg.isEnabled());
        template.setSelectedItem(JoustTemplate.GRADLE);
        assertTrue(pkg.isEnabled());
        assertFalse(note.getText().contains("network access"));
    }

    public void testCreateWithoutAValidatedJoustRunsNothing() throws Exception {
        JoustToolsState.getInstance().getState().joustCliPath = "";
        JoustToolsState.getInstance().setValidatedJoustCli(null, null);
        int probes = JoustCliVersionProbe.invocations();
        Path dir = Files.createTempDirectory("joust-wizard-");
        JoustModuleBuilder b = new JoustModuleBuilder();
        b.getAdditionalFields();
        Project p = b.createProject("counter", dir.toString());
        assertNull("no project without a validated joust", p);
        try (Stream<Path> s = Files.list(dir)) {
            assertEquals(0, s.count());
        }
        assertEquals(probes, JoustCliVersionProbe.invocations());
    }

    /** M2-SEC-1: progress titles are rendered as HTML in the status-bar tooltip; the typed directory name is defused. */
    public void testAProjectDirectoryNamedLikeMarkupGivesAPlainTitle() {
        for (String dir : new String[]{"/tmp/<img src=x>", "/tmp/<img src=x>/", "/tmp/a<b>c\n<script>"}) {
            String title = JoustModuleBuilder.progressTitle(dir);
            assertFalse(title, title.contains("<"));
            assertFalse(title, title.contains(">"));
            assertFalse(title, title.contains("\n"));
        }
        assertEquals("Creating the JOUST project \u2039img src=x\u203A (joust new)", JoustModuleBuilder.progressTitle("/tmp/<img src=x>"));
        assertEquals("Creating the JOUST project counter (joust new)", JoustModuleBuilder.progressTitle("/tmp/counter"));
    }

    public void testCreateRunsTheRealJoustIntoTheEmptyProjectDirectory() throws Exception {
        String cli = JoustTestTools.env("JOUST_CLI", "JoustWizardPlatformTest.testCreateRunsTheRealJoustIntoTheEmptyProjectDirectory");
        if (cli == null) return;   // skipped (a JOUST-SKIPPED line was printed); a required run has failed above
        String jdkHome = System.getenv("JOUST_TEST_JAVA_HOME");
        if (jdkHome == null || jdkHome.isBlank()) {
            Path sdkman = Path.of(System.getProperty("user.home"), ".sdkman/candidates/java/25.0.2-librca");
            jdkHome = Files.isDirectory(sdkman) ? sdkman.toString() : System.getProperty("java.home");
        }
        Sdk jdk = JavaSdk.getInstance().createJdk("JOUST test JDK", jdkHome, false);
        WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(jdk, getTestRootDisposable()));
        JoustToolsState state = JoustToolsState.getInstance();
        state.getState().joustCliPath = cli;
        state.setValidatedJoustCli(JoustToolValidator.identityOf(Path.of(cli).toRealPath()), "test");

        // The Gradle linker is replaced by one that records what it was asked to link (the real one starts a Gradle sync).
        List<String> linked = new CopyOnWriteArrayList<>();
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), JoustBuildLinker.class,
                (project, buildRoot) -> linked.add(project.getBasePath() + " -> " + buildRoot), getTestRootDisposable());

        Path dir = Files.createDirectories(Files.createTempDirectory("joust-wizard-").resolve("counter")).toRealPath();
        JoustModuleBuilder b = new JoustModuleBuilder();
        List<WizardInputField<?>> fields = b.getAdditionalFields();
        @SuppressWarnings("unchecked")
        JComboBox<Sdk> jdks = (JComboBox<Sdk>) fields.get(3).getComponent();
        jdks.setSelectedItem(jdk);
        assertTrue(fields.get(4).validate());
        Project p = b.createProject("counter", dir.toString());
        try {
            assertNotNull("the project is created after joust new", p);
            assertTrue(Files.isRegularFile(dir.resolve("build.gradle")));
            assertTrue(Files.isRegularFile(dir.resolve("src/main/jsc/app/Counter.jsc")));
            // The module is committed without running joust again (the directory is no longer empty), with the JDK.
            b.setName("counter");
            b.setModuleFilePath(dir.resolve("counter.iml").toString());
            b.setContentEntryPath(dir.toString());
            List<Module> modules = b.commit(p, null, ModulesProvider.EMPTY_MODULES_PROVIDER);
            assertNotNull(modules);
            assertEquals(1, modules.size());
            assertEquals(jdk, ModuleRootManager.getInstance(modules.get(0)).getSdk());
            assertEquals(1, ModuleRootManager.getInstance(modules.get(0)).getContentRoots().length);
            assertTrue("the Gradle build is linked only once the project is open: " + linked, linked.isEmpty());

            // D9 "Afterwards": the project opens and, trusted, asks IntelliJ's Gradle integration to link build.gradle.
            TrustedProjects.setProjectTrusted(p, true);
            assertTrue("the test project manager opens the project",
                    ((TestProjectManager) ProjectManager.getInstance()).openProject(p));
            PlatformTestUtil.waitWithEventsDispatching("the Gradle build was not linked", () -> !linked.isEmpty(), 30);
            assertEquals(List.of(p.getBasePath() + " -> " + dir), linked);

            // The real linker (linkAndSyncGradleProject) records the link in the opened project's Gradle settings.
            new JoustGradleBuildLinker().linkGradle(p, dir.toString());
            PlatformTestUtil.waitWithEventsDispatching("IntelliJ did not link " + dir, () -> org.jetbrains.plugins.gradle.settings
                    .GradleSettings.getInstance(p).getLinkedProjectSettings(dir.toString()) != null, 60);
        } finally {
            if (p != null) PlatformTestUtil.forceCloseProjectWithoutSaving(p);
        }
    }
}
