package com.bloxbean.intelliada.idea.julc.trace;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class JulcTraceToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JulcExecutionTracePanel panel = new JulcExecutionTracePanel(project);
        Content content = ContentFactory.getInstance().createContent(panel.getMainPanel(), "Trace", false);
        toolWindow.getContentManager().addContent(content);
    }
}
