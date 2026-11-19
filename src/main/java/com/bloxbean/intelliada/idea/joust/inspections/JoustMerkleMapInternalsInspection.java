package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * ADR-004 D10, {@code MerkleMap} row (error): in a {@code @StateContract} class, reading {@code MerkleMap.reads()},
 * {@code writes()} or {@code root()}, and constructing a {@code MerkleMap} or {@code MapEntry}. Mirrors
 * {@code SubsetLint}'s typed rules (member select on a {@code MerkleMap}, {@code new} of either type), and also flags a
 * method reference ({@code map::root}) and an anonymous subclass, which SubsetLint does not see.
 */
public final class JoustMerkleMapInternalsInspection extends JoustContractInspection {

    static final Set<String> INTERNALS = Set.of("reads", "writes", "root");

    @Override
    protected @NotNull JavaElementVisitor contractVisitor(@NotNull ProblemsHolder holder) {
        return new JavaElementVisitor() {
            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
                String name = expression.getReferenceName();
                if (name == null || !INTERNALS.contains(name)) return;
                PsiExpression qualifier = expression.getQualifierExpression();
                if (qualifier == null || !JoustStateContracts.MERKLE_MAP.equals(JoustStateContracts.className(qualifier.getType()))) {
                    return;
                }
                if (!JoustStateContracts.isInStateContract(expression)) return;
                PsiElement at = expression.getReferenceNameElement() != null ? expression.getReferenceNameElement() : expression;
                holder.registerProblem(at, "MerkleMap." + name + "() in a @StateContract: its internals differ between"
                        + " the JVM run and the on-chain run. Use MerkleMaps helpers", ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }

            @Override
            public void visitNewExpression(@NotNull PsiNewExpression expression) {
                PsiAnonymousClass anonymous = expression.getAnonymousClass();
                PsiJavaCodeReferenceElement ref = anonymous != null ? anonymous.getBaseClassReference()
                        : expression.getClassReference();
                if (ref == null || !(ref.resolve() instanceof PsiClass cls)) return;
                String fqn = cls.getQualifiedName();
                if (!JoustStateContracts.MERKLE_MAP.equals(fqn) && !JoustStateContracts.MAP_ENTRY.equals(fqn)) return;
                if (!JoustStateContracts.isInStateContract(expression)) return;
                holder.registerProblem(ref, "constructing " + cls.getName() + " in a @StateContract: a hand-made map"
                        + " carries reads nobody proved. Use MerkleMaps.empty() and its helpers",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        };
    }
}
