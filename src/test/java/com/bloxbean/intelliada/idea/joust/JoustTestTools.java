package com.bloxbean.intelliada.idea.joust;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Test prerequisites (M1-CDX-25). A test that needs an installed tool calls {@link #env}: without the tool it is skipped
 * with a visible {@code JOUST-SKIPPED} line, and with {@code JOUST_REQUIRE_TOOLS=1} (milestone verification runs) it fails.
 */
public final class JoustTestTools {
    private JoustTestTools() {
    }

    /** The variable's value, or null (skip) — or a failure when tools are required. */
    public static String env(String name, String test) {
        String v = System.getenv(name);
        if (v != null && !v.isBlank()) return v;
        if (required()) throw new AssertionError(test + " needs " + name + " (JOUST_REQUIRE_TOOLS=1)");
        System.out.println("JOUST-SKIPPED " + test + ": set " + name);
        return null;
    }

    /** The JOUST checkout ({@code -Djoust.repo} or {@code JOUST_REPO}) if usable; required runs fail otherwise (M1-CDX-25). */
    public static String repo(String test) {
        String r = System.getProperty("joust.repo");
        if (r == null || r.isBlank()) r = System.getenv("JOUST_REPO");
        boolean usable = r != null && !r.isBlank() && new java.io.File(r, ".git").exists();
        if (usable) return r;
        if (required()) throw new AssertionError(test + " needs a JOUST checkout in JOUST_REPO (JOUST_REQUIRE_TOOLS=1); got " + r);
        System.out.println("JOUST-SKIPPED " + test + ": set JOUST_REPO to a JOUST checkout");
        return null;
    }

    /** True when the run must have every tool: {@code -Djoust.requireTools} is {@code true} or {@code 1} (M1-DES-18). */
    public static boolean required() {
        String v = System.getProperty("joust.requireTools", "");
        return v.equals("1") || v.equalsIgnoreCase("true");
    }

    /** A real native executable copied as {@code node} (validation refuses scripts named node, M1-CDX-5). */
    public static Path nodeBinary(Path target) throws IOException {
        Path src = Path.of(System.getProperty("os.name").toLowerCase().contains("win") ? "C:\\Windows\\System32\\where.exe" : "/bin/ls");
        Files.createDirectories(target.getParent());
        Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
        target.toFile().setExecutable(true);
        return target;
    }

    public static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Runs the highlighting passes on the file's selected editor, as the daemon would in the IDE. LSP4IJ 0.30.1 connects
     * a document (and starts its server) when a feature first asks for it, and a headless test has no running daemon.
     */
    static void highlight(com.intellij.openapi.project.Project project, com.intellij.openapi.vfs.VirtualFile vf) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait(() -> {
            var editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor();
            var psi = com.intellij.psi.PsiManager.getInstance(project).findFile(vf);
            if (editor != null && psi != null) {
                com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl.instantiateAndRun(psi, editor, new int[0], false);
            }
        });
    }
}
