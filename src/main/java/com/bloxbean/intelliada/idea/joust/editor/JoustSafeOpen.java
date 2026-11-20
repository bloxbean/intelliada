package com.bloxbean.intelliada.idea.joust.editor;

import com.bloxbean.intelliada.idea.joust.core.JoustLocationPolicy;
import com.bloxbean.intelliada.idea.joust.settings.JoustForbiddenRoots;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.INativeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Path;

/**
 * ADR-004 D3a items 4 and 7, I18: the only way IntelliAda opens a file a language server named.
 *
 * <ul>
 *   <li><b>Re-checked at the moment of opening</b> (M1-CDX-2): the file's current real path must still be a regular file
 *   inside the project's current real base, by identity. A file moved or replaced by a symlink after it was listed is
 *   refused.</li>
 *   <li><b>Editor only</b> (M1-CDX-1): opened with {@link FileEditorManager#openTextEditor}, never
 *   {@code OpenFileDescriptor.navigate()}, which hands a file of a native type to its associated application. Binary and
 *   native file types are refused.</li>
 *   <li><b>Generated files are refused</b> (D3a item 4): they are not opened writable from a server's request.</li>
 * </ul>
 * Must be called on the EDT.
 */
public final class JoustSafeOpen {

    private JoustSafeOpen() {
    }

    /** Why {@code file} may not be opened, or {@code null} when it may. */
    public static String refusal(Project project, VirtualFile file) {
        if (project == null || project.isDisposed() || file == null || !file.isValid() || !file.isInLocalFileSystem()) {
            return "not a local file";
        }
        if (file.getFileType().isBinary() || file.getFileType() instanceof INativeFileType) return "not a text file";
        Path base = JoustForbiddenRoots.baseReal(project);
        Path current;
        try {
            current = file.toNioPath();
        } catch (UnsupportedOperationException e) {
            return "not a local file";
        }
        if (JoustLocationPolicy.allowedFile(current, base) == null) return "outside the project";
        if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project)) return "a generated file";
        return null;
    }

    /** Opens {@code file} at {@code offset} in a text editor if {@link #refusal} allows it; true when opened. */
    public static boolean open(Project project, VirtualFile file, int offset, boolean focus) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (refusal(project, file) != null) return false;
        return FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, Math.max(0, offset)), focus) != null;
    }

    /** As {@link #open(Project, VirtualFile, int, boolean)}, at a line and column. */
    public static boolean open(Project project, VirtualFile file, int line, int column, boolean focus) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        if (refusal(project, file) != null) return false;
        return FileEditorManager.getInstance(project).openTextEditor(
                new OpenFileDescriptor(project, file, Math.max(0, line), Math.max(0, column)), focus) != null;
    }
}
