package com.bloxbean.intelliada.idea.joust.run;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/** ADR-005 D15: the JOUST Run tool window (registered only with IntelliJ's Gradle plugin, which the host launch needs). */
public final class JoustRunToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JoustRunPanel panel = new JoustRunPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel.component(), "", false);
        Disposer.register(content, panel);
        toolWindow.getContentManager().addContent(content);
    }
}
