package com.bloxbean.intelliada.idea.joust.host;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ADR-005 D11: the project's dev hosts, one per contract module. A host starts only from a user action in a trusted
 * project (D1), bound to that action's {@link JoustRunGuard}: revoking trust or closing the project stops it (I20). The
 * launch spec is produced by the project's own Gradle ({@code joustHostSpec}), copied into a private snapshot (deleted
 * when the host ends), and the first start of each module in an IDE session is confirmed by the user.
 */
@Service(Service.Level.PROJECT)
public final class JoustHostService implements Disposable {

    static final int STDERR_CAP = 1024 * 1024;

    /** Modules whose host start the user confirmed in this IDE session (D11 step 5). */
    private static final Set<String> CONFIRMED = ConcurrentHashMap.newKeySet();

    /** A running host. {@code session} is live until it ends; {@code initialize} is the caller's next call. */
    public record Running(Path moduleDir, HostSpec spec, HostSnapshot snapshot, Process process, HostSession session,
                          HostStderr stderr, @Nullable String joustVersion) {
    }

    /** Why a start did not happen; plain text for the panel. */
    public static final class NotStarted extends Exception {
        public NotStarted(String why) {
            super(why, null, false, false);
        }
    }

    /** Asks the user to confirm a first start; replaced in tests. Runs on the EDT. */
    public interface Confirm {
        boolean ask(Project project, Path moduleDir, HostSpec spec, @Nullable String joustVersion);
    }

    static volatile Confirm confirmForTests;

    private final Project project;
    private final Map<Path, Running> hosts = new ConcurrentHashMap<>();

    public JoustHostService(Project project) {
        this.project = project;
        HostSnapshot.delete(snapshotsBase());   // leftovers of an earlier IDE run; no host of this project is alive yet
    }

    public static JoustHostService getInstance(Project project) {
        return project.getService(JoustHostService.class);
    }

    /** The running host of {@code moduleDir}, or null. */
    public @Nullable Running running(Path moduleDir) {
        Running r = hosts.get(moduleDir);
        return r != null && r.session().ended() == null ? r : null;
    }

    /**
     * Runs {@code joustHostSpec}, snapshots and starts the host of {@code moduleDir}. Off the EDT; blocks. The returned
     * session has not been initialized.
     */
    public Running start(JoustRunGuard guard, Path moduleDir, HostSession.Reads reads, Consumer<String> onEnd)
            throws NotStarted, InterruptedException {
        Running existing = running(moduleDir);
        if (existing != null) return existing;
        if (!guard.live()) throw new NotStarted("the project is not trusted, or was closed");
        JoustGradleTasks tasks = JoustGradleTasks.getInstance();
        if (tasks == null) throw new NotStarted("the run panel needs IntelliJ's Gradle plugin");
        JoustGradleTasks.Linked linked = tasks.linked(project, moduleDir);
        if (linked == null) throw new NotStarted("this module is not part of a linked Gradle build");
        String task = linked.gradlePath().equals(":") ? ":joustHostSpec" : linked.gradlePath() + ":joustHostSpec";
        JoustAbiReview.Result r = tasks.run(guard, linked.rootDir(), List.of(task), "JOUST: dev-host launch spec");
        if (r.cancelled()) throw new NotStarted("cancelled");
        if (!r.ok()) {
            throw new NotStarted(r.output().contains("joustHostSpec") && r.output().contains("not found")
                    ? "this project's JOUST has no dev host (joustHostSpec); it needs a JOUST with dev-host protocol 1"
                    : "joustHostSpec failed; see the Gradle output");
        }
        if (!guard.live()) throw new NotStarted("the project is not trusted, or was closed");
        HostSpec spec;
        HostSnapshot snapshot;
        try {
            spec = HostSpec.read(moduleDir);
            snapshot = HostSnapshot.create(spec, snapshotsBase());
        } catch (HostSpec.Invalid e) {
            throw new NotStarted(e.getMessage());
        } catch (IOException e) {
            throw new NotStarted("could not copy the launch set: " + e.getMessage());
        }
        boolean started = false;
        try {
            String version = HostVersions.runtimeVersion(snapshot);
            if (!confirmed(moduleDir, spec, version) || !guard.live()) throw new NotStarted("not started");
            JoustTaskTracker.Run run = JoustTaskTracker.getInstance(project).start(guard);
            HostLauncher.Started s;
            try {
                s = HostLauncher.start(spec, snapshot, moduleDir, STDERR_CAP);
            } catch (IOException e) {
                throw new NotStarted(e.getMessage());
            }
            Process p = s.process();
            HostSession session = new HostSession(p.getInputStream(), p.getOutputStream(), () -> HostLauncher.stop(p), reads,
                    why -> {
                        hosts.remove(moduleDir);
                        onEnd.accept(why);
                    });
            run.attach(session::close);
            p.onExit().thenRun(() -> {
                session.close();
                snapshot.delete();
            });
            Running running = new Running(moduleDir, spec, snapshot, p, session, s.stderr(), version);
            hosts.put(moduleDir, running);
            started = true;
            return running;
        } finally {
            if (!started) snapshot.delete();
        }
    }

    /** Stops the host of {@code moduleDir} in order (shutdown, exit, end of output), then ends its process tree. */
    public void stop(Path moduleDir) {
        Running r = hosts.remove(moduleDir);
        if (r == null) return;
        try {
            r.session().shutdown(Duration.ofSeconds(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            HostLauncher.stop(r.process());
        }
    }

    @Override
    public void dispose() {
        for (Running r : hosts.values()) {
            r.session().close();
            HostLauncher.stop(r.process());
            r.snapshot().delete();
        }
        hosts.clear();
    }

    private boolean confirmed(Path moduleDir, HostSpec spec, @Nullable String version) {
        String key = project.getLocationHash() + "|" + moduleDir;
        if (CONFIRMED.contains(key)) return true;
        Confirm c = confirmForTests;
        boolean[] yes = {false};
        ApplicationManager.getApplication().invokeAndWait(() -> {
            if (project.isDisposed()) return;
            if (c != null) {
                yes[0] = c.ask(project, moduleDir, spec, version);
                return;
            }
            String text = "Start this project's JOUST dev host?\n\n"
                    + "Module: " + PlainText.literal(String.valueOf(moduleDir), 300) + "\n"
                    + "Java: " + PlainText.literal(spec.java().toString(), 300) + "\n"
                    + "Main class: " + spec.mainClass() + "\n"
                    + "JOUST: " + (version == null ? "unknown" : PlainText.literal(version, 64)) + "\n\n"
                    + "It runs the project's own code, from a private copy of its build, with no network endpoint or key.";
            yes[0] = Messages.showYesNoDialog(project, text, "JOUST Dev Host", "Start", "Cancel", Messages.getQuestionIcon())
                    == Messages.YES;
        });
        if (yes[0]) CONFIRMED.add(key);
        return yes[0];
    }

    Path snapshotsBase() {
        return Path.of(PathManager.getSystemPath(), "intelliada", "joust", "snapshots", sha(project.getLocationHash()));
    }

    private static String sha(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /** For tests. */
    static void resetConfirmationsForTests() {
        CONFIRMED.clear();
    }
}
