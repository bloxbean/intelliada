package com.bloxbean.intelliada.idea.joust.wizard;

import com.bloxbean.intelliada.idea.joust.core.JoustCliVersionProbe;
import com.bloxbean.intelliada.idea.joust.core.JoustToolValidator;
import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.bloxbean.intelliada.idea.joust.wizard.gradle.JoustGradleBuildLinker;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * ADR-004 D9 in the IDE: the wizard is registered, the Gradle linker comes from the optional dependency, the package is
 * for Gradle only, and Create without a validated native {@code joust} runs nothing and creates no project. With
 * {@code JOUST_CLI} set, Create runs the real {@code joust new} into the new, empty project directory.
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

    public void testCreateRunsTheRealJoustIntoTheEmptyProjectDirectory() throws Exception {
        String cli = System.getenv("JOUST_CLI");
        if (cli == null || cli.isBlank()) {
            System.out.println("JOUST_CLI is not set: skipped");
            return;
        }
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

        Path dir = Files.createDirectories(Files.createTempDirectory("joust-wizard-").resolve("counter"));
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
        } finally {
            if (p != null) PlatformTestUtil.forceCloseProjectWithoutSaving(p);
        }
    }
}
