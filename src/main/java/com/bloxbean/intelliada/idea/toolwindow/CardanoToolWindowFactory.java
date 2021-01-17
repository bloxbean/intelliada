package com.bloxbean.intelliada.idea.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;

public class CardanoToolWindowFactory implements ToolWindowFactory  {
    public final static String CARDANO_WINDOW_ID = "Cardano";

    @Override
    public boolean isApplicable(Project project) {
       return true;
    }

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {

    }

    @Override
    public void init(ToolWindow toolWindow) {

    }

    @Override
    public boolean shouldBeAvailable(Project project) {
        return true;
    }

}
