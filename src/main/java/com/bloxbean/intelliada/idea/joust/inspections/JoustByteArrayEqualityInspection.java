package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * ADR-004 D10, first row: in a {@code @StateContract} class, {@code byte[]} compared with {@code ==}/{@code !=}
 * (JuLC's JULC0086), with {@code byte[].equals} (JULC0087) or with {@code Objects.equals} on a {@code byte[]} operand
 * (JULC0087). On the JVM these compare references, on chain bytes. Earlier feedback for the pinned JuLC's own errors
 * (F28), with {@link ByteStringLibEqualsFix}.
 */
public final class JoustByteArrayEqualityInspection extends JoustContractInspection {

    static final String HAZARD = "compares references on the JVM and bytes on chain; use ByteStringLib.equals(a, b)";

    @Override
    protected @NotNull JavaElementVisitor contractVisitor(@NotNull ProblemsHolder holder) {
        return new JavaElementVisitor() {
            @Override
            public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
                IElementType op = expression.getOperationTokenType();
                if (op != JavaTokenType.EQEQ && op != JavaTokenType.NE) return;
                PsiExpression right = expression.getROperand();
                if (right == null) return;
                if (!JoustStateContracts.isByteArray(expression.getLOperand().getType())
                        && !JoustStateContracts.isByteArray(right.getType())) return;
                if (!JoustStateContracts.isInStateContract(expression)) return;
                holder.registerProblem(expression, "byte[] " + expression.getOperationSign().getText() + " "
                                + HAZARD + " (JuLC JULC0086)", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        new ByteStringLibEqualsFix());
            }

            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                if (!"equals".equals(call.getMethodExpression().getReferenceName())) return;
                PsiExpression[] args = call.getArgumentList().getExpressions();
                PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
                String what;
                if (args.length == 1 && qualifier != null && JoustStateContracts.isByteArray(qualifier.getType())) {
                    what = "byte[].equals";
                } else if (args.length == 2 && isObjectsEquals(call)
                        && (JoustStateContracts.isByteArray(args[0].getType())
                        || JoustStateContracts.isByteArray(args[1].getType()))) {
                    what = "Objects.equals on byte[]";
                } else {
                    return;
                }
                if (!JoustStateContracts.isInStateContract(call)) return;
                holder.registerProblem(call, what + " " + HAZARD + " (JuLC JULC0087)",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new ByteStringLibEqualsFix());
            }
        };
    }

    private static boolean isObjectsEquals(PsiMethodCallExpression call) {
        PsiMethod method = call.resolveMethod();
        PsiClass owner = method != null ? method.getContainingClass() : null;
        return owner != null && "java.util.Objects".equals(owner.getQualifiedName());
    }
}
