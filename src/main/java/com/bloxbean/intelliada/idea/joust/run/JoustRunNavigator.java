package com.bloxbean.intelliada.idea.joust.run;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;

/**
 * ADR-005 D15 "Entry points" (M3.4a): a gutter icon opens the JOUST Run tool window at its contract. Icons only open the
 * panel; nothing runs without D1 (ADR-004) and the panel's own Start.
 */
@Service(Service.Level.PROJECT)
public final class JoustRunNavigator {

    static final String TOOL_WINDOW = "JOUST Run";

    private final Project project;
    private volatile WeakReference<JoustRunPanel> panel = new WeakReference<>(null);
    /** A contract asked for before the panel existed: selected when it attaches. */
    private volatile @Nullable String pending;

    public JoustRunNavigator(Project project) {
        this.project = project;
    }

    public static JoustRunNavigator getInstance(Project project) {
        return project.getService(JoustRunNavigator.class);
    }

    void attach(JoustRunPanel p) {
        panel = new WeakReference<>(p);
        String c = pending;
        pending = null;
        if (c != null) p.select(c);
    }

    /** Opens the run panel at {@code contract} ({@code <package>.<Contract>}); on the EDT. */
    public void open(String contract) {
        ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW);
        if (tw == null) return;   // the tool window comes with IntelliJ's Gradle plugin
        JoustRunPanel p = panel.get();
        if (p == null) pending = contract;
        tw.activate(() -> {
            JoustRunPanel now = panel.get();
            if (now != null) now.select(contract);
        });
    }
}
