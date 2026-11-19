package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.core.JoustLaunch;
import com.bloxbean.intelliada.idea.joust.core.JoustLaunchPlanner;
import com.bloxbean.intelliada.idea.joust.core.JoustPaths;
import com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots;
import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ADR-004 D3: starts {@code joust-lsp} with exactly I3's command line, planned afresh at every start from the
 * application settings. Nothing comes from the project: not the executable, an argument, the working directory (the
 * directory of the real {@code joust-lsp.js}) or the environment (the IDE's own, minus joust-lsp's test hooks).
 */
public final class JoustStreamConnectionProvider extends ProcessStreamConnectionProvider {

    /** Tests observe every command line IntelliAda launches here (I1, I3). */
    private static volatile Consumer<List<String>> launchRecorder;

    private static final java.util.Set<Path> LAUNCHED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The Node and joust-lsp real paths servers were started with in this session (the project-open check uses them). */
    public static java.util.Set<Path> launchedPaths() {
        return java.util.Set.copyOf(LAUNCHED);
    }

    /** For tests: receives each command line just before it is started; {@code null} to stop. */
    public static void setLaunchRecorderForTests(Consumer<List<String>> recorder) {
        launchRecorder = recorder;
    }

    /** The only text IntelliAda hands to LSP4IJ when a start is refused. */
    static final String SAFE_FAILURE = "The JOUST tools are not valid. See the JOUST notification, or Settings | Tools | JOUST.";

    private final Project project;

    public JoustStreamConnectionProvider(Project project) {
        this.project = project;
    }

    /** IntelliAda's private storage for joust-lsp, under the IDE's system directory, outside every project. */
    public static Path storageDir() {
        return Path.of(PathManager.getSystemPath(), "intelliada", "joust-lsp");
    }

    @Override
    public void start() throws CannotStartProcessException {
        JoustLaunchPlanner.Plan plan = JoustLaunchPlanner.plan(JoustToolsState.getInstance().settings(),
                JoustForbiddenRoots.ofOpenProjects(), storageDir(), JoustPaths.Flavor.current());
        if (!plan.ok()) {
            var n = JoustNotifications.create("JOUST language server not started", plan.error(), NotificationType.WARNING);
            n.addAction(NotificationAction.createSimpleExpiring("Configure JOUST…",
                    () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, "JOUST")));
            n.notify(project);
            // LSP4IJ shows this message in a notification of its own, unescaped: keep it fixed and free of paths
            // (M1-CDX-7). The details are in IntelliAda's escaped notification above.
            throw new CannotStartProcessException(SAFE_FAILURE);
        }
        JoustLaunch launch = plan.launch();
        setCommands(launch.command());
        setWorkingDirectory(launch.workingDirectory().toString());
        setIncludeSystemEnvironmentVariables(true);
        Consumer<List<String>> rec = launchRecorder;
        if (rec != null) rec.accept(launch.command());
        LAUNCHED.add(Path.of(launch.command().get(0)));
        LAUNCHED.add(Path.of(launch.command().get(1)));
        try {
            super.start();
        } catch (RuntimeException e) {
            throw failed(e);
        }
    }

    /**
     * LSP4IJ builds its own notification from these exceptions' messages, which would carry the command and working
     * directory unescaped (M1-CDX-7). IntelliAda shows the details escaped and hands LSP4IJ only {@link #SAFE_FAILURE}.
     */
    @Override
    public void ensureIsAlive() throws CannotStartProcessException {
        try {
            super.ensureIsAlive();
        } catch (RuntimeException e) {
            throw failed(e);
        }
    }

    private CannotStartProcessException failed(Exception e) {
        JoustNotifications.show(project, "JOUST language server stopped",
                "joust-lsp did not start or exited early: " + e.getMessage(), NotificationType.WARNING);
        return new CannotStartProcessException(SAFE_FAILURE);
    }

    /** The server's output, with JSON-RPC error messages escaped before LSP4J reads them (M1-SEC-1). */
    @Override
    public java.io.InputStream getInputStream() {
        java.io.InputStream raw = super.getInputStream();
        if (raw == null) return null;
        if (sanitized == null || sanitizedFrom != raw) {
            sanitizedFrom = raw;
            sanitized = new JoustSanitizingInput(raw);
        }
        return sanitized;
    }

    private java.io.InputStream sanitizedFrom;
    private java.io.InputStream sanitized;

    @Override
    protected ProcessBuilder createProcessBuilder() {
        ProcessBuilder pb = super.createProcessBuilder();
        for (String k : JoustLaunch.REMOVED_ENVIRONMENT) pb.environment().remove(k);
        return pb;
    }

    /**
     * D3: {@code {client: "intelliada", buildOnSave: false}}. Never {@code client: "vscode-joust"} (I5), and no
     * {@code trusted}, {@code allowedRoots}, {@code workspaceFile} or {@code storageDir}.
     */
    @Override
    public Object getInitializationOptions(VirtualFile rootUri) {
        return initializationOptions(JoustToolsState.getInstance().getState().buildOnSave);
    }

    static Map<String, Object> initializationOptions(boolean buildOnSave) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("client", "intelliada");
        o.put("buildOnSave", buildOnSave);
        return o;
    }
}
