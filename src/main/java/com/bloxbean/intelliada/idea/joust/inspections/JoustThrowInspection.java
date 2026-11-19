package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiThrowStatement;
import org.jetbrains.annotations.NotNull;

/**
 * ADR-004 D10, {@code throw} row: a {@code throw} statement in a {@code @StateContract} class. JuLC has no
 * {@code throw}; JOUST's {@code CLAUDE.md} says to use {@code Require.that(cond, "msg")}, which throws on the JVM and
 * traces and fails on chain.
 */
public final class JoustThrowInspection extends JoustContractInspection {

    static final String MESSAGE = "'throw' in a @StateContract: JuLC has no throw; use Require.that(cond, \"msg\")";

    @Override
    protected @NotNull JavaElementVisitor contractVisitor(@NotNull ProblemsHolder holder) {
        return new JavaElementVisitor() {
            @Override
            public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
                if (!JoustStateContracts.isInStateContract(statement)) return;
                holder.registerProblem(statement, MESSAGE, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        new TextRange(0, Math.min("throw".length(), statement.getTextLength())));
            }
        };
    }
}
