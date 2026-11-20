package com.bloxbean.intelliada.idea.joust.lsp;

import com.bloxbean.intelliada.idea.joust.core.JoustConstants;
import com.bloxbean.intelliada.idea.joust.settings.JoustToolsState;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/** ADR-004 D3: the compiler and mode of the JOUST language server, shown while a .jsc file is selected. */
public final class JoustStatusBarWidgetFactory implements StatusBarWidgetFactory {

    public static final String ID = "IntelliAdaJoustStatus";

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "JOUST Compiler and Mode";
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new Widget(project);
    }

    static final class Widget extends EditorBasedWidget implements StatusBarWidget.TextPresentation {
        private final Project project;

        Widget(Project project) {
            super(project);
            this.project = project;
        }

        private Project project() {
            return project;
        }

        @Override
        public @NotNull String ID() {
            return ID;
        }

        @Override
        public @Nullable WidgetPresentation getPresentation() {
            return this;
        }

        @Override
        public @NotNull String getText() {
            VirtualFile f = getSelectedFile();
            if (f == null || !JoustConstants.isJscName(f.getName())) return "";
            // E22 (UP-DES-6): the same order everywhere: an unsupported LSP4IJ first, then the configuration.
            if (JoustLsp4jCompat.problem() != null) return "JOUST: LSP4IJ " + JoustLsp4jCompat.result().version() + " unsupported";
            if (!JoustToolsState.getInstance().looksConfigured()) return "JOUST: not configured";
            if (JoustServerControl.status(project()) != com.redhat.devtools.lsp4ij.ServerStatus.started) return "JOUST: not running";
            String uri = com.redhat.devtools.lsp4ij.LSPIJUtils.toUriAsString(f);
            JoustStatusCache cache = JoustStatusCache.getInstance(project());
            var declared = f.isInLocalFileSystem() ? cache.declaredFor(uri, f.toNioPath()) : null;
            JoustStatusCache.Status s = cache.forUri(uri);
            if (s == null) return "JOUST: advisory" + (declared == null ? "" : "; " + declared.describe());
            return s.summary(declared);
        }

        @Override
        public float getAlignment() {
            return Component.CENTER_ALIGNMENT;
        }

        @Override
        public @Nullable String getTooltipText() {
            String problem = JoustLsp4jCompat.problem();
            if (problem != null) return com.bloxbean.intelliada.idea.joust.core.PlainText.literal(problem, 600);
            return "The .jsc language server runs in advisory mode: its own bundled compiler, no project code (ADR-004 D5)";
        }

        @Override
        public void install(@NotNull StatusBar statusBar) {
            super.install(statusBar);
            project().getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
                    new FileEditorManagerListener() {
                        @Override
                        public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                            statusBar.updateWidget(ID);
                        }
                    });
        }
    }
}
