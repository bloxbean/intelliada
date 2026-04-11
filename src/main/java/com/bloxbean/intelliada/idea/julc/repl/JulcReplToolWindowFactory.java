package com.bloxbean.intelliada.idea.julc.repl;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class JulcReplToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JulcReplPanel replPanel = new JulcReplPanel(project);
        Content content = ContentFactory.getInstance().createContent(
                replPanel.getMainPanel(), "REPL", false);
        toolWindow.getContentManager().addContent(content);
    }
}
