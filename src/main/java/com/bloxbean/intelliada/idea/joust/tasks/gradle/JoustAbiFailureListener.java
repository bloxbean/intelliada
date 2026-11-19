package com.bloxbean.intelliada.idea.joust.tasks.gradle;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbi;
import com.bloxbean.intelliada.idea.joust.project.JoustDiagnosticLinks;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectService;
import com.bloxbean.intelliada.idea.joust.project.actions.JoustProjectAction;
import com.bloxbean.intelliada.idea.joust.tasks.JoustGradleTasks;
import com.bloxbean.intelliada.idea.joust.trust.JoustTrust;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADR-004 D8 step 1 for every Gradle run, including ones the user starts from the Gradle tool window: a JSC0002 or
 * JSC0003 failure in a task's output becomes one notification per module and contract, naming the module, with
 * Review ABI change…. The module is the one whose task failed when the output says so (M2-DES-5, M2-CDX-13); when a
 * source path is in several modules and the output does not say which failed, the notification names them and offers
 * no action (D8 step 2). Only matching lines and failed task paths are kept, each bounded as it arrives (M2-CDX-15).
 * Nothing runs until the user chooses the action, which checks trust again; an untrusted project gets no notification.
 */
public final class JoustAbiFailureListener implements ExternalSystemTaskNotificationListener {

    static final int MAX_LINES = 64;
    static final int MAX_LINE = 2_000;
    static final int MAX_TASKS = 64;
    private static final Pattern TASK_HEADER = Pattern.compile("^> Task (:[A-Za-z0-9_.:-]{1,400})(?:\\s.*)?$");
    private static final Pattern FAILED_TASK = Pattern.compile("(?:> Task (:[A-Za-z0-9_.:-]{1,400}) FAILED|Execution failed for task '(:[A-Za-z0-9_.:-]{1,400})')");
    /** The JOUST tasks that report JSC0002/JSC0003 (JoustPlugin): only their failures attribute a diagnostic. */
    static final Set<String> ABI_TASKS = Set.of("generateJsc", "checkJscAbi", "jscAbiDiff");

    /** A diagnostic line and the task whose output it was in (the latest {@code > Task} header), or null. */
    public record Diagnostic(String line, String task) {
    }

    private static final class Pending {
        final StringBuilder partial = new StringBuilder();
        boolean discarding;
        String currentTask;
        int generation = -1;
        Project project;
        /** The linked build this run is for (its external project path), when IntelliJ said (M2-CDX-13). */
        String rootPath;
        final List<Diagnostic> lines = new ArrayList<>();
        final Set<String> failedTasks = new LinkedHashSet<>();
    }

    private final Map<ExternalSystemTaskId, Pending> pending = new ConcurrentHashMap<>();

