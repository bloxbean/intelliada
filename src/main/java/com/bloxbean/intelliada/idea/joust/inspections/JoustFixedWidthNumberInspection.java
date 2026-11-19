package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * ADR-004 D10, fixed-width numbers: mirrors JOUST's {@code SubsetLint} ({@code julc-state-processor}, tag
 * {@code v0.1.0-pre2}) rule for rule, as earlier feedback only. JuLC compiles {@code int}/{@code long} arithmetic as
 * unbounded integers and {@code intValue()} as the identity, while the JVM wraps; {@code float}/{@code double} have no
 * on-chain meaning. Flagged inside a {@code @StateContract} class:
 * <ol>
 *   <li>a written primitive type {@code int}, {@code long}, {@code short}, {@code char}, {@code float} or
 *       {@code double} (SubsetLint's FIXED_WIDTH_WRITTEN; {@code byte} is allowed there, {@code byte[]} being a
 *       bytestring);</li>
 *   <li>a qualified call of a narrowing method (SubsetLint's NARROWING_CALLS);</li>
 *   <li>arithmetic (SubsetLint's ARITHMETIC set, binary, unary and compound) whose type is fixed-width, {@code byte}
 *       included. A negative int or long literal ({@code -1}) is a literal to javac, so it is not flagged;</li>
 *   <li>a variable (local, {@code var}, parameter, implicit lambda parameter, field) whose type is fixed-width. A
 *       written type is already flagged by item 1, so the variable is reported only when its type is inferred (on its
 *       {@code var}) or {@code byte};</li>
 *   <li>beyond SubsetLint (D10 names "their boxes"): a reference to {@code Integer}, {@code Long}, {@code Short},
 *       {@code Byte}, {@code Character}, {@code Float} or {@code Double}, and an inferred variable of a box type.</li>
 * </ol>
 * Nested fixed-width arithmetic is reported once, on the outermost expression.
 */
public final class JoustFixedWidthNumberInspection extends JoustContractInspection {

    static final Set<String> WRITTEN = Set.of("int", "long", "short", "char", "float", "double");

    static final Set<String> NARROWING_CALLS = Set.of(
            "intValue", "longValue", "shortValue", "byteValue", "intValueExact", "longValueExact", "shortValueExact",
            "byteValueExact", "doubleValue", "floatValue");

    private static final TokenSet BINARY_ARITHMETIC = TokenSet.create(
            JavaTokenType.PLUS, JavaTokenType.MINUS, JavaTokenType.ASTERISK, JavaTokenType.DIV, JavaTokenType.PERC,
            JavaTokenType.LTLT, JavaTokenType.GTGT, JavaTokenType.GTGTGT, JavaTokenType.AND, JavaTokenType.OR,
            JavaTokenType.XOR);

    private static final TokenSet PREFIX_ARITHMETIC = TokenSet.create(
            JavaTokenType.MINUS, JavaTokenType.PLUS, JavaTokenType.TILDE, JavaTokenType.PLUSPLUS,
            JavaTokenType.MINUSMINUS);

    private static final String WHY = "JuLC treats it as an unbounded integer on-chain while the JVM wraps or rounds"
            + " it, so the two runs could disagree. Use BigInteger";

    @Override
    protected @NotNull JavaElementVisitor contractVisitor(@NotNull ProblemsHolder holder) {
        return new JavaElementVisitor() {
            @Override
            public void visitKeyword(@NotNull PsiKeyword keyword) {
                if (!WRITTEN.contains(keyword.getText())) return;
                if (!JoustStateContracts.isInStateContract(keyword)) return;
                report(holder, keyword, null, "'" + keyword.getText() + "' in a @StateContract: " + WHY);
            }

            @Override
            public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
                box(reference);
            }

            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
                box(expression);
            }

            private void box(PsiJavaCodeReferenceElement reference) {
                String name = reference.getReferenceName();
                if (name == null || !JoustStateContracts.FIXED_WIDTH_BOXES.contains("java.lang." + name)) return;
                if (PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class) != null) return;
                if (!(reference.resolve() instanceof PsiClass cls)
                        || !JoustStateContracts.FIXED_WIDTH_BOXES.contains(cls.getQualifiedName())) return;
                if (!JoustStateContracts.isInStateContract(reference)) return;
                PsiElement at = reference.getReferenceNameElement() != null ? reference.getReferenceNameElement() : reference;
                report(holder, at, null, "'" + name + "' in a @StateContract: a fixed-width box. " + WHY);
            }

            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                PsiReferenceExpression method = call.getMethodExpression();
                String name = method.getReferenceName();
                if (name == null || !NARROWING_CALLS.contains(name) || method.getQualifier() == null) return;
                if (!JoustStateContracts.isInStateContract(call)) return;
                PsiElement at = method.getReferenceNameElement() != null ? method.getReferenceNameElement() : call;
                report(holder, at, null, name + "() in a @StateContract: it narrows on the JVM but is the identity"
                        + " on-chain, so the two runs could disagree. Stay in BigInteger");
            }

            @Override
            public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
                arithmetic(expression);
            }

            @Override
            public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
                arithmetic(expression);
            }

            @Override
            public void visitPostfixExpression(@NotNull PsiPostfixExpression expression) {
                arithmetic(expression);
            }

            @Override
            public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
                arithmetic(expression);
            }

            private void arithmetic(PsiExpression expression) {
                TextRange range = flaggedRange(expression);
                if (range == null) return;
                PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
                if (parent instanceof PsiExpression outer) {
                    TextRange outerRange = flaggedRange(outer);
                    if (outerRange != null && outerRange.contains(expression.getTextRange())) return;
                }
                if (!JoustStateContracts.isInStateContract(expression)) return;
                PsiType type = typeOfRange(expression);
                report(holder, expression, range.shiftLeft(expression.getTextRange().getStartOffset()),
                        "fixed-width arithmetic (" + (type != null ? type.getPresentableText() : "?")
                                + ") in a @StateContract: the JVM wraps, on-chain integers are unbounded, so the two"
                                + " runs could disagree. Compute in BigInteger");
            }

            @Override
            public void visitVariable(@NotNull PsiVariable variable) {
                PsiType type = variable.getType();
                boolean fixed = JoustStateContracts.isFixedWidth(type);
                if (!fixed && !JoustStateContracts.isFixedWidthBox(type)) return;
                PsiTypeElement typeElement = variable.getTypeElement();
                boolean written = typeElement != null && !typeElement.isInferredType();
                // a written type is flagged where it is written (keyword or box reference), except byte
                if (written && !PsiTypes.byteType().equals(type)) return;
                if (!JoustStateContracts.isInStateContract(variable)) return;
                // an inferred type is reported on its `var` (javac places the inferred type there), else on the name
                PsiElement at = typeElement != null && !written && typeElement.getTextLength() > 0 ? typeElement
                        : variable.getNameIdentifier() != null ? variable.getNameIdentifier() : variable;
                report(holder, at, null, "'" + variable.getName() + "' is "
                        + type.getPresentableText() + " in a @StateContract (inferred or declared); fixed-width numbers"
                        + " diverge between the JVM and on-chain. Use BigInteger");
            }
        };
    }

    private static void report(ProblemsHolder holder, PsiElement element, @Nullable TextRange inElement, String message) {
        holder.registerProblem(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, inElement);
    }

    /** The type the flagged range of {@code expression} has (for a polyadic prefix, of that prefix). */
    private static @Nullable PsiType typeOfRange(PsiExpression expression) {
        if (expression instanceof PsiPolyadicExpression polyadic && polyadic.getOperands().length > 2) {
            return prefixType(polyadic, longestFixedPrefix(polyadic));
        }
        return expression.getType();
    }

    /**
     * The absolute range of {@code expression} that SubsetLint's typed pass would flag, or null. javac nests
     * {@code a + b + c} as {@code (a + b) + c}, so for a polyadic expression this is its longest prefix whose type is
     * fixed-width ({@code 1 + 2 + "s"} flags {@code 1 + 2}).
     */
    static @Nullable TextRange flaggedRange(PsiExpression expression) {
        if (expression instanceof PsiPolyadicExpression polyadic) {
            if (!BINARY_ARITHMETIC.contains(polyadic.getOperationTokenType())) return null;
            int last = longestFixedPrefix(polyadic);
            if (last < 1) return null;
            PsiExpression[] operands = polyadic.getOperands();
            return new TextRange(operands[0].getTextRange().getStartOffset(), operands[last].getTextRange().getEndOffset());
        }
        if (expression instanceof PsiPrefixExpression prefix) {
            IElementType op = prefix.getOperationTokenType();
            if (!PREFIX_ARITHMETIC.contains(op) || !JoustStateContracts.isFixedWidth(prefix.getType())) return null;
            // javac reads `-1` and `-1L` as one negative literal, not as a unary minus
            if (op == JavaTokenType.MINUS && prefix.getOperand() instanceof PsiLiteralExpression literal
                    && (PsiTypes.intType().equals(literal.getType()) || PsiTypes.longType().equals(literal.getType()))) {
                return null;
            }
            return prefix.getTextRange();
        }
        if (expression instanceof PsiPostfixExpression postfix) {
            return JoustStateContracts.isFixedWidth(postfix.getType()) ? postfix.getTextRange() : null;
        }
        if (expression instanceof PsiAssignmentExpression assignment) {
            if (assignment.getOperationTokenType() == JavaTokenType.EQ) return null;
            return JoustStateContracts.isFixedWidth(assignment.getType()) ? assignment.getTextRange() : null;
        }
        return null;
    }

    /** Index of the last operand of the longest fixed-width prefix, or -1 if even the first pair is not fixed-width. */
    private static int longestFixedPrefix(PsiPolyadicExpression polyadic) {
        PsiExpression[] operands = polyadic.getOperands();
        IElementType op = polyadic.getOperationTokenType();
        PsiType type = operands[0].getType();
        int last = -1;
        for (int i = 1; i < operands.length; i++) {
            if (type == null || operands[i].getType() == null) break;
            type = TypeConversionUtil.calcTypeForBinaryExpression(type, operands[i].getType(), op, true);
            if (!JoustStateContracts.isFixedWidth(type)) break;
            last = i;
        }
        return last;
    }

    private static @Nullable PsiType prefixType(PsiPolyadicExpression polyadic, int last) {
        PsiExpression[] operands = polyadic.getOperands();
        PsiType type = operands[0].getType();
        for (int i = 1; i <= last && type != null; i++) {
            type = TypeConversionUtil.calcTypeForBinaryExpression(type, operands[i].getType(), polyadic.getOperationTokenType(), true);
        }
        return type;
    }
}
