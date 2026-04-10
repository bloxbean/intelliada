package com.bloxbean.intelliada.idea.julc.editor;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class JulcUplcPreviewToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JulcUplcPreviewPanel panel = new JulcUplcPreviewPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel.getMainPanel(), "UPLC", false);
        toolWindow.getContentManager().addContent(content);
    }
}
