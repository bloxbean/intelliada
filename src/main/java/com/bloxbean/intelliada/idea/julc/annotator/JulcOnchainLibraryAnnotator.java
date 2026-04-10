package com.bloxbean.intelliada.idea.julc.annotator;

import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * PSI-based annotator that checks cross-file @OnchainLibrary requirements in julc validators.
 *
 * When a validator calls a method on another class, that class must be:
 * 1. Annotated with @OnchainLibrary (project source)
 * 2. From a julc library JAR (has META-INF/plutus-sources/ inside)
 * 3. A julc core/stdlib/ledger type
 * 4. An allowed Java type (BigInteger, String, etc.)
 *
 * This annotator uses IntelliJ PSI for cross-file resolution.
 * Only activates for Java files in julc projects containing validator annotations.
 */
public class JulcOnchainLibraryAnnotator implements Annotator {
    private static final Logger LOG = Logger.getInstance(JulcOnchainLibraryAnnotator.class);

    private static final String ONCHAIN_LIBRARY_FQN = "com.bloxbean.cardano.julc.stdlib.annotation.OnchainLibrary";

    private static final Set<String> JULC_ANNOTATION_NAMES = Set.of(
            "Validator", "SpendingValidator", "MintingValidator", "MultiValidator",
            "WithdrawValidator", "CertifyingValidator", "VotingValidator",
            "ProposingValidator", "OnchainLibrary"
    );

    private static final Set<String> ALLOWED_JAVA_PREFIXES = Set.of(
            "java.math.", "java.lang.String", "java.lang.Boolean",
            "java.lang.Integer", "java.lang.Long", "java.lang.Byte",
            "java.lang.Object", "java.lang.Comparable", "java.lang.Record",
            "java.util.Optional", "java.util.List", "java.util.Map"
    );

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // Only process method call expressions
        if (!(element instanceof PsiMethodCallExpression call)) return;

        // Only in julc projects
        Project project = element.getProject();
        JulcTomlService tomlService = JulcTomlService.getInstance(project);
        if (tomlService == null || !tomlService.isJulcProject()) return;

        // Only in files that contain julc validator annotations
        if (!isInJulcValidatorFile(element)) return;

        PsiMethod method = call.resolveMethod();
        if (method == null) return;

        PsiClass targetClass = method.getContainingClass();
        if (targetClass == null) return;

        String qualifiedName = targetClass.getQualifiedName();
        if (qualifiedName == null) return;

        // Always allow julc types
        if (qualifiedName.startsWith("com.bloxbean.cardano.julc.")) return;

        // Always allow permitted Java types
        if (isAllowedJavaType(qualifiedName)) return;

        // Same class references are fine
        PsiClass thisClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (targetClass.equals(thisClass)) return;

        // Check based on source location
        if (isProjectSource(targetClass, project)) {
            // Project source class — must have @OnchainLibrary
            if (!hasOnchainLibraryAnnotation(targetClass)) {
                holder.newAnnotation(HighlightSeverity.ERROR,
                                "julc: '" + targetClass.getName() + "' needs @OnchainLibrary annotation for on-chain use")
                        .tooltip("<html><b>julc compilation will fail</b><br/><br/>" +
                                "Class <code>" + qualifiedName + "</code> is called from a validator but is not annotated with <code>@OnchainLibrary</code>.<br/>" +
                                "julc will not compile this class for on-chain use.<br/><br/>" +
                                "<b>Fix:</b> Add <code>@OnchainLibrary</code> annotation to <code>" + targetClass.getName() + "</code></html>")
                        .create();
            }
        } else if (isFromJar(targetClass)) {
            // JAR class — check if the JAR is a valid julc library
            if (!isJulcLibraryJar(targetClass)) {
                holder.newAnnotation(HighlightSeverity.WARNING,
                                "julc: '" + targetClass.getName() + "' is from a non-julc library and may not be available on-chain")
                        .tooltip("<html><b>Possibly unavailable on-chain</b><br/><br/>" +
                                "Class <code>" + qualifiedName + "</code> comes from a JAR that doesn't contain julc on-chain sources " +
                                "(no <code>META-INF/plutus-sources/</code>).<br/>It may not be available in the Plutus VM.</html>")
                        .create();
            }
        }
    }

    private boolean isInJulcValidatorFile(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) return false;

        // Quick text check first (fast path)
        String text = file.getText();
        if (text == null) return false;

        for (String ann : JULC_ANNOTATION_NAMES) {
            if (text.contains("@" + ann)) return true;
        }
        return false;
    }

    private boolean isProjectSource(PsiClass psiClass, Project project) {
        VirtualFile vFile = getVirtualFile(psiClass);
        if (vFile == null) return false;

        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
        return fileIndex.isInSourceContent(vFile);
    }

    private boolean isFromJar(PsiClass psiClass) {
        VirtualFile vFile = getVirtualFile(psiClass);
        if (vFile == null) return false;

        return vFile.getFileSystem() instanceof JarFileSystem
                || vFile.getPath().contains(".jar!");
    }

    private boolean hasOnchainLibraryAnnotation(PsiClass psiClass) {
        PsiAnnotation[] annotations = psiClass.getAnnotations();
        for (PsiAnnotation ann : annotations) {
            String name = ann.getQualifiedName();
            if (ONCHAIN_LIBRARY_FQN.equals(name)) return true;
            // Also check simple name (in case import is present)
            if (name != null && name.endsWith("OnchainLibrary")) return true;
        }
        return false;
    }

    private boolean isJulcLibraryJar(PsiClass psiClass) {
        VirtualFile vFile = getVirtualFile(psiClass);
        if (vFile == null) return false;

        // Navigate to the JAR root
        VirtualFile jarRoot = null;

        if (vFile.getFileSystem() instanceof JarFileSystem jarFs) {
            jarRoot = jarFs.getLocalByEntry(vFile);
            if (jarRoot != null) {
                jarRoot = jarFs.getJarRootForLocalFile(jarRoot);
            }
        }

        // Alternative: parse the path to find the jar root
        if (jarRoot == null) {
            String path = vFile.getPath();
            int jarSep = path.indexOf(".jar!");
            if (jarSep > 0) {
                String jarPath = path.substring(0, jarSep + 4); // include .jar
                VirtualFile localFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                        .findFileByPath(jarPath);
                if (localFile != null) {
                    jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(localFile);
                }
            }
        }

        if (jarRoot == null) return false;

        // Check for plutus-sources index
        return jarRoot.findFileByRelativePath("META-INF/plutus-sources/index.txt") != null;
    }

    private boolean isAllowedJavaType(String qualifiedName) {
        for (String prefix : ALLOWED_JAVA_PREFIXES) {
            if (qualifiedName.startsWith(prefix) || qualifiedName.equals(prefix)) return true;
        }
        return false;
    }

    private VirtualFile getVirtualFile(PsiClass psiClass) {
        PsiFile containingFile = psiClass.getContainingFile();
        return containingFile != null ? containingFile.getVirtualFile() : null;
    }
}
