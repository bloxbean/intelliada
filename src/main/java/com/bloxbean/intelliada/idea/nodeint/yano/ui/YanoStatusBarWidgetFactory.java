package com.bloxbean.intelliada.idea.nodeint.yano.ui;

import com.bloxbean.intelliada.idea.nodeint.yano.YanoLifecycleService;
import com.bloxbean.intelliada.idea.nodeint.yano.YanoProcessManager;
import com.bloxbean.intelliada.idea.nodeint.yano.YanoStatusMonitor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;

/**
 * Status bar widget showing live Yano chain tip when running.
 * Click to open the Yano Devnet tool window.
 */
public class YanoStatusBarWidgetFactory implements StatusBarWidgetFactory {
    private static final String ID = "YanoDevnetStatus";

    @Override
    public @NotNull @NonNls String getId() {
        return ID;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Yano Devnet";
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new YanoStatusWidget(project);
    }

    private static class YanoStatusWidget implements StatusBarWidget, StatusBarWidget.TextPresentation {
        private final Project project;
        private StatusBar statusBar;
        private String text = "";

        YanoStatusWidget(Project project) {
            this.project = project;
            setupMonitor();
        }

        @Override
        public @NotNull @NonNls String ID() {
            return ID;
        }

        @Override
        public void install(@NotNull StatusBar statusBar) {
            this.statusBar = statusBar;
        }

        @Override
        public void dispose() {}

        @Override
        public @NotNull StatusBarWidget.WidgetPresentation getPresentation() {
            return this;
        }

        @Override
        public @NotNull String getText() {
            return text;
        }

        @Override
        public float getAlignment() {
            return 0;
        }

        @Override
        public @Nullable String getTooltipText() {
            return "Yano Devnet - Click to open control panel";
        }

        @Override
        public @Nullable Consumer<MouseEvent> getClickConsumer() {
            return e -> {
                ToolWindowManager twm = ToolWindowManager.getInstance(project);
                var tw = twm.getToolWindow("Yano Devnet");
                if (tw != null) {
                    tw.show();
                }
            };
        }

        private void setupMonitor() {
            YanoLifecycleService lifecycle = YanoLifecycleService.getInstance();
            if (lifecycle == null) return;

            YanoStatusMonitor monitor = lifecycle.getStatusMonitor(project);
            if (monitor == null) {
                text = "";
                return;
            }

            monitor.addStatusChangeListener(new YanoStatusMonitor.StatusChangeListener() {
                @Override
                public void onStatusChanged(YanoProcessManager.YanoStatus old, YanoProcessManager.YanoStatus newStatus) {
                    if (newStatus == YanoProcessManager.YanoStatus.RUNNING) {
                        text = "Yano: running";
                    } else if (newStatus == YanoProcessManager.YanoStatus.STARTING) {
                        text = "Yano: starting...";
                    } else {
                        text = "";
                    }
                    if (statusBar != null) statusBar.updateWidget(ID);
                }

                @Override
                public void onHealthChanged(boolean healthy) {}

                @Override
                public void onChainTipChanged(long slot, long blockNumber) {
                    text = "Yano: slot " + slot + " | block " + blockNumber;
                    if (statusBar != null) statusBar.updateWidget(ID);
                }
            });
        }
    }
}
