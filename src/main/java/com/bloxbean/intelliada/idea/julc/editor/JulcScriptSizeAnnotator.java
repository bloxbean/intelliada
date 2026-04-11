package com.bloxbean.intelliada.idea.julc.editor;

import com.bloxbean.intelliada.idea.julc.annotator.validate.JulcVmBridge;
import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows compiled script size next to @Validator classes.
 * Compiles the validator in the background and displays size with color coding:
 * - Green: < 8KB
 * - Yellow: 8-14KB
 * - Red: > 14KB (approaching 16KB limit)
 */
public class JulcScriptSizeAnnotator implements Annotator {
    private static final Logger LOG = Logger.getInstance(JulcScriptSizeAnnotator.class);

    private static final Set<String> VALIDATOR_ANNOTATIONS = Set.of(
            "Validator", "SpendingValidator", "MintingValidator", "MultiValidator",
            "WithdrawValidator", "CertifyingValidator", "VotingValidator", "ProposingValidator"
    );

    // Cache: file path -> last compile info (avoid recompiling on every keystroke)
    private static final Map<String, CachedSize> sizeCache = new ConcurrentHashMap<>();

    private record CachedSize(long timestamp, int sizeBytes, boolean hasErrors, String errorMsg) {}

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiIdentifier)) return;
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiClass psiClass)) return;
        if (!element.equals(psiClass.getNameIdentifier())) return;

        if (!hasValidatorAnnotation(psiClass)) return;

        JulcTomlService tomlService = JulcTomlService.getInstance(element.getProject());
        if (tomlService == null || !tomlService.isJulcProject()) return;

        if (!JulcVmBridge.isCompilerAvailable()) return;

        // Get the full file text for compilation
        PsiFile file = element.getContainingFile();
        if (file == null) return;
        String source = file.getText();
        String filePath = file.getVirtualFile() != null ? file.getVirtualFile().getPath() : "";

        // Check cache (avoid recompiling if source hasn't changed recently)
        CachedSize cached = sizeCache.get(filePath);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.timestamp) < 3000) {
            applyAnnotation(element, holder, cached);
            return;
        }

        // Compile and get size
        try {
            JulcVmBridge.CompileInfo info = JulcVmBridge.compile(source);
            if (info != null) {
                CachedSize newCached;
                if (info.hasErrors) {
                    String errorMsg = info.diagnostics.isEmpty() ? "Compilation error"
                            : info.diagnostics.get(0).message();
                    newCached = new CachedSize(now, 0, true, errorMsg);
                } else {
                    newCached = new CachedSize(now, info.scriptSizeBytes, false, null);
                }
                sizeCache.put(filePath, newCached);
                applyAnnotation(element, holder, newCached);
            }
        } catch (Exception e) {
            LOG.debug("Script size compilation failed: " + e.getMessage());
        }
    }

    private void applyAnnotation(PsiElement element, AnnotationHolder holder, CachedSize info) {
        if (info.hasErrors) {
            return; // Don't show size if compilation failed — SubsetValidator handles errors
        }

        String sizeText = formatSize(info.sizeBytes);
        HighlightSeverity severity;
        String color;

        if (info.sizeBytes > 14 * 1024) {
            severity = HighlightSeverity.WARNING;
            color = "#CC0000";
        } else if (info.sizeBytes > 8 * 1024) {
            severity = HighlightSeverity.WEAK_WARNING;
            color = "#CC8800";
        } else {
            severity = HighlightSeverity.INFORMATION;
            color = "#008800";
        }

        holder.newAnnotation(severity, "julc: Script size " + sizeText)
                .tooltip("<html><b>Compiled Script Size: " + sizeText + "</b><br/>" +
                        "<span style='color:" + color + "'>" + sizeText + " / 16 KB limit</span><br/><br/>" +
                        "On-chain scripts must be under 16 KB (FLAT-encoded).<br/>" +
                        (info.sizeBytes > 14 * 1024 ? "<b>⚠️ Approaching limit — consider optimizing</b>" :
                                info.sizeBytes > 8 * 1024 ? "Script is moderately sized" :
                                        "✅ Script size is healthy") + "</html>")
                .afterEndOfLine()
                .create();
    }

    private String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        return kb < 10 ? String.format("%.1f KB", kb) : String.format("%.0f KB", kb);
    }

    private boolean hasValidatorAnnotation(PsiClass psiClass) {
        PsiModifierList mods = psiClass.getModifierList();
        if (mods == null) return false;
        for (PsiAnnotation ann : mods.getAnnotations()) {
            String name = ann.getQualifiedName();
            if (name == null) continue;
            for (String target : VALIDATOR_ANNOTATIONS) {
                if (name.endsWith(target)) return true;
            }
        }
        return false;
    }
}
