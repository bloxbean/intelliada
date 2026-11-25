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
                          HostStderr stderr, @Nullable String joustVersion, JsonNode initialize, ObjectNode requested,
                          String binding, HostLauncher.Started started, int javaFeature) {
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

    public static volatile Confirm confirmForTests;

    /** A module's slot, reserved before anything starts and released only once its process has exited (M31A-CDX-3). */
    private static final class Slot {
        volatile Running running;
        volatile HostLauncher.Started started;
        volatile boolean stopping;
    }

    private final Project project;
    private final Map<Path, Slot> slots = new ConcurrentHashMap<>();

    public JoustHostService(Project project) {
        this.project = project;
        // Snapshots of this IDE session live in a directory of their own, held by a lock while the service lives; the
        // sweep removes only other sessions' directories whose lock is free (their IDE is gone), so it never races a
        // snapshot of this session (M31A-CDX-17, M31A-DES-8). It runs off the calling thread.
        this.session = sessionDir();
        ApplicationManager.getApplication().executeOnPooledThread(this::sweepOtherSessions);
    }

    private final Path session;
    private java.nio.channels.FileChannel sessionLockChannel;
    private java.nio.channels.FileLock sessionLock;

    /**
     * Creates this session's directory under a temporary name, locks it, and only then renames it into the sweepers'
     * view, so no sweeper can claim it in between (M31A-CDX-37). Without a lock, launches are refused.
     */
    private Path sessionDir() {
        String id = java.util.UUID.randomUUID().toString();
        Path tmp = projectBase().resolve(".tmp-session-" + id);
        Path dir = projectBase().resolve(id);
        try {
            java.nio.file.Files.createDirectories(tmp);
            sessionLockChannel = java.nio.channels.FileChannel.open(tmp.resolve(".lock"),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
            sessionLock = sessionLockChannel.tryLock();
            java.nio.file.Files.move(tmp, dir, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            sessionLock = null;
        }
        return dir;
    }

    private void sweepOtherSessions() {
        try (var list = java.nio.file.Files.list(projectBase())) {
            for (Path d : (Iterable<Path>) list::iterator) {
                if (d.equals(session) || !java.nio.file.Files.isDirectory(d, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                // A session still being published (.tmp-session-*) is left alone unless it is a day old.
                if (d.getFileName().toString().startsWith(".tmp-session-")) {
                    try {
                        if (java.nio.file.Files.getLastModifiedTime(d).toMillis() > System.currentTimeMillis() - 86_400_000L) continue;
                    } catch (IOException e) {
                        continue;
                    }
                }
                Path lock = d.resolve(".lock");
                // The claim is held while deleting, so its owner cannot reappear midway.
                try (var ch = java.nio.channels.FileChannel.open(lock, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.WRITE); var l = ch.tryLock()) {
                    if (l != null) HostSnapshot.delete(d);
                } catch (IOException | java.nio.channels.OverlappingFileLockException e) {
                    // held by a live session
                }
            }
        } catch (IOException ignored) {
            // nothing to sweep
        }
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
    /**
     * The running host of {@code moduleDir} for an action bound to {@code guard}, if it serves the requested network and
     * clock; otherwise refused (M31A-CDX-18).
     */
    @Nullable Running reuse(JoustRunGuard guard, Path moduleDir, ObjectNode initialize, String binding) throws NotStarted {
        Running r = running(moduleDir);
        if (r == null) return null;
        if (!guard.live()) throw new NotStarted("the project is not trusted, or was closed");
        // The bound node too: two configurations of one chain have equal parameters but separate providers (M31A-CDX-32).
        if (!r.binding().equals(binding) || !sameBinding(r.requested(), initialize)) {
            throw new NotStarted("this module's host runs on another network; stop it before binding another");
        }
        return r;
    }

    private static boolean sameBinding(ObjectNode a, ObjectNode b) {
        java.util.Comparator<JsonNode> numeric = (x, y) -> x.isNumber() && y.isNumber()
                ? x.decimalValue().compareTo(y.decimalValue()) : x.equals(y) ? 0 : 1;
        for (String f : new String[]{"network", "clock", "time", "limits"}) {
            if (!a.path(f).equals(numeric, b.path(f))) return false;
        }
        return true;
    }

    public @Nullable Running running(Path moduleDir) {
        Slot s = slots.get(key(moduleDir));
        Running r = s == null ? null : s.running;
        return r != null && r.session().ended() == null ? r : null;
    }

    /**
     * Runs {@code joustHostSpec}, then {@link #launch}. Off the EDT; blocks. {@code initialize} holds every
     * {@code initialize} parameter except {@code build}, which this service fills from the verified snapshot.
     */
    public Running start(JoustRunGuard guard, Path moduleDir, ObjectNode initialize, String binding, HostSession.Reads reads,
                         Consumer<String> onEnd) throws NotStarted, InterruptedException {
        Running existing = reuse(guard, moduleDir, initialize, binding);
        if (existing != null) return existing;
        if (!guard.live()) throw new NotStarted("the project is not trusted, or was closed");
        // The slot is reserved before the build runs, so Stop during joustHostSpec is recorded (M31A-CDX-31).
        Path key = key(moduleDir);
        Slot slot = new Slot();
        if (slots.putIfAbsent(key, slot) != null) {
            Running r = reuse(guard, moduleDir, initialize, binding);
            if (r != null) return r;
            throw new NotStarted("a host for this module is starting or stopping");
        }
        boolean handed = false;
        try {
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
            Running running = launchIn(key, slot, guard, moduleDir, initialize, binding, reads, onEnd);
            handed = true;
            return running;
        } finally {
            if (!handed && slot.started == null) slots.remove(key, slot);
        }
    }

    /** The part of {@link #start} after {@code joustHostSpec}: snapshot, confirm, start, {@code initialize}. Off the EDT. */
    public Running launch(JoustRunGuard guard, Path moduleDir, ObjectNode initialize, String binding, HostSession.Reads reads,
                   Consumer<String> onEnd) throws NotStarted, InterruptedException {
        Path key = key(moduleDir);
        Slot slot = new Slot();
        if (slots.putIfAbsent(key, slot) != null) {
            Running r = reuse(guard, moduleDir, initialize, binding);
            if (r != null) return r;
            throw new NotStarted("a host for this module is starting or stopping");
        }
        return launchIn(key, slot, guard, moduleDir, initialize, binding, reads, onEnd);
    }

    /** {@link #launch} in a slot already reserved by the caller; releases it on every failure. */
    private Running launchIn(Path key, Slot slot, JoustRunGuard guard, Path moduleDir, ObjectNode initialize, String binding,
                             HostSession.Reads reads, Consumer<String> onEnd) throws NotStarted, InterruptedException {
        if (sessionLock == null) throw new NotStarted("IntelliAda could not lock its snapshot directory for this session");
        JoustTaskTracker tracker = JoustTaskTracker.getInstance(project);
        HostSnapshot snapshot = null;
        JoustTaskTracker.Run run = null;
        Process process = null;
        HostSession session = null;
        boolean handedOut = false;
        try {
            HostSpec spec;
            int feature;
            try {
                spec = HostSpec.read(moduleDir);
                feature = HostLauncher.javaVersion(spec.java());   // D11 step 2, before the copy and the confirmation (M31A-DES-9)
                snapshot = HostSnapshot.create(spec, snapshotsBase());
            } catch (HostSpec.Invalid e) {
                throw new NotStarted(e.getMessage());
            } catch (IOException e) {
                throw new NotStarted("could not copy the launch set: " + e.getMessage());
            }
            String version = HostVersions.runtimeVersion(snapshot, feature);
            if (slot.stopping) throw new NotStarted("stopped");
            if (!confirmed(moduleDir, spec, version) || !guard.live()) throw new NotStarted("not started");
            if (slot.stopping) throw new NotStarted("stopped");   // Stop before any process exists (M31A-CDX-31)
            // Registered before the process exists; null when trust went away meanwhile (M31A-CDX-2).
            run = tracker.start(guard);
            if (run == null) throw new NotStarted("the project is not trusted, or was closed");
            HostLauncher.Started s;
            HostSnapshot snap = snapshot;
            JoustTaskTracker.Run tracked = run;
            // The final Stop decision, the process's creation and its ownership are one step under the slot's lock: Stop
            // either comes first (no process) or finds slot.started (M31A-CDX-41). The exit cleanup is installed before
            // anything can fail: the tree's end releases the snapshot, the tracker entry and the slot (M31A-CDX-21, -22).
            synchronized (slot) {
                if (slot.stopping) throw new NotStarted("stopped");
                try {
                    s = HostLauncher.start(spec, snapshot, moduleDir, STDERR_CAP, feature);   // refused before any process (M33A-CDX-48)
                } catch (IOException e) {
                    throw new NotStarted(e.getMessage());
                }
                slot.started = s;
                s.process().onExit().thenRun(() -> releaseWhenTreeGone(s, snap, tracker, tracked, key, slot, 0));
            }
            process = s.process();
            Process p = process;
            session = new HostSession(p.getInputStream(), p.getOutputStream(), s::stop, reads, onEnd);
            HostSession sess = session;
            run.attach(sess::close);
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
            if (slot.stopping) throw new NotStarted("stopped");   // Stop during startup (M31A-CDX-20)
            Running running = new Running(moduleDir, spec, snapshot, p, session, s.stderr(), version, init, initialize.deepCopy(), binding, s,
                    feature);   // the launch JDK, as read at launch (M33A-CDX-30)
            slot.running = running;
            handedOut = true;
            return running;
        } finally {
            if (!handedOut) {
                if (session != null) session.close();
                if (process != null) {
                    HostLauncher.Started st = slot.started;
                    if (st != null) st.stop();
                    else HostLauncher.stop(process);    // the exit callback then releases the rest
                } else {
                    if (snapshot != null) snapshot.delete();
                    if (run != null) tracker.done(run);
                    slots.remove(key, slot);
                }
            }
        }
    }

    /**
     * Stops the host of {@code moduleDir} in order (shutdown, exit, end of output), then ends its process tree. During
     * startup it ends the starting process at once, and the start is refused (M31A-CDX-20).
     */
    public void stop(Path moduleDir) {
        Slot s = slots.get(key(moduleDir));
        if (s == null) return;
        Running r;
        HostLauncher.Started st;
        synchronized (s) {
            s.stopping = true;
            r = s.running;
            st = s.started;
        }
        if (r == null) {
            if (st != null) st.stop();
            return;
        }
        try {
            r.session().shutdown(Duration.ofSeconds(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            r.started().stop();
        }
    }

    /** Releases a host's resources once its whole process tree is gone (polled briefly after the root's exit). */
    private void releaseWhenTreeGone(HostLauncher.Started s, HostSnapshot snap, JoustTaskTracker tracker, JoustTaskTracker.Run run,
                                     Path key, Slot slot, int attempt) {
        // Ownership is kept while any tracked process lives, retried with backoff up to every 5 s (M31A-CDX-33).
        if (!s.treeGone()) {
            s.stop();
            if (attempt == 20) {
                // About a minute: a process that cannot be ended (a privileged helper) must not lock the module for the
                // rest of the session. The slot is released and the survivors named; the snapshot and the tracker entry
                // are still released once they are gone (M31A-SEC-14, M31A-DES-22).
                String pids = s.owned().stream().filter(ProcessHandle::isAlive).map(h -> String.valueOf(h.pid()))
                        .collect(java.util.stream.Collectors.joining(", "));
                com.intellij.openapi.diagnostic.Logger.getInstance(JoustHostService.class)
                        .warn("JOUST dev host: processes still alive after stop: " + (s.process().isAlive() ? s.process().pid() + " " : "") + pids);
                slots.remove(key, slot);
            }
            long delay = Math.min(5000, 250L << Math.min(attempt, 5));
            com.intellij.util.concurrency.AppExecutorUtil.getAppScheduledExecutorService().schedule(
                    () -> releaseWhenTreeGone(s, snap, tracker, run, key, slot, attempt + 1), delay, java.util.concurrent.TimeUnit.MILLISECONDS);
            return;
        }
        snap.delete();
        tracker.done(run);
        slots.remove(key, slot);
        if (disposed && slots.isEmpty()) releaseSessionLock();
    }

    private volatile boolean disposed;

    private synchronized void releaseSessionLock() {
        try {
            if (sessionLock != null) sessionLock.release();
            if (sessionLockChannel != null) sessionLockChannel.close();
        } catch (IOException ignored) {
            // the directory is swept by a later session
        }
        sessionLock = null;
    }

    @Override
    public void dispose() {
        for (Slot s : slots.values()) {
            Running r = s.running;
            if (r == null) continue;
            r.session().close();
            r.started().stop();   // the exit callback deletes the snapshot once the tree is gone (M31A-CDX-33)
        }
        disposed = true;
        // The session directory stays locked until the last tracked tree is gone (M31A-CDX-42).
        if (slots.isEmpty()) releaseSessionLock();
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

    /** This IDE session's snapshot directory for the project. */
    Path snapshotsBase() {
        return session;
    }

    private Path projectBase() {
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
