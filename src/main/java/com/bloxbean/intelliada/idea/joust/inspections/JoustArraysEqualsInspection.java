package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.annotations.NotNull;

/**
 * ADR-004 D10, second row: {@code Arrays.equals(byte[], byte[])} in a {@code @StateContract} class. D10 flags it only
 * if JuLC does not compile it. M2 verified that the pinned JuLC ({@code 0.1.0-pre21-rc1}, JOUST's
 * {@code gradle/libs.versions.toml}) rejects it: compiling {@code Arrays.equals(a, b)} on two {@code byte[]} gives
 * {@code JULC0024 Unknown method: java.util.Arrays.equals}, and the qualified form {@code java.util.Arrays.equals}
 * gives JULC0123. The message is JuLC's. Not a JVM hazard (it compares contents there), so the quick fix is the
 * same rewrite to {@code ByteStringLib.equals}.
 */
public final class JoustArraysEqualsInspection extends JoustContractInspection {

    static final String MESSAGE = "JuLC does not compile this: \"Unknown method: java.util.Arrays.equals\" (JULC0024);"
            + " use ByteStringLib.equals(a, b)";

    @Override
    protected @NotNull JavaElementVisitor contractVisitor(@NotNull ProblemsHolder holder) {
        return new JavaElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                if (!"equals".equals(call.getMethodExpression().getReferenceName())) return;
                PsiExpression[] args = call.getArgumentList().getExpressions();
                if (args.length != 2 || !JoustStateContracts.isByteArray(args[0].getType())
                        || !JoustStateContracts.isByteArray(args[1].getType())) return;
                PsiMethod method = call.resolveMethod();
                PsiClass owner = method != null ? method.getContainingClass() : null;
                if (owner == null || !"java.util.Arrays".equals(owner.getQualifiedName())) return;
                if (!JoustStateContracts.isInStateContract(call)) return;
                holder.registerProblem(call, MESSAGE, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        new ByteStringLibEqualsFix());
            }
        };
    }
}
