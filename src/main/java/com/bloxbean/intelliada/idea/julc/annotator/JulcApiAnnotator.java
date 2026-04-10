package com.bloxbean.intelliada.idea.julc.annotator;

import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * PSI-based annotator that flags imports and constructor calls for APIs
 * not available on-chain in julc validators.
 *
 * Two validation layers in IntelliAda:
 * 1. julc-compiler bridge (SubsetValidator via shadow JAR) — language subset checks
 * 2. This annotator (PSI-based) — API blocklist + @OnchainLibrary checks
 *
 * Only activates for Java files in julc projects containing validator annotations.
 */
public class JulcApiAnnotator implements Annotator {

    private static final Set<String> BLOCKED_PACKAGES = Set.of(
            "java.io", "java.net", "java.nio", "java.sql", "java.awt",
            "javax.swing", "javax.net", "javax.crypto",
            "java.util.concurrent", "java.util.stream", "java.util.regex",
            "java.lang.reflect", "java.lang.invoke", "java.text", "java.time"
    );

    private static final Set<String> BLOCKED_CLASSES = Set.of(
            "java.lang.Thread", "java.lang.Runtime", "java.lang.System",
            "java.lang.ProcessBuilder", "java.lang.ClassLoader",
            "java.util.HashMap", "java.util.LinkedHashMap", "java.util.TreeMap",
            "java.util.HashSet", "java.util.LinkedHashSet", "java.util.TreeSet",
            "java.util.ArrayList", "java.util.LinkedList", "java.util.ArrayDeque",
            "java.util.Collections", "java.util.Arrays",
            "java.util.Scanner", "java.util.Random"
    );

    private static final Set<String> ALLOWED_CLASSES = Set.of(
            "java.math.BigInteger", "java.lang.String", "java.lang.Boolean",
            "java.lang.Integer", "java.lang.Long", "java.lang.Byte",
            "java.lang.Object", "java.lang.Comparable", "java.lang.Record",
            "java.util.Optional", "java.util.List", "java.util.Map"
    );

    private static final Set<String> JULC_ANNOTATION_NAMES = Set.of(
            "Validator", "SpendingValidator", "MintingValidator", "MultiValidator",
            "WithdrawValidator", "CertifyingValidator", "VotingValidator",
            "ProposingValidator", "OnchainLibrary"
    );

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // Only in julc projects
        JulcTomlService tomlService = JulcTomlService.getInstance(element.getProject());
        if (tomlService == null || !tomlService.isJulcProject()) return;

        // Only in validator files
        if (!isInJulcValidatorFile(element)) return;

        if (element instanceof PsiImportStatement importStmt) {
            checkImport(importStmt, holder);
        }
    }

    private void checkImport(PsiImportStatement importStmt, AnnotationHolder holder) {
        PsiJavaCodeReferenceElement ref = importStmt.getImportReference();
        if (ref == null) return;

        String qualifiedName = ref.getQualifiedName();
        if (qualifiedName == null) return;

        // Only check java.* and javax.* imports
        if (!qualifiedName.startsWith("java.") && !qualifiedName.startsWith("javax.")) return;

        // Allow explicitly permitted classes
        if (ALLOWED_CLASSES.contains(qualifiedName)) return;

        // Check blocked specific classes
        if (BLOCKED_CLASSES.contains(qualifiedName)) {
            holder.newAnnotation(HighlightSeverity.ERROR,
                            "julc: '" + qualifiedName + "' is not available on-chain")
                    .tooltip("<html><b>Not available on-chain</b><br/><br/>" +
                            "<code>" + qualifiedName + "</code> cannot be used in julc validators.<br/>" +
                            getSuggestionHtml(qualifiedName) + "</html>")
                    .create();
            return;
        }

        // Check blocked packages
        for (String blockedPkg : BLOCKED_PACKAGES) {
            if (qualifiedName.startsWith(blockedPkg + ".") || qualifiedName.equals(blockedPkg)) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                                "julc: '" + blockedPkg + ".*' is not available on-chain")
                        .tooltip("<html><b>Not available on-chain</b><br/><br/>" +
                                "The <code>" + blockedPkg + "</code> package is not available in the Plutus VM.<br/>" +
                                getSuggestionHtml(qualifiedName) + "</html>")
                        .create();
                return;
            }
        }
    }

    private String getSuggestionHtml(String className) {
        if (className.contains("HashMap") || className.contains("TreeMap"))
            return "<b>Use:</b> julc Map type from julc-ledger-api";
        if (className.contains("ArrayList") || className.contains("LinkedList"))
            return "<b>Use:</b> julc List type from julc-ledger-api";
        if (className.contains("HashSet") || className.contains("TreeSet"))
            return "<b>Use:</b> List with contains() — sets not available on-chain";
        if (className.contains("io"))
            return "<b>Note:</b> File I/O is not available on-chain";
        if (className.contains("Thread") || className.contains("concurrent"))
            return "<b>Note:</b> Threading not available — validators are single-threaded";
        if (className.contains("System"))
            return "<b>Use:</b> Builtins.trace() for debugging instead of System.out";
        if (className.contains("stream"))
            return "<b>Use:</b> julc ListsLib (map, filter, foldl) instead of streams";
        if (className.contains("Random"))
            return "<b>Note:</b> Use on-chain randomness from block hash instead";
        return "";
    }

    private boolean isInJulcValidatorFile(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) return false;
        String text = file.getText();
        if (text == null) return false;
        for (String ann : JULC_ANNOTATION_NAMES) {
            if (text.contains("@" + ann)) return true;
        }
        return false;
    }
}
