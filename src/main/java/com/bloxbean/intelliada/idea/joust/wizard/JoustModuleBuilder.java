package com.bloxbean.intelliada.idea.joust.wizard;

import com.bloxbean.intelliada.idea.common.CardanoIcons;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.core.JoustPrivateTemp;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.lsp.JoustNotifications;
import com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots;
import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.bloxbean.intelliada.idea.joust.trust.JoustTrust;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleBuilderListener;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.util.projectWizard.WizardInputField;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADR-004 D9: the JOUST new-project wizard (File | New | Project… | JOUST), a {@code moduleBuilder} like
 * {@code JulcModuleBuilder}.
 *
 * <ul>
 *   <li><b>It asks</b> for the language ({@code .jsc} or Java), the template (Gradle, Node or web), the package for
 *   Gradle only ({@code -p} is refused for Node and web, F27), and a JDK from IntelliJ's application SDK table.</li>
 *   <li><b>It runs</b> the user's native {@code joust} (application settings, validated as in D4) as
 *   {@code joust new <empty dir> -t <template> -l <lang> [-p <package>]}, an argument list, when the user clicks Create:
 *   into the new project's directory, which IntelliJ has just created empty, before the project is opened
 *   ({@link #createProject}). The environment is built, not inherited (I7, {@link JoustNewEnvironment}).</li>
 *   <li><b>Afterwards</b> the project opens and, when it has a {@code build.gradle} and is trusted (D1), its Gradle
 *   build is linked through IntelliJ's Gradle integration ({@link JoustBuildLinker}, optional dependency).
 *   {@code npm create joust} is not offered (Q9).</li>
 * </ul>
 */
public class JoustModuleBuilder extends ModuleBuilder implements ModuleBuilderListener {

    private static final Logger LOG = Logger.getInstance(JoustModuleBuilder.class);
    /** {@code joust new -t node|web} fetches create-joust with npm; allow for a slow network. */
    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final Pattern MAJOR = Pattern.compile("(?:version\\s+)?\"?(?:1\\.)?(\\d+)");

    private LanguageField languageField;
    private TemplateField templateField;
    private PackageField packageField;
    private JdkField jdkField;
    private NoteField noteField;

    // The choices, kept current from the EDT so createProject may read them from any thread.
    private volatile JoustLanguage language = JoustLanguage.JSC;
    private volatile JoustTemplate template = JoustTemplate.GRADLE;
    private volatile String pkg = "app";
    private volatile Sdk jdk;

    private volatile boolean scaffolded;
    private volatile List<String> lastCommand;
    private volatile String lastOutput;

    public JoustModuleBuilder() {
        addListener(this);
    }

    @Override
    public String getBuilderId() {
        return "Joust";
    }

    @Override
    public Icon getNodeIcon() {
        return CardanoIcons.CARDANO_ICON_16x16;
    }

    @Override
    public String getDescription() {
        return "JOUST: Cardano state contracts in .jsc or Java, created by your native joust (joust new)";
    }

    @Override
    public String getPresentableName() {
        return "JOUST";
    }

    @Override
    public String getGroupName() {
        return "JOUST";
    }

    @Override
    public ModuleType<?> getModuleType() {
        return ModuleTypeManager.getInstance().getDefaultModuleType();
    }

    @Override
    public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
        return new ModuleWizardStep[]{};
    }

    @Override
    protected List<WizardInputField<?>> getAdditionalFields() {
        if (languageField == null) {
            languageField = new LanguageField();
            templateField = new TemplateField();
            packageField = new PackageField();
            jdkField = new JdkField();
            noteField = new NoteField();
            languageField.getComponent().addItemListener(e -> {
                language = (JoustLanguage) languageField.getComponent().getSelectedItem();
                refresh();
            });
            templateField.getComponent().addItemListener(e -> {
                template = (JoustTemplate) templateField.getComponent().getSelectedItem();
                refresh();
            });
            packageField.getComponent().getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    pkg = packageField.getValue();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    pkg = packageField.getValue();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    pkg = packageField.getValue();
                }
            });
            jdkField.getComponent().addItemListener(e -> {
                jdk = (Sdk) jdkField.getComponent().getSelectedItem();
                refresh();
            });
            jdk = (Sdk) jdkField.getComponent().getSelectedItem();
            refresh();
        }
        return Arrays.asList(languageField, templateField, packageField, jdkField, noteField);
    }

    /** Enables the package for Gradle only and says what will run, and that Node and web need npm and the network. */
    private void refresh() {
        boolean gradle = template.takesPackage();
        packageField.getComponent().setEnabled(gradle);
        String cli = JoustToolsState.getInstance().getState().joustCliPath;
        StringBuilder sb = new StringBuilder();
        sb.append("Create runs your native joust");
        sb.append(cli == null || cli.isBlank() ? " (configure it in Settings | Tools | JOUST)" : " (" + cli + ")");
        sb.append(": joust new <project directory> -t ").append(template.cliName()).append(" -l ").append(language.cliName());
        if (gradle) sb.append(" -p <package>");
        sb.append(", with PATH limited to the configured tools and the system directories, and JAVA_HOME set to the JDK.");
        if (template.usesNpm()) {
            sb.append("\nThe ").append(template.cliName()).append(" template needs npm and network access: joust new "
                    + "fetches create-joust from the npm registry with the npm beside your configured Node.");
        } else {
            sb.append("\nIntelliAda then opens the project and links its Gradle build (IntelliJ imports it with Gradle).");
        }
        Integer major = jdk == null ? null : major(jdk.getVersionString());
        if (major != null && major < 25) {
            sb.append("\nNote: JOUST's Gradle projects build with JDK 25; ").append(PlainText.label(jdk.getName(), 60))
                    .append(" is Java ").append(major).append('.');
        }
        noteField.getComponent().setText(PlainText.html(sb.toString()));
    }

    static Integer major(String versionString) {
        if (versionString == null) return null;
        Matcher m = MAJOR.matcher(versionString.replaceAll("^[^0-9\"]*", ""));
        try {
            return m.find() ? Integer.parseInt(m.group(1)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * New project: IntelliJ has just created the project directory, empty, and asks for the project. {@code joust new}
     * runs into it first; on failure no project is created and the user is told why.
     */
    @Override
    public @Nullable Project createProject(String name, String path) {
        String target = path != null && path.endsWith(".ipr") ? Path.of(path).getParent().toString() : path;
        if (!scaffolded && !scaffold(null, target)) return null;
        return super.createProject(name, path);
    }

    /** New module in an existing project (no {@link #createProject} call): run outside the model's write action. */
    @Override
    public @Nullable List<Module> commit(@NotNull Project project, @Nullable ModifiableModuleModel model,
                                         @Nullable ModulesProvider modulesProvider) {
        if (!scaffolded) {
            String target = getContentEntryPath();
            if (target == null || !scaffold(project, target)) return List.of();
        }
        return super.commit(project, model, modulesProvider);
    }

    @Override
    public void setupRootModel(@NotNull ModifiableRootModel rootModel) throws ConfigurationException {
        Sdk chosen = jdk;
        if (chosen != null) {
            rootModel.setSdk(chosen);
            ProjectRootManager prm = ProjectRootManager.getInstance(rootModel.getProject());
            if (prm.getProjectSdk() == null) prm.setProjectSdk(chosen);
        } else {
            rootModel.inheritSdk();
        }
        doAddContentEntry(rootModel);
    }

    @Override
    public void moduleCreated(@NotNull Module module) {
        Project project = module.getProject();
        String root = getContentEntryPath();
        List<String> command = lastCommand;
        String output = lastOutput;
        StartupManager.getInstance(project).runAfterOpened(() -> ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || root == null) return;
            VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(root);
            if (dir != null) VfsUtil.markDirtyAndRefresh(false, true, true, dir);
            if (command != null) {
                JoustNotifications.show(project, "JOUST project created", "Ran: " + JoustNewCommand.display(command)
                        + (output == null || output.isBlank() ? "" : "\n\n" + output), NotificationType.INFORMATION);
            }
            if (Files.isRegularFile(Path.of(root, "build.gradle"))) linkGradle(project, root);
        }));
    }

    /**
     * The title of the wizard's progress (M2-SEC-1): IntelliJ renders progress titles as HTML in the status-bar tooltip,
     * so the project directory's name, which the user typed, is shown with {@code <} and {@code >} as their
     * single-guillemet look-alikes {@code ‹ ›}, without control characters and capped.
     */
    static String progressTitle(String target) {
        String name = target == null ? "" : JoustPaths.fileName(target.replaceAll("[/\\\\]+$", ""));
        name = PlainText.clean(name, 60).replace('\n', ' ').replace('\t', ' ').replace('<', '\u2039').replace('>', '\u203A');
        return name.isBlank() ? "Creating the JOUST project (joust new)" : "Creating the JOUST project " + name + " (joust new)";
    }

    private static void linkGradle(Project project, String root) {
        JoustBuildLinker linker = JoustBuildLinker.getInstance();
        if (linker == null) {
            JoustNotifications.show(project, "Link the Gradle build", "IntelliJ's Gradle plugin is not enabled, so the "
                    + "build was not linked. Enable it, then open build.gradle and choose Link Gradle Project.", NotificationType.INFORMATION);
            return;
        }
        if (!JoustTrust.isTrusted(project)) {
            JoustNotifications.show(project, "Gradle build not linked", "This project is not trusted, so IntelliAda did "
                    + "not link its Gradle build (ADR-004 D1). Trust the project, then link build.gradle.", NotificationType.WARNING);
            return;
        }
        try {
            linker.linkGradle(project, root);
        } catch (RuntimeException e) {
            LOG.warn("Linking the Gradle build failed", e);
            JoustNotifications.show(project, "Gradle build not linked", "Linking " + root + " failed: " + e.getMessage()
                    + ". Open build.gradle and choose Link Gradle Project.", NotificationType.WARNING);
        }
    }

    /** What one Create did: the plan, and the outcome when it ran. */
    private record Attempt(JoustNewPlanner.Plan plan, JoustNewRunner.Outcome outcome) {
    }

    /** Plans and runs {@code joust new} into {@code target}; false (and the user told) when it did not succeed. */
    private boolean scaffold(@Nullable Project project, String target) {
        JoustToolsState state = JoustToolsState.getInstance();
        JoustToolsState.Data d = state.getState();
        Sdk chosen = jdk;
        JoustNewPlanner.Request request = new JoustNewPlanner.Request(template, language,
                template.takesPackage() ? pkg : null, target, chosen == null ? null : chosen.getHomePath());
        JoustNewPlanner.ToolSettings tools = new JoustNewPlanner.ToolSettings(d.joustCliPath, state.validatedJoustCli(),
                d.nodePath, state.validatedNode());
        // E12 (M2-CDX-11): the working directory and the child's TMPDIR are a private directory under the IDE's own
        // temporary directory, created by the planner only after it is checked against every open project.
        Path tempBase = Path.of(PathManager.getTempPath());
        List<Path> forbidden = JoustForbiddenRoots.ofOpenProjects();
        Attempt attempt;
        try {
            // M2-CDX-17: planning hashes the tools, so it runs with joust new, off the EDT.
            attempt = inBackground(() -> planAndRun(request, tools, forbidden, tempBase), progressTitle(target), project);
        } catch (IOException e) {
            fail(project, "joust could not be started: " + e.getMessage());
            return false;
        }
        JoustNewPlanner.Plan plan = attempt.plan();
        if (!plan.ok()) {
            fail(project, plan.error());
            return false;
        }
        JoustNewRunner.Outcome outcome = attempt.outcome();
        if (!outcome.ok()) {
            String why = outcome.cancelled() ? "was cancelled" : outcome.timedOut() ? "did not finish in "
                    + TIMEOUT.toMinutes() + " minutes" : "exited with " + outcome.exitCode();
            fail(project, "joust new " + why + ".\n\n" + outcome.output());
            return false;
        }
        lastCommand = plan.command();
        lastOutput = outcome.output();
        scaffolded = true;
        return true;
    }

    private static Attempt planAndRun(JoustNewPlanner.Request request, JoustNewPlanner.ToolSettings tools,
                                      List<Path> forbidden, Path tempBase) throws IOException {
        JoustNewPlanner.Plan plan = JoustNewPlanner.plan(request, tools, System.getenv(), forbidden, tempBase,
                JoustPaths.Flavor.current());
        if (!plan.ok()) return new Attempt(plan, null);
        LOG.info("JOUST wizard: " + JoustNewCommand.display(plan.command()) + " with PATH=" + plan.env().get("PATH")
                + " JAVA_HOME=" + plan.env().get("JAVA_HOME") + " in " + plan.workDir());
        try {
            return new Attempt(plan, JoustNewRunner.run(plan.command(), plan.env(), plan.workDir(), TIMEOUT, cancelled()));
        } finally {
            JoustPrivateTemp.deleteTree(plan.workDir());
        }
    }

    private static Attempt inBackground(com.intellij.openapi.util.ThrowableComputable<Attempt, IOException> work, String title,
                                        @Nullable Project project) throws IOException {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            return ProgressManager.getInstance().runProcessWithProgressSynchronously(work, title, true, project);
        }
        return work.compute();
    }

    private static BooleanSupplier cancelled() {
        return () -> {
            try {
                ProgressManager.checkCanceled();
                return false;
            } catch (CancellationException e) {
                // ProcessCanceledException is a CancellationException: the user cancelled the progress
                return true;
            }
        };
    }

    private static void fail(@Nullable Project project, String message) {
        LOG.warn("JOUST wizard: " + message);
        ApplicationManager.getApplication().invokeLater(() ->
                JoustNotifications.show(project, "JOUST project not created", message, NotificationType.ERROR));
    }

    // ---- the wizard's fields (plain text only; nothing here runs a process)

    static final class LanguageField extends WizardInputField<JComboBox<JoustLanguage>> {
        private final JComboBox<JoustLanguage> combo = new JComboBox<>(JoustLanguage.values());

        LanguageField() {
            super("joustLanguage", JoustLanguage.JSC.cliName());
            combo.setSelectedItem(JoustLanguage.JSC);
        }

        @Override
        public @NlsContexts.Label String getLabel() {
            return "Language";
        }

        @Override
        public JComboBox<JoustLanguage> getComponent() {
            return combo;
        }

        @Override
        public String getValue() {
            return ((JoustLanguage) combo.getSelectedItem()).cliName();
        }
    }

    static final class TemplateField extends WizardInputField<JComboBox<JoustTemplate>> {
        private final JComboBox<JoustTemplate> combo = new JComboBox<>(JoustTemplate.values());

        TemplateField() {
            super("joustTemplate", JoustTemplate.GRADLE.cliName());
            combo.setSelectedItem(JoustTemplate.GRADLE);
        }

        @Override
        public @NlsContexts.Label String getLabel() {
            return "Template";
        }

        @Override
        public JComboBox<JoustTemplate> getComponent() {
            return combo;
        }

        @Override
        public String getValue() {
            return ((JoustTemplate) combo.getSelectedItem()).cliName();
        }
    }

    final class PackageField extends WizardInputField<JBTextField> {
        private final JBTextField text = new JBTextField("app");

        PackageField() {
            super("joustPackage", "app");
        }

        @Override
        public @NlsContexts.Label String getLabel() {
            return "Package (Gradle only)";
        }

        @Override
        public JBTextField getComponent() {
            return text;
        }

        @Override
        public String getValue() {
            return text.getText() == null ? "" : text.getText().trim();
        }

        @Override
        public boolean validate() throws ConfigurationException {
            if (!template.takesPackage()) return true;
            String problem = JoustNewCommand.packageProblem(getValue());
            if (problem != null) throw new ConfigurationException(problem, "Package");
            return true;
        }
    }

    static final class JdkField extends WizardInputField<JComboBox<Sdk>> {
        private final JComboBox<Sdk> combo;

        JdkField() {
            super("joustJdk", "");
            List<Sdk> sdks = new ArrayList<>(ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()));
            sdks.sort(Comparator.comparing((Sdk s) -> {
                Integer m = major(s.getVersionString());
                return m == null ? 0 : m;
            }).reversed());
            combo = new JComboBox<>(sdks.toArray(new Sdk[0]));
            combo.setRenderer(SimpleListCellRenderer.create("No JDK configured (File | Project Structure | SDKs)",
                    s -> PlainText.label(s.getName(), 60) + (s.getVersionString() == null ? ""
                            : "  (" + PlainText.label(s.getVersionString(), 60) + ")")));
            if (!sdks.isEmpty()) combo.setSelectedIndex(0);
        }

        @Override
        public @NlsContexts.Label String getLabel() {
            return "JDK (JAVA_HOME)";
        }

        @Override
        public JComboBox<Sdk> getComponent() {
            return combo;
        }

        @Override
        public String getValue() {
            Sdk s = (Sdk) combo.getSelectedItem();
            return s == null ? "" : s.getName();
        }

        @Override
        public boolean validate() throws ConfigurationException {
            Sdk s = (Sdk) combo.getSelectedItem();
            if (s == null || s.getHomePath() == null) {
                throw new ConfigurationException("Choose a JDK from IntelliJ's SDK table (File | Project Structure | SDKs).", "JDK");
            }
            return true;
        }
    }

    /** The plain-text note, and the wizard's checks that need no target directory (nothing is executed). */
    final class NoteField extends WizardInputField<JBLabel> {
        private final JBLabel label = new JBLabel();

        NoteField() {
            super("joustNote", "");
        }

        @Override
        public @NlsContexts.Label String getLabel() {
            return "";
        }

        @Override
        public JBLabel getComponent() {
            return label;
        }

        @Override
        public String getValue() {
            return "";
        }

        @Override
        public boolean validate() throws ConfigurationException {
            JoustToolsState state = JoustToolsState.getInstance();
            JoustToolsState.Data d = state.getState();
            Sdk s = jdk;
            String problem = JoustNewPlanner.preflight(
                    new JoustNewPlanner.Request(template, language, template.takesPackage() ? pkg : null, null,
                            s == null ? null : s.getHomePath()),
                    new JoustNewPlanner.ToolSettings(d.joustCliPath, state.validatedJoustCli(), d.nodePath, state.validatedNode()),
                    System.getenv(), JoustForbiddenRoots.ofOpenProjects(), JoustPaths.Flavor.current());
            if (problem != null) throw new ConfigurationException(problem, "JOUST");
            return true;
        }
    }
}