    @Override
    public void onStart(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
        if (!GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())) return;
        Pending p = pending(id);   // the generation, at task start (M2-CDX-3)
        synchronized (p) {
            p.rootPath = projectPath;
        }
    }

    @Override
    public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
        if (GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())) pending(id);
    }

    private Pending pending(ExternalSystemTaskId id) {
        return pending.computeIfAbsent(id, k -> {
            Pending n = new Pending();
            // The trust generation the run started in: a result is published only within it (M2-CDX-3).
            Project project = k.findProject();
            if (project != null && !project.isDisposed()) {
                n.project = project;
                n.generation = com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker.getInstance(project).generation();
            }
            return n;
        });
    }

    @Override
    public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, @NotNull ProcessOutputType type) {
        if (!GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId())) return;
        Pending p = pending(id);
        synchronized (p) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n') {
                    if (!p.discarding) consider(p, p.partial.toString());
                    p.partial.setLength(0);
                    p.discarding = false;
                } else if (!p.discarding) {
                    if (p.partial.length() >= MAX_LINE) {
                        p.partial.setLength(0);   // an over-long line is no diagnostic: drop it through its newline
                        p.discarding = true;
                    } else {
                        p.partial.append(c);
                    }
                }
            }
        }
    }

    private static void consider(Pending p, String line) {
        if (line.contains("error[JSC0002]") || line.contains("error[JSC0003]")) {
            if (p.lines.size() < MAX_LINES) p.lines.add(new Diagnostic(line, p.currentTask));
            return;
        }
        Matcher h = TASK_HEADER.matcher(line);
        if (h.matches()) p.currentTask = h.group(1);
        Matcher m = FAILED_TASK.matcher(line);
        if (m.find()) {
            String t = m.group(1) != null ? m.group(1) : m.group(2);
            if (ABI_TASKS.contains(t.substring(t.lastIndexOf(':') + 1)) && p.failedTasks.size() < MAX_TASKS) p.failedTasks.add(t);
        }
    }

    @Override
    public void onEnd(@NotNull ExternalSystemTaskId id) {
        Pending p = pending.remove(id);
        if (p == null) return;
        List<Diagnostic> lines;
        Set<String> tasks;
        synchronized (p) {
            if (!p.discarding && !p.partial.isEmpty()) consider(p, p.partial.toString());
            lines = List.copyOf(p.lines);
            tasks = Set.copyOf(p.failedTasks);
        }
        if (lines.isEmpty() || p.project == null) return;
        Project project = p.project;
        int generation = p.generation;
        String root = p.rootPath;
        if (!live(project, generation)) return;
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(
                () -> notifyFailures(project, lines, tasks, generation, root));
    }

    /** Trusted, open, and still in the trust generation the run started in. */
    static boolean live(Project project, int generation) {
        return !project.isDisposed() && JoustTrust.isTrusted(project)
                && com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker.getInstance(project).current(generation);
    }

    /** As {@link #notifyFailures(Project, List, Set, int)}, for lines with no task header, in the current generation. */
    public static int notifyFailures(Project project, List<String> lines, Set<String> failedTasks) {
        return notifyFailures(project, lines.stream().map(l -> new Diagnostic(l, null)).toList(), failedTasks,
                com.bloxbean.intelliada.idea.joust.tasks.JoustTaskTracker.getInstance(project).generation(), null);
    }

    /** Whether a linked module belongs to the run's build ({@code buildRoot}, a real path; null: unknown, any build). */
    static boolean sameBuild(JoustGradleTasks.Linked linked, Path buildRoot) {
        if (buildRoot == null) return true;
        try {
            return linked.rootDir().toRealPath().toString().equals(buildRoot.toString());
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * One notification per (module, contract); returns how many were shown (with or without an action). A diagnostic is
     * attributed to the module of the task whose output it was in; without a header, to the modules whose JOUST task
     * failed (M2-CDX-13). Nothing is published outside the run's trust generation.
     */
    public static int notifyFailures(Project project, List<Diagnostic> lines, Set<String> failedTasks, int generation, String rootPath) {
        if (!live(project, generation)) return 0;
        JoustProjectLayout layout = JoustProjectService.getInstance(project).layout();
        if (layout == null || layout.gradleModules().isEmpty()) return 0;
        List<JoustProjectLayout.SourceRoot> gradleRoots = layout.sourceRoots().stream().filter(r -> r.gradle() != null).toList();
        JoustGradleTasks gradle = JoustGradleTasks.getInstance();
        Set<String> seen = new HashSet<>();
        int n = 0;
        for (Diagnostic diag : lines) {
            String line = diag.line();
            JoustDiagnosticLinks.Printed printed = JoustDiagnosticLinks.printed(line);
            List<JoustAbi.Failure> failures = JoustAbi.failures(line);
            if (printed == null || failures.isEmpty()) continue;
            JoustAbi.Failure f = failures.get(0);
            List<JoustProjectLayout.SourceRoot> candidates = JoustDiagnosticLinks.rootsContaining(printed.rel(), gradleRoots, layout.base());
            Set<String> owners = new HashSet<>();
            if (diag.task() != null) owners.add(ownerOf(diag.task()));
            else failedTasks.forEach(t -> owners.add(ownerOf(t)));
            // The run's own linked build first (M2-CDX-13): its start path, resolved through IntelliJ's linked projects (a run
            // may start from a module directory), scopes the candidates before any task-path or ambiguity rule.
            Path buildRoot = rootPath == null || gradle == null ? null : gradle.linkedRootOf(project, Path.of(rootPath));
            if (rootPath != null && gradle != null && buildRoot == null && !candidates.isEmpty()) {
                // The run said where it started, but that is no linked build now: no module can be credited (M2-CDX-13).
                if (!live(project, generation)) return n;
                if (seen.add("?" + rootPath + printed.rel())) {
                    JoustProjectAction.notifyUnresolvedAbiFailure(project, f, List.of("started at " + rootPath), List.of());
                    n++;
                }
                continue;
            }
            // Unlinked modules holding the source, kept through scoping for the reason they get (M2-CDX-13).
            List<String> unlinkedAll = new ArrayList<>();
            if (gradle != null) {
                for (JoustProjectLayout.SourceRoot r : candidates) {
                    if (gradle.linked(project, r.ownerDir()) == null && (buildRoot == null || r.ownerDir().startsWith(buildRoot))) {
                        unlinkedAll.add(layout.base().relativize(r.ownerDir()).toString());
                    }
                }
            }
            if (buildRoot != null) {
                List<JoustProjectLayout.SourceRoot> inBuild = new ArrayList<>();
                List<String> unlinkedHere = new ArrayList<>();
                for (JoustProjectLayout.SourceRoot r : candidates) {
                    var linked = gradle.linked(project, r.ownerDir());
                    if (linked != null && sameBuild(linked, buildRoot)) inBuild.add(r);
                    else if (linked == null && r.ownerDir().startsWith(buildRoot)) unlinkedHere.add(layout.base().relativize(r.ownerDir()).toString());
                }
                if (owners.isEmpty() && inBuild.size() + unlinkedHere.size() > 1) {
                    // No task tells which module failed, and the build holds the source more than once (linked or not):
                    // ambiguous, named, no action (E17, M2-CDX-13).
                    if (!live(project, generation)) return n;
                    if (seen.add("?" + buildRoot + printed.rel())) {
                        List<String> names = new ArrayList<>();
                        for (JoustProjectLayout.SourceRoot r : inBuild) names.add(layout.base().relativize(r.ownerDir()).toString());
                        names.addAll(unlinkedHere);
                        JoustProjectAction.notifyAmbiguousAbiFailure(project, f, names.stream().map(x -> x.isEmpty() ? "." : x).toList());
                        n++;
                    }
                    continue;
                }
                if (inBuild.isEmpty() && !candidates.isEmpty()) {
                    if (!live(project, generation)) return n;   // published only within the run's generation (M2-CDX-2)
                    if (seen.add("!" + buildRoot + printed.rel())) {
                        JoustProjectAction.notifyUnresolvedAbiFailure(project, f, List.of(owners.isEmpty()
                                ? "at " + layout.base().relativize(buildRoot) : String.join(", ", new java.util.TreeSet<>(owners))), unlinkedHere);
                        n++;
                    }
                    continue;
                }
                candidates = inBuild;
            }
            // When the failing task is known, only its module may get the notification, even for a single candidate;
            // a source no longer there names the failing module without an action (M2-CDX-13).
            if (gradle != null && !owners.isEmpty()) {
                List<JoustProjectLayout.SourceRoot> failed = new ArrayList<>();
                List<String> unlinked = new ArrayList<>(unlinkedAll);
                for (JoustProjectLayout.SourceRoot r : candidates) {
                    var linked = gradle.linked(project, r.ownerDir());
                    if (linked != null && owners.contains(linked.gradlePath()) && sameBuild(linked, buildRoot)) failed.add(r);
                }
                if (failed.isEmpty()) {
                    if (!live(project, generation)) return n;
                    if (seen.add("!" + owners + printed.rel())) {
                        JoustProjectAction.notifyUnresolvedAbiFailure(project, f, List.copyOf(new java.util.TreeSet<>(owners)), unlinked);
                        n++;
                    }
                    continue;
                }
                candidates = failed;
            }
            if (candidates.isEmpty()) continue;
            if (candidates.size() > 1) {
                if (!live(project, generation)) return n;
                if (seen.add("?" + printed.rel())) {
                    List<String> names = candidates.stream().map(r -> layout.base().relativize(r.ownerDir()).toString()).toList();
                    JoustProjectAction.notifyAmbiguousAbiFailure(project, f, names.stream().map(s -> s.isEmpty() ? "." : s).toList());
                    n++;
                }
                continue;
            }
            Path jsc = JoustDiagnosticLinks.resolveIn(printed.rel(), candidates.get(0), layout.base());
            VirtualFile vf = jsc == null ? null : LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jsc);
            JoustProjectAction.Ctx ctx = JoustProjectAction.context(project, vf);
            if (ctx == null || ctx.gradle() == null || !ctx.gradle().dir().equals(candidates.get(0).ownerDir())) continue;
            if (!live(project, generation)) return n;
            if (seen.add(ctx.dir() + "\0" + f.contract())) {
                JoustProjectAction.notifyAbiFailure(ctx, f);
                n++;
            }
        }
        return n;
    }

    /** The project path owning a task path: {@code :a:b:generateJsc} is {@code :a:b}; {@code :generateJsc} is {@code :}. */
    static String ownerOf(String taskPath) {
        int i = taskPath.lastIndexOf(':');
        return i <= 0 ? ":" : taskPath.substring(0, i);
    }
}
