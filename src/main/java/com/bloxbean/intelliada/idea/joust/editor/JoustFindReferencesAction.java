package com.bloxbean.intelliada.idea.joust.editor;

import com.bloxbean.intelliada.idea.joust.core.PlainText;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ADR-004 D3a item 7: Find References for .jsc, by IntelliAda. Server locations are confined to real paths inside the
 * project before the list is shown; the list renders plain text only (no HTML).
 */
public final class JoustFindReferencesAction extends AnAction {

    public record Hit(VirtualFile file, int offset, String where, String line) {
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
        e.getPresentation().setEnabledAndVisible(e.getProject() != null && e.getData(CommonDataKeys.EDITOR) != null
                && JoustServerAccess.isJsc(vf));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || editor == null || !JoustServerAccess.isJsc(vf)) return;
        Position pos = LSPIJUtils.toPosition(editor.getCaretModel().getOffset(), editor.getDocument());
        new Task.Backgroundable(project, "Finding JOUST references", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                List<Hit> hits = findHits(project, vf, pos);
                ApplicationManager.getApplication().invokeLater(() -> show(project, editor, hits));
            }
        }.queue();
    }

    /** Asks the server for references and keeps only in-project targets (D3a item 7); off the EDT. */
    public static List<Hit> findHits(Project project, VirtualFile vf, Position pos) {
        var server = JoustServerAccess.server(project);
        if (server == null) return List.of();
        String uri = ReadAction.compute(() -> JoustServerAccess.uriOf(vf));
        var params = new ReferenceParams(new TextDocumentIdentifier(uri), pos, new ReferenceContext(true));
        var locs = JoustServerAccess.await(server.getTextDocumentService().references(params));
        List<JoustServerAccess.Target> targets = new ArrayList<>(JoustServerAccess.confine(project,
                locs == null ? List.of() : new ArrayList<>(locs)));
        return ReadAction.compute(() -> toHits(project, targets));
    }

    private static List<Hit> toHits(Project project, List<JoustServerAccess.Target> targets) {
        List<Hit> out = new ArrayList<>();
        VirtualFile base = com.intellij.openapi.project.ProjectUtil.guessProjectDir(project);
        for (JoustServerAccess.Target t : targets) {
            Document doc = FileDocumentManager.getInstance().getDocument(t.file());
            if (doc == null) continue;
            int off = t.range() == null ? 0 : LSPIJUtils.toOffset(t.range().getStart(), doc);
            int line = doc.getLineNumber(Math.min(off, doc.getTextLength()));
            String rel = base == null ? t.file().getName() : VfsUtilCore.getRelativePath(t.file(), base, '/');
            String text = doc.getText(new TextRange(doc.getLineStartOffset(line), doc.getLineEndOffset(line))).trim();
            out.add(new Hit(t.file(), off, PlainText.clean(rel == null ? t.file().getName() : rel, 200) + ":" + (line + 1),
                    PlainText.clean(text, 160)));
        }
        return out;
    }

    /** What choosing a row does: open it only through {@link JoustSafeOpen}, re-checked now. */
    public static boolean choose(Project project, Hit h) {
        return JoustSafeOpen.open(project, h.file(), h.offset(), true);
    }

    private static void show(Project project, Editor editor, List<Hit> hits) {
        if (project.isDisposed() || editor.isDisposed()) return;
        if (hits.isEmpty()) {
            JBPopupFactory.getInstance().createMessage("No references inside the project").showInBestPositionFor(editor);
            return;
        }
        JBPopupFactory.getInstance().createPopupChooserBuilder(hits)
                .setTitle("JOUST References (" + hits.size() + ")")
                .setRenderer(new ColoredListCellRenderer<Hit>() {
                    @Override
                    protected void customizeCellRenderer(@NotNull JList<? extends Hit> list, Hit h, int index, boolean sel, boolean focus) {
                        append(h.where(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                        append("  " + h.line(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    }
                })
                .setItemChosenCallback(h -> choose(project, h))
                .createPopup()
                .showInBestPositionFor(editor);
    }
}
