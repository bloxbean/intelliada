package com.bloxbean.intelliada.idea.aiken.lang.psi.impl;

import com.bloxbean.intelliada.idea.aiken.lang.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AikenPsiImplUtil {

    /**
     * Get the name of an identifier element
     */
    @Nullable
    public static String getName(@NotNull PsiElement element) {
        // For now, just return the text content
        return element.getText();
    }

    /**
     * Get all imports in the current file
     */
    @NotNull
    public static List<AikenImportStatement> getImports(@NotNull PsiElement context) {
        AikenFile file = (AikenFile) context.getContainingFile();
        return PsiTreeUtil.findChildrenOfType(file, AikenImportStatement.class)
                .stream().toList();
    }

    /**
     * Get all function definitions in the current file
     */
    @NotNull
    public static List<AikenFunctionStatement> getFunctions(@NotNull PsiElement context) {
        AikenFile file = (AikenFile) context.getContainingFile();
        return PsiTreeUtil.findChildrenOfType(file, AikenFunctionStatement.class)
                .stream().toList();
    }

    /**
     * Get all type definitions in the current file
     */
    @NotNull
    public static List<AikenTypeStatement> getTypes(@NotNull PsiElement context) {
        AikenFile file = (AikenFile) context.getContainingFile();
        return PsiTreeUtil.findChildrenOfType(file, AikenTypeStatement.class)
                .stream().toList();
    }

    /**
     * Get all validators in the current file
     */
    @NotNull
    public static List<AikenValidatorStatement> getValidators(@NotNull PsiElement context) {
        AikenFile file = (AikenFile) context.getContainingFile();
        return PsiTreeUtil.findChildrenOfType(file, AikenValidatorStatement.class)
                .stream().toList();
    }

    /**
     * Check if the element is inside a validator context
     */
    public static boolean isInValidatorContext(@NotNull PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, AikenValidatorStatement.class) != null;
    }

    /**
     * Check if the element is inside an import statement
     */
    public static boolean isInImportContext(@NotNull PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, AikenImportStatement.class) != null;
    }

    /**
     * Get the module path from an import statement
     */
    @Nullable
    public static String getModulePath(@NotNull AikenImportStatement importStatement) {
        AikenImportStatementElement element = importStatement.getImportStatementElement();
        if (element != null) {
            return element.getText();
        }
        return null;
    }
}
