package com.bloxbean.intelliada.idea.julc.editor;

import com.bloxbean.intelliada.idea.julc.repl.JulcReplPanel;
import com.bloxbean.intelliada.idea.julc.trace.JulcExecutionTracePanel;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Single julc tool window with tabs:
 * - Trace: compile + evaluate + budget + traces
 * - UPLC: generated UPLC preview
 * - REPL: interactive julc REPL
 */
public class JulcToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ContentFactory cf = ContentFactory.getInstance();

        // Trace tab (default)
        JulcExecutionTracePanel tracePanel = new JulcExecutionTracePanel(project);
        Content traceContent = cf.createContent(tracePanel.getMainPanel(), "Trace", false);
        toolWindow.getContentManager().addContent(traceContent);

        // UPLC tab
        JulcUplcPreviewPanel uplcPanel = new JulcUplcPreviewPanel(project);
        Content uplcContent = cf.createContent(uplcPanel.getMainPanel(), "UPLC", false);
        toolWindow.getContentManager().addContent(uplcContent);

        // REPL tab
        JulcReplPanel replPanel = new JulcReplPanel(project);
        Content replContent = cf.createContent(replPanel.getMainPanel(), "REPL", false);
        toolWindow.getContentManager().addContent(replContent);
    }
}
