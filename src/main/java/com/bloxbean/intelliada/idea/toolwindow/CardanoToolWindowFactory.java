package com.bloxbean.intelliada.idea.toolwindow;

import com.bloxbean.intelliada.idea.toolwindow.ui.CardanoExplorer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;

public class CardanoToolWindowFactory implements ToolWindowFactory, DumbAware {
    public final static String CARDANO_WINDOW_ID = "Cardano";

    @Override
    public boolean isApplicable(Project project) {
       return true;
    }

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        CardanoExplorer explorer = new CardanoExplorer(project);
        ContentManager contentManager = toolWindow.getContentManager();
        Content content = contentManager.getFactory().createContent(explorer, null, false);
        contentManager.addContent(content);
        content.setDisposer(explorer);
    }

    @Override
    public void init(ToolWindow toolWindow) {

    }

    @Override
    public boolean shouldBeAvailable(Project project) {
        return true;
    }
}
