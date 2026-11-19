package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * Base of ADR-004 D10's inspections: a visitor only for Java files that may hold a {@code @StateContract} class. Each
 * inspection then reports an element only when {@link JoustStateContracts#isInStateContract} holds for it, so nothing
 * outside a (resolved) {@code @StateContract} class is ever flagged. All of them are advice: JOUST's processor and JuLC
 * stay the authority (D10).
 */
public abstract class JoustContractInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    public final @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!JoustStateContracts.mayContainStateContract(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
        return contractVisitor(holder);
    }

    protected abstract @NotNull JavaElementVisitor contractVisitor(@NotNull ProblemsHolder holder);
}
