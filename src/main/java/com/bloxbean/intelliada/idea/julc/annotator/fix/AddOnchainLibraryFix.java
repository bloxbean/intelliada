package com.bloxbean.intelliada.idea.julc.annotator.fix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Quick fix that adds @OnchainLibrary annotation to a class referenced from a julc validator.
 * Triggered from JulcOnchainLibraryAnnotator when a project class is missing the annotation.
 */
public class AddOnchainLibraryFix implements IntentionAction {

    private static final String ONCHAIN_LIBRARY_FQN = "com.bloxbean.cardano.julc.stdlib.annotation.OnchainLibrary";

    private final SmartPsiElementPointer<PsiClass> targetClassPointer;
    private final String className;

    public AddOnchainLibraryFix(@NotNull PsiClass targetClass) {
        this.targetClassPointer = SmartPointerManager.createPointer(targetClass);
        this.className = targetClass.getName();
    }

    @Override
    public @NotNull String getText() {
        return "Add @OnchainLibrary to '" + className + "'";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "julc";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        PsiClass targetClass = targetClassPointer.getElement();
        return targetClass != null && targetClass.isValid();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiClass targetClass = targetClassPointer.getElement();
        if (targetClass == null || !targetClass.isValid()) return;

        PsiFile targetFile = targetClass.getContainingFile();
        if (targetFile == null) return;

        // Add annotation
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiAnnotation annotation = factory.createAnnotationFromText("@OnchainLibrary", targetClass);

        PsiModifierList modifierList = targetClass.getModifierList();
        if (modifierList != null) {
            modifierList.addBefore(annotation, modifierList.getFirstChild());
        }

        // Add import
        if (targetFile instanceof PsiJavaFile javaFile) {
            JavaCodeStyleManager.getInstance(project).addImport(javaFile,
                    JavaPsiFacade.getInstance(project).findClass(ONCHAIN_LIBRARY_FQN, targetClass.getResolveScope()));
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        return IntentionPreviewInfo.EMPTY;
    }
}
