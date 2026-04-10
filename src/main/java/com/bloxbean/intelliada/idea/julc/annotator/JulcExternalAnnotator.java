package com.bloxbean.intelliada.idea.julc.annotator;

import com.bloxbean.intelliada.idea.julc.annotator.fix.ReplaceNullWithOptionalFix;
import com.bloxbean.intelliada.idea.julc.annotator.validate.JulcCompilerBridge;
import com.bloxbean.intelliada.idea.julc.annotator.validate.JulcDiagnostic;
import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
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

import java.util.Collections;
import java.util.List;

/**
 * External annotator that runs julc's SubsetValidator via the runtime classloader bridge.
 *
 * Validates language subset: try/catch, null, arrays, this/super, float/double,
 * method references, functional interface calls, unreachable code, etc.
 *
 * Uses the real julc-compiler SubsetValidator loaded from the bundled shadow JAR
 * (julc-compiler-all.jar) via JulcCompilerBridge.
 *
 * Only activates for .java files in julc projects containing validator annotations.
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
        if (file.getVirtualFile() == null || !"java".equalsIgnoreCase(file.getVirtualFile().getExtension())) {
            return null;
        }

        JulcTomlService tomlService = JulcTomlService.getInstance(file.getProject());
        if (tomlService == null || !tomlService.isJulcProject()) {
            return null;
        }

        String text = file.getText();
        if (text == null || text.isBlank()) {
            return null;
        }

        if (!containsJulcAnnotation(text)) {
            return null;
        }

        return new Input(text, file.getVirtualFile().getName());
    }

    @Override
    public @Nullable List<JulcDiagnostic> doAnnotate(Input input) {
        if (input == null) return null;

        // Use real julc SubsetValidator via runtime classloader bridge
        if (JulcCompilerBridge.isAvailable()) {
            List<JulcDiagnostic> diagnostics = JulcCompilerBridge.validate(input.text);
            if (!diagnostics.isEmpty()) {
                return diagnostics;
            }
        } else {
            LOG.debug("julc-compiler bridge not available — subset validation skipped");
        }

        return Collections.emptyList();
    }

    @Override
    public void apply(@NotNull PsiFile file, List<JulcDiagnostic> diagnostics, @NotNull AnnotationHolder holder) {
        if (diagnostics == null || diagnostics.isEmpty()) return;

        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return;

        for (JulcDiagnostic diag : diagnostics) {
            int line = diag.line() - 1;
            if (line < 0 || line >= document.getLineCount()) continue;

            int lineStart = document.getLineStartOffset(line);
            int lineEnd = document.getLineEndOffset(line);
            int col = Math.max(0, diag.column() - 1);
            int rangeStart = Math.min(lineStart + col, lineEnd);

            TextRange range = new TextRange(rangeStart, lineEnd);

            HighlightSeverity severity = switch (diag.level()) {
                case ERROR -> HighlightSeverity.ERROR;
                case WARNING -> HighlightSeverity.WARNING;
                default -> HighlightSeverity.WEAK_WARNING;
            };

            String tooltip = diag.message();
            if (diag.hasSuggestion()) {
                tooltip += "\n\nSuggestion: " + diag.suggestion();
            }

            var builder = holder.newAnnotation(severity, diag.message())
                    .range(range)
                    .tooltip(tooltip);

            if (diag.message().contains("null is not supported")) {
                builder = builder.withFix(new ReplaceNullWithOptionalFix(range));
            }

            builder.create();
        }
    }

    private static boolean containsJulcAnnotation(String text) {
        return text.contains("@Validator") || text.contains("@SpendingValidator")
                || text.contains("@MintingValidator") || text.contains("@MultiValidator")
                || text.contains("@WithdrawValidator") || text.contains("@CertifyingValidator")
                || text.contains("@VotingValidator") || text.contains("@ProposingValidator")
                || text.contains("@OnchainLibrary");
    }
}
