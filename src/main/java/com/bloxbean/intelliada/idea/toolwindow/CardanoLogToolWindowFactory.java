package com.bloxbean.intelliada.idea.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

public class CardanoLogToolWindowFactory implements ToolWindowFactory {
    public final static String CARDANO_LOG_WINDOW_ID = "Cardano Log";

    @Override
    public boolean isApplicable(@NotNull Project project) {
        return true;
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
//        TextConsoleBuilderFactory factory = TextConsoleBuilderFactory.getInstance();
//        TextConsoleBuilder builder = factory.createBuilder(project);
//        ConsoleView view = builder.getConsole();
//
//        final ContentManager contentManager = toolWindow.getContentManager();
//        Content content = contentManager
//                .getFactory()
//                .createContent(view.getComponent(), "Teal Compile", false);
//        contentManager.addContent(content);
    }

    @Override
    public void init(@NotNull ToolWindow toolWindow) {

    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

}
