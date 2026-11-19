package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;

/**
 * ADR-004 D10, last row (weak warning): {@code JulcMap.getOrDefault} in a {@code @StateContract} class. JOUST's
 * {@code CLAUDE.md}: "{@code getOrDefault} isn't on-chain". Matched when the receiver's type is, or inherits,
 * {@code org.julclang.core.types.JulcMap}, or the call resolves to a method of it.
 */
public final class JoustJulcMapGetOrDefaultInspection extends JoustContractInspection {

    static final String MESSAGE = "JulcMap.getOrDefault is not available on-chain; use get(key) and handle the"
            + " missing case";

    @Override
    protected @NotNull JavaElementVisitor contractVisitor(@NotNull ProblemsHolder holder) {
        return new JavaElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                if (!"getOrDefault".equals(call.getMethodExpression().getReferenceName())) return;
                if (!onJulcMap(call)) return;
                if (!JoustStateContracts.isInStateContract(call)) return;
                PsiElement at = call.getMethodExpression().getReferenceNameElement();
                holder.registerProblem(at != null ? at : call, MESSAGE, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        };
    }

    private static boolean onJulcMap(PsiMethodCallExpression call) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier != null && qualifier.getType() instanceof PsiClassType type) {
            PsiClass cls = type.resolve();
            if (cls != null && InheritanceUtil.isInheritor(cls, false, JoustStateContracts.JULC_MAP)) return true;
            if (cls != null && JoustStateContracts.JULC_MAP.equals(cls.getQualifiedName())) return true;
        }
        PsiMethod method = call.resolveMethod();
        PsiClass owner = method != null ? method.getContainingClass() : null;
        return owner != null && JoustStateContracts.JULC_MAP.equals(owner.getQualifiedName());
    }
}
