package com.bloxbean.intelliada.idea.joust.host;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbiReview;
import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks;
import com.bloxbean.intelliada.idea.joust.tasks.JoustRunGuard;
import com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * when the host exits), the first start of each module in an IDE session is confirmed by the user, and the session is
 * handed out only after a verified {@code initialize} (I24).
 */
@Service(Service.Level.PROJECT)
public final class JoustHostService implements Disposable {

    static final int STDERR_CAP = 1024 * 1024;
    static final Duration INIT_TIMEOUT = Duration.ofSeconds(120);

    /** Modules whose host start the user confirmed in this IDE session (D11 step 5). */
    private static final Set<String> CONFIRMED = ConcurrentHashMap.newKeySet();

    /** A running, initialized host. */
    public record Running(Path moduleDir, HostSpec spec, HostSnapshot snapshot, Process process, HostSession session,
                          HostStderr stderr, @Nullable String joustVersion, JsonNode initialize) {
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

    /** A module's slot, reserved before anything starts and released only once its process has exited (M31A-CDX-3). */
    private static final class Slot {
        volatile Running running;
    }

    private final Project project;
    private final Map<Path, Slot> slots = new ConcurrentHashMap<>();

    public JoustHostService(Project project) {
        this.project = project;
        // Leftovers of an earlier IDE run (no host of this project is alive yet), off the calling thread (M31A-DES-8).
        Path base = snapshotsBase();
        ApplicationManager.getApplication().executeOnPooledThread(() -> HostSnapshot.delete(base));
    }

    public static JoustHostService getInstance(Project project) {
        return project.getService(JoustHostService.class);
    }

    static Path key(Path moduleDir) {
        try {
            return moduleDir.toRealPath();
        } catch (IOException e) {
            return moduleDir.toAbsolutePath().normalize();
        }
    }

    /** The running host of {@code moduleDir}, or null. */
    public @Nullable Running running(Path moduleDir) {
        Slot s = slots.get(key(moduleDir));
        Running r = s == null ? null : s.running;
        return r != null && r.session().ended() == null ? r : null;
    }

    /**
     * Runs {@code joustHostSpec}, then {@link #launch}. Off the EDT; blocks. {@code initialize} holds every
     * {@code initialize} parameter except {@code build}, which this service fills from the verified snapshot.
     */
    public Running start(JoustRunGuard guard, Path moduleDir, ObjectNode initialize, HostSession.Reads reads,
                         Consumer<String> onEnd) throws NotStarted, InterruptedException {
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
        return launch(guard, moduleDir, initialize, reads, onEnd);
    }

    /** The part of {@link #start} after {@code joustHostSpec}: snapshot, confirm, start, {@code initialize}. Off the EDT. */
    Running launch(JoustRunGuard guard, Path moduleDir, ObjectNode initialize, HostSession.Reads reads, Consumer<String> onEnd)
            throws NotStarted, InterruptedException {
        Path key = key(moduleDir);
        Slot slot = new Slot();
        if (slots.putIfAbsent(key, slot) != null) {
            Running r = running(moduleDir);
            if (r != null) return r;
            throw new NotStarted("a host for this module is starting or stopping");
        }
        JoustTaskTracker tracker = JoustTaskTracker.getInstance(project);
        HostSnapshot snapshot = null;
        JoustTaskTracker.Run run = null;
        Process process = null;
        HostSession session = null;
        boolean handedOut = false;
        try {
            HostSpec spec;
            try {
                spec = HostSpec.read(moduleDir);
                HostLauncher.javaVersion(spec.java());   // D11 step 2, before the copy and the confirmation (M31A-DES-9)
                snapshot = HostSnapshot.create(spec, snapshotsBase());
            } catch (HostSpec.Invalid e) {
                throw new NotStarted(e.getMessage());
            } catch (IOException e) {
                throw new NotStarted("could not copy the launch set: " + e.getMessage());
            }
            String version = HostVersions.runtimeVersion(snapshot);
            if (!confirmed(moduleDir, spec, version) || !guard.live()) throw new NotStarted("not started");
            // Registered before the process exists; null when trust went away meanwhile (M31A-CDX-2).
            run = tracker.start(guard);
            if (run == null) throw new NotStarted("the project is not trusted, or was closed");
            HostLauncher.Started s;
            try {
                s = HostLauncher.start(spec, snapshot, moduleDir, STDERR_CAP);
            } catch (IOException e) {
                throw new NotStarted(e.getMessage());
            }
            process = s.process();
            Process p = process;
            session = new HostSession(p.getInputStream(), p.getOutputStream(), () -> HostLauncher.stop(p), reads, onEnd);
            HostSession sess = session;
            run.attach(sess::close);
            // The process's exit releases the snapshot, the tracker entry and the module's slot; the reader, not this
            // callback, decides how the session ended (the final frames and EOF are its to classify; M31A-CDX-5, -8).
            HostSnapshot snap = snapshot;
            JoustTaskTracker.Run tracked = run;
            p.onExit().thenRun(() -> {
                snap.delete();
                tracker.done(tracked);
                slots.remove(key, slot);
            });
            ObjectNode params = initialize.deepCopy();
            params.putObject("build").put("snapshot", snapshot.root().toString()).put("digest", snapshot.digest());
            JsonNode init;
            try {
                init = session.initialize(params, snapshot.digest(), INIT_TIMEOUT);
            } catch (HostError e) {
                throw new NotStarted("the host refused to initialize: " + e.kind() + "/" + e.code() + ": "
                        + PlainText.literal(e.getMessage(), 300));
            } catch (HostSession.Ended e) {
                throw new NotStarted("the host did not start: " + e.getMessage() + " (see the JOUST host console: "
                        + PlainText.literal(s.stderr().lastLine(), 300) + ")");
            }
            if (!guard.live()) throw new NotStarted("the project is not trusted, or was closed");
            Running running = new Running(moduleDir, spec, snapshot, p, session, s.stderr(), version, init);
            slot.running = running;
            handedOut = true;
            return running;
        } finally {
            if (!handedOut) {
                if (session != null) session.close();
                if (process != null) {
                    HostLauncher.stop(process);    // the exit callback then releases the rest
                } else {
                    if (snapshot != null) snapshot.delete();
                    if (run != null) tracker.done(run);
                    slots.remove(key, slot);
                }
            }
        }
    }

    /** Stops the host of {@code moduleDir} in order (shutdown, exit, end of output), then ends its process tree. */
    public void stop(Path moduleDir) {
        Slot s = slots.get(key(moduleDir));
        Running r = s == null ? null : s.running;
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
        for (Slot s : slots.values()) {
            Running r = s.running;
            if (r == null) continue;
            r.session().close();
            HostLauncher.stop(r.process());
            r.snapshot().delete();
        }
    }

    /** Asks on the EDT and waits: {@link #start} must not be called under a read action or a modal progress. */
    private boolean confirmed(Path moduleDir, HostSpec spec, @Nullable String version) {
        // Keyed on what the dialog shows: another java or JOUST version is confirmed again (M31A-DES-9).
        String key = project.getLocationHash() + "|" + key(moduleDir) + "|" + spec.java() + "|" + version;
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
