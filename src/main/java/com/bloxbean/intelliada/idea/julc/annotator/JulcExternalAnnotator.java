package com.bloxbean.intelliada.idea.julc.annotator;

import com.bloxbean.intelliada.idea.julc.annotator.fix.ReplaceNullWithOptionalFix;
import com.bloxbean.intelliada.idea.julc.annotator.validate.JulcApiValidator;
import com.bloxbean.intelliada.idea.julc.annotator.validate.JulcCompilerBridge;
import com.bloxbean.intelliada.idea.julc.annotator.validate.JulcDiagnostic;
import com.bloxbean.intelliada.idea.julc.annotator.validate.JulcSubsetValidator;
import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * External annotator that provides real-time inline error highlighting for julc validators.
 *
 * Two validation layers:
 * 1. Subset validation — flags unsupported Java constructs (try/catch, null, arrays, etc.)
 * 2. API validation — flags imports of blocked java.* packages (java.io, java.net, etc.)
 *
 * Only activates for .java files in julc projects that contain validator annotations.
 * Uses JavaParser for AST analysis — runs in-process for instant feedback.
 */
public class JulcExternalAnnotator extends ExternalAnnotator<JulcExternalAnnotator.Input, List<JulcDiagnostic>> {

    private static final Logger LOG = Logger.getInstance(JulcExternalAnnotator.class);

    public static class Input {
        final String text;
        final String fileName;

        Input(String text, String fileName) {
            this.text = text;
            this.fileName = fileName;
        }
    }

    @Override
    public @Nullable Input collectInformation(@NotNull PsiFile file) {
        // Only .java files
        if (file.getVirtualFile() == null || !"java".equalsIgnoreCase(file.getVirtualFile().getExtension())) {
            return null;
        }

        // Only julc projects
        JulcTomlService tomlService = JulcTomlService.getInstance(file.getProject());
        if (tomlService == null || !tomlService.isJulcProject()) {
            return null;
        }

        String text = file.getText();
        if (text == null || text.isBlank()) {
            return null;
        }

        // Only files with julc validator/library annotations
        if (!containsJulcAnnotation(text)) {
            return null;
        }

        return new Input(text, file.getVirtualFile().getName());
    }

    @Override
    public @Nullable List<JulcDiagnostic> doAnnotate(Input input) {
        if (input == null) return null;

        try {
            List<JulcDiagnostic> diagnostics = new ArrayList<>();

            // Try real julc-compiler via runtime classloader (JBR 25+ only)
            if (JulcCompilerBridge.isAvailable()) {
                diagnostics.addAll(JulcCompilerBridge.validate(input.text));
            } else {
                // Fallback: local JavaParser-based subset validator
                CompilationUnit cu = StaticJavaParser.parse(input.text);
                JulcSubsetValidator subsetValidator = new JulcSubsetValidator();
                diagnostics.addAll(subsetValidator.validate(cu));
            }

            // API allowlist validation (always runs — not in julc-compiler)
            CompilationUnit cu2 = StaticJavaParser.parse(input.text);
            JulcApiValidator apiValidator = new JulcApiValidator();
            diagnostics.addAll(apiValidator.validate(cu2));

            return diagnostics;
        } catch (Exception e) {
            // Parse errors are expected while user is typing
            LOG.debug("julc annotator parse error (normal during editing): " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void apply(@NotNull PsiFile file, List<JulcDiagnostic> diagnostics, @NotNull AnnotationHolder holder) {
        if (diagnostics == null || diagnostics.isEmpty()) return;

        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return;

        for (JulcDiagnostic diag : diagnostics) {
            int line = diag.line() - 1; // JavaParser uses 1-based, IntelliJ uses 0-based
            if (line < 0 || line >= document.getLineCount()) continue;

            int lineStart = document.getLineStartOffset(line);
            int lineEnd = document.getLineEndOffset(line);

            // Narrow range using column
            int col = Math.max(0, diag.column() - 1);
            int rangeStart = Math.min(lineStart + col, lineEnd);

            TextRange range = new TextRange(rangeStart, lineEnd);

            HighlightSeverity severity;
            switch (diag.level()) {
                case ERROR -> severity = HighlightSeverity.ERROR;
                case WARNING -> severity = HighlightSeverity.WARNING;
                default -> severity = HighlightSeverity.WEAK_WARNING;
            }

            String tooltip = diag.message();
            if (diag.hasSuggestion()) {
                tooltip += "\n\nSuggestion: " + diag.suggestion();
            }

            var builder = holder.newAnnotation(severity, diag.message())
                    .range(range)
                    .tooltip(tooltip);

            // Attach quick fixes for specific diagnostics
            if (diag.message().contains("null is not supported")) {
                builder = builder.withFix(new ReplaceNullWithOptionalFix(range));
            }

            builder.create();
        }
    }

    private static boolean containsJulcAnnotation(String text) {
        return text.contains("@Validator")
                || text.contains("@SpendingValidator")
                || text.contains("@MintingValidator")
                || text.contains("@MultiValidator")
                || text.contains("@WithdrawValidator")
                || text.contains("@CertifyingValidator")
                || text.contains("@VotingValidator")
                || text.contains("@ProposingValidator")
                || text.contains("@OnchainLibrary");
    }
}
