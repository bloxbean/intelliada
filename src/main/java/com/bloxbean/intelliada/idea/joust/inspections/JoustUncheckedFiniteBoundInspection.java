package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * ADR-004 D10, last row (weak warning): {@code IntervalLib.finiteUpperBound}/{@code finiteLowerBound} compared without
 * a {@code Finite} check. They return -1 for an infinite bound (JOUST {@code CLAUDE.md}: "never compare them without
 * checking {@code Finite}"). A heuristic, documented in the inspection's description:
 * <ul>
 *   <li>the call's value is <em>compared</em>: it is the receiver or the argument of {@code compareTo}/{@code equals},
 *       or an operand of a relational or equality operator, directly or through a local variable it initialises or is
 *       assigned to;</li>
 *   <li>and the enclosing method (or initializer) never names {@code org.julclang.ledger.IntervalBoundType.Finite}
 *       (an {@code instanceof}, a {@code case} pattern, a cast...), which is taken as the check.</li>
 * </ul>
 */
public final class JoustUncheckedFiniteBoundInspection extends JoustContractInspection {

    static final Set<String> BOUNDS = Set.of("finiteUpperBound", "finiteLowerBound");
    private static final Set<String> COMPARISONS = Set.of("compareTo", "equals");
    private static final TokenSet RELATIONAL = TokenSet.create(JavaTokenType.LT, JavaTokenType.LE, JavaTokenType.GT,
            JavaTokenType.GE, JavaTokenType.EQEQ, JavaTokenType.NE);

    @Override
    protected @NotNull JavaElementVisitor contractVisitor(@NotNull ProblemsHolder holder) {
        return new JavaElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                String name = call.getMethodExpression().getReferenceName();
                if (name == null || !BOUNDS.contains(name)) return;
                PsiMethod method = call.resolveMethod();
                PsiClass owner = method != null ? method.getContainingClass() : null;
                if (owner == null || !JoustStateContracts.INTERVAL_LIB.equals(owner.getQualifiedName())) return;
                if (!JoustStateContracts.isInStateContract(call)) return;
                PsiElement scope = PsiTreeUtil.getParentOfType(call, PsiMethod.class, PsiClassInitializer.class, PsiField.class);
                if (scope == null || namesFinite(scope) || !isCompared(call, scope)) return;
                PsiElement at = call.getMethodExpression().getReferenceNameElement();
                holder.registerProblem(at != null ? at : call, "IntervalLib." + name + " returns -1 for an infinite"
                        + " bound; check IntervalBoundType.Finite before comparing it",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        };
    }

    /** Whether the value of {@code call} reaches a comparison, directly or through a local variable. */
    private static boolean isCompared(PsiExpression call, PsiElement scope) {
        if (isComparedDirectly(call)) return true;
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(call.getParent());
        PsiVariable variable = null;
        if (parent instanceof PsiLocalVariable local) {
            variable = local;
        } else if (parent instanceof PsiAssignmentExpression assignment
                && assignment.getOperationTokenType() == JavaTokenType.EQ
                && assignment.getLExpression() instanceof PsiReferenceExpression ref
                && ref.resolve() instanceof PsiLocalVariable local) {
            variable = local;
        }
        if (variable == null) return false;
        for (var reference : ReferencesSearch.search(variable, new LocalSearchScope(scope)).findAll()) {
            if (reference.getElement() instanceof PsiReferenceExpression use && isComparedDirectly(use)) return true;
        }
        return false;
    }

    private static boolean isComparedDirectly(PsiExpression value) {
        PsiExpression expression = value;
        PsiElement parent = value.getParent();
        while (parent instanceof PsiParenthesizedExpression parens) {
            expression = parens;
            parent = parens.getParent();
        }
        if (parent instanceof PsiBinaryExpression binary) return RELATIONAL.contains(binary.getOperationTokenType());
        if (parent instanceof PsiReferenceExpression ref && ref.getQualifierExpression() == expression
                && ref.getParent() instanceof PsiMethodCallExpression) {
            return COMPARISONS.contains(ref.getReferenceName());
        }
        if (parent instanceof PsiExpressionList list && list.getParent() instanceof PsiMethodCallExpression call) {
            return COMPARISONS.contains(call.getMethodExpression().getReferenceName());
        }
        return false;
    }

    private static boolean namesFinite(PsiElement scope) {
        boolean[] found = {false};
        scope.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
                check(reference);
                super.visitReferenceElement(reference);
            }

            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
                check(expression);
                super.visitReferenceExpression(expression);
            }

            private void check(PsiJavaCodeReferenceElement reference) {
                if (found[0] || !"Finite".equals(reference.getReferenceName())) return;
                if (reference.resolve() instanceof PsiClass cls && JoustStateContracts.FINITE.equals(cls.getQualifiedName())) {
                    found[0] = true;
                    stopWalking();
                }
            }
        });
        return found[0];
    }
}
