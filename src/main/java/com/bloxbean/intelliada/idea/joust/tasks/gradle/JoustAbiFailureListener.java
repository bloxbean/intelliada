package com.bloxbean.intelliada.idea.joust.tasks.gradle;

import com.bloxbean.intelliada.idea.joust.abi.JoustAbi;
import com.bloxbean.intelliada.idea.joust.project.JoustDiagnosticLinks;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectLayout;
import com.bloxbean.intelliada.idea.joust.project.JoustProjectService;
import com.bloxbean.intelliada.idea.joust.project.actions.JoustProjectAction;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ADR-004 D8 step 1 for every Gradle run, including ones the user starts from the Gradle tool window: a JSC0002 or
 * JSC0003 failure in a task's output becomes one notification per contract, naming the contract and its owning module,
 * with Review ABI change…. Only the matching lines are kept (bounded); nothing runs until the user chooses the action,
 * which checks trust again.
 */
public final class JoustAbiFailureListener implements ExternalSystemTaskNotificationListener {

    static final int MAX_LINES = 64;
    static final int MAX_PARTIAL = 8 * 1024;

    private static final class Pending {
        final StringBuilder partial = new StringBuilder();
        final List<String> lines = new ArrayList<>();
    }

    private final Map<ExternalSystemTaskId, Pending> pending = new ConcurrentHashMap<>();

    @Override
    public void onTaskOutput(ExternalSystemTaskId id, String text, ProcessOutputType type) {
        if (!GradleConstants.SYSTEM_ID.equals(id.getProjectSystemId()) || text == null) return;
        Pending p = pending.computeIfAbsent(id, k -> new Pending());
        synchronized (p) {
            p.partial.append(text);
            int nl;
            while ((nl = p.partial.indexOf("\n")) >= 0) {
                String line = p.partial.substring(0, nl);
                p.partial.delete(0, nl + 1);
                if (p.lines.size() < MAX_LINES && (line.contains("error[JSC0002]") || line.contains("error[JSC0003]"))) p.lines.add(line);
            }
            if (p.partial.length() > MAX_PARTIAL) p.partial.setLength(0);   // an over-long line is no diagnostic
        }
    }

    @Override
    public void onEnd(ExternalSystemTaskId id) {
        Pending p = pending.remove(id);
        if (p == null) return;
        List<String> lines;
        synchronized (p) {
            if (!p.partial.isEmpty() && p.lines.size() < MAX_LINES) p.lines.add(p.partial.toString());
            lines = List.copyOf(p.lines);
        }
        if (lines.isEmpty()) return;
        Project project = id.findProject();
        if (project == null || project.isDisposed()) return;
        notifyFailures(project, lines);
    }

    /** One notification per (module, contract) for the failure lines that resolve inside a JOUST module. */
    public static int notifyFailures(Project project, List<String> lines) {
        JoustProjectLayout layout = JoustProjectService.getInstance(project).layout();
        if (layout == null || !layout.isJoust()) return 0;
        Set<String> seen = new HashSet<>();
        int n = 0;
        for (String line : lines) {
            JoustDiagnosticLinks.Link link = JoustDiagnosticLinks.find(line, layout);
            if (link == null) continue;
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(link.target());
            JoustProjectAction.Ctx ctx = JoustProjectAction.context(project, vf);
            if (ctx == null) continue;
            for (JoustAbi.Failure f : JoustAbi.failures(line)) {
                if (seen.add(ctx.moduleName() + "\0" + f.contract())) {
                    JoustProjectAction.notifyAbiFailure(ctx, f);
                    n++;
                }
            }
        }
        return n;
    }
}
