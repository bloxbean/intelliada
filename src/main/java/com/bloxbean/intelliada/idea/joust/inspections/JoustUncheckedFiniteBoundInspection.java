package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiCaseLabelElementList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDeconstructionList;
import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPattern;
import com.intellij.psi.PsiPatternVariable;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeTestPattern;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ADR-004 D10, last row (weak warning): {@code IntervalLib.finiteUpperBound}/{@code finiteLowerBound} compared without
 * a {@code Finite} check. They return -1 for an infinite bound (JOUST {@code CLAUDE.md}: "never compare them without
 * checking {@code Finite}"). A heuristic, documented in the inspection's description:
 * <ul>
 *   <li>the call's value is <em>compared</em>: it is the receiver or the argument of {@code compareTo}/{@code equals},
 *       or an operand of a relational or equality operator, directly or through a local variable it initialises or is
 *       assigned to;</li>
 *   <li>and no compared use is guarded by a check of <em>that</em> bound: a test against
 *       {@code org.julclang.ledger.IntervalBoundType.Finite} (an {@code instanceof}, a {@code case} pattern, a cast, or a
 *       record pattern of {@code Interval}/{@code IntervalBound}) of {@code i.to().boundType()} for
 *       {@code finiteUpperBound(i)}, or of {@code i.from().boundType()} for {@code finiteLowerBound(i)}, matched through
 *       local variables that are never reassigned;</li>
 *   <li>a check guards a use when it dominates it structurally: it sits in an earlier statement of an enclosing block
 *       (not inside a branch or block of that statement), in the condition of an enclosing {@code if}, loop or
 *       {@code ?:}, in an earlier operand of an enclosing {@code &&}/{@code ||}, or in a {@code case} label of an
 *       enclosing {@code switch}. The check's polarity is not analysed.</li>
 * </ul>
 */
public final class JoustUncheckedFiniteBoundInspection extends JoustContractInspection {

    static final Set<String> BOUNDS = Set.of("finiteUpperBound", "finiteLowerBound");
    private static final Set<String> COMPARISONS = Set.of("compareTo", "equals");
    private static final TokenSet RELATIONAL = TokenSet.create(JavaTokenType.LT, JavaTokenType.LE, JavaTokenType.GT,
            JavaTokenType.GE, JavaTokenType.EQEQ, JavaTokenType.NE);
    private static final int MAX_DEPTH = 8;

    @Override
    protected @NotNull JavaElementVisitor contractVisitor(@NotNull ProblemsHolder holder) {
        return new JavaElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                String name = call.getMethodExpression().getReferenceName();
                if (name == null || !BOUNDS.contains(name)) return;
                PsiExpression[] args = call.getArgumentList().getExpressions();
                if (args.length != 1) return;
                PsiMethod method = call.resolveMethod();
                PsiClass owner = method != null ? method.getContainingClass() : null;
                if (owner == null || !JoustStateContracts.INTERVAL_LIB.equals(owner.getQualifiedName())) return;
                if (!JoustStateContracts.isInStateContract(call)) return;
                PsiElement scope = PsiTreeUtil.getParentOfType(call, PsiMethod.class, PsiClassInitializer.class, PsiField.class);
                if (scope == null) return;
                List<PsiElement> compared = comparedUses(call, scope);
                if (compared.isEmpty()) return;
                String bound = canonical(args[0], 0) + ("finiteUpperBound".equals(name) ? ".to()" : ".from()")
                        + ".boundType()";
                List<PsiElement> checks = finiteChecksOf(scope, bound);
                if (isGuarded(call, checks)) return;
                for (PsiElement use : compared) {
                    if (!isGuarded(use, checks)) {
                        PsiElement at = call.getMethodExpression().getReferenceNameElement();
                        holder.registerProblem(at != null ? at : call, "IntervalLib." + name + " returns -1 for an"
                                        + " infinite bound; check IntervalBoundType.Finite before comparing it",
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                        return;
                    }
                }
            }
        };
    }

    // ---- where the value is compared

    /** The places where the value of {@code call} is compared: the call itself, or the uses of a local it is stored in. */
    private static List<PsiElement> comparedUses(PsiExpression call, PsiElement scope) {
        List<PsiElement> uses = new ArrayList<>();
        if (isComparedDirectly(call)) {
            uses.add(call);
            return uses;
        }
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
        if (variable == null) return uses;
        for (var reference : ReferencesSearch.search(variable, new LocalSearchScope(scope)).findAll()) {
            if (reference.getElement() instanceof PsiReferenceExpression use && isComparedDirectly(use)) uses.add(use);
        }
        return uses;
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

    // ---- which Finite checks test this bound

    /** The references to {@code IntervalBoundType.Finite} in {@code scope} that test the bound {@code bound}. */
    private static List<PsiElement> finiteChecksOf(PsiElement scope, String bound) {
        List<PsiElement> checks = new ArrayList<>();
        scope.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
                if ("Finite".equals(reference.getReferenceName())
                        && reference.resolve() instanceof PsiClass cls
                        && JoustStateContracts.FINITE.equals(cls.getQualifiedName())
                        && bound.equals(testedExpression(reference))) {
                    checks.add(reference);
                }
                super.visitReferenceElement(reference);
            }
        });
        return checks;
    }

    /**
     * The canonical text of the expression a {@code Finite} type reference tests: the operand of an {@code instanceof}
     * or a cast, or the selector of a {@code switch} whose case label it is, followed by the record accessors of any
     * enclosing record patterns ({@code i instanceof Interval(var lo, IntervalBound(Finite f, var inc))} tests
     * {@code i.to().boundType()}). Null when the reference is not such a test.
     */
    private static @Nullable String testedExpression(PsiJavaCodeReferenceElement reference) {
        PsiElement current = reference.getParent();
        if (!(current instanceof PsiTypeElement)) return null;
        List<String> accessors = new ArrayList<>();
        while (true) {
            PsiElement parent = current.getParent();
            if (parent instanceof PsiPatternVariable || parent instanceof PsiTypeTestPattern
                    || parent instanceof PsiDeconstructionPattern pattern && pattern.getTypeElement() == current) {
                current = parent;
            } else if (parent instanceof PsiDeconstructionList list
                    && list.getParent() instanceof PsiDeconstructionPattern record) {
                PsiPattern[] components = list.getDeconstructionComponents();
                int index = List.of(components).indexOf(current);
                PsiClass recordClass = PsiUtil.resolveClassInClassTypeOnly(record.getTypeElement().getType());
                PsiRecordComponent[] recordComponents = recordClass != null ? recordClass.getRecordComponents()
                        : PsiRecordComponent.EMPTY_ARRAY;
                if (index < 0 || index >= recordComponents.length) return null;
                accessors.add(0, recordComponents[index].getName());
                current = record;
            } else {
                break;
            }
        }
        PsiElement parent = current.getParent();
        PsiExpression tested;
        if (parent instanceof PsiInstanceOfExpression instanceOf && instanceOf.getOperand() != current) {
            tested = instanceOf.getOperand();
        } else if (parent instanceof PsiTypeCastExpression cast && cast.getCastType() == current) {
            tested = cast.getOperand();
        } else if (parent instanceof PsiCaseLabelElementList
                && PsiTreeUtil.getParentOfType(parent, PsiSwitchBlock.class) instanceof PsiSwitchBlock block) {
            tested = block.getExpression();
        } else {
            return null;
        }
        if (tested == null) return null;
        StringBuilder text = new StringBuilder(canonical(tested, 0));
        for (String accessor : accessors) text.append('.').append(accessor).append("()");
        return text.toString();
    }

    /**
     * A canonical text for comparing two expressions: no-argument calls and qualified references by their parts, a
     * local variable that is never reassigned by its initializer, anything else by its text without whitespace.
     */
    private static String canonical(@Nullable PsiExpression expression, int depth) {
        PsiExpression e = PsiUtil.skipParenthesizedExprDown(expression);
        if (e == null) return "";
        if (depth < MAX_DEPTH) {
            if (e instanceof PsiMethodCallExpression call && call.getArgumentList().getExpressionCount() == 0
                    && call.getMethodExpression().getQualifierExpression() != null) {
                return canonical(call.getMethodExpression().getQualifierExpression(), depth + 1) + "."
                        + call.getMethodExpression().getReferenceName() + "()";
            }
            if (e instanceof PsiReferenceExpression ref) {
                PsiExpression qualifier = ref.getQualifierExpression();
                if (qualifier != null) return canonical(qualifier, depth + 1) + "." + ref.getReferenceName();
                if (ref.resolve() instanceof PsiLocalVariable local && local.getInitializer() != null
                        && !isReassigned(local)) {
                    return canonical(local.getInitializer(), depth + 1);
                }
                return String.valueOf(ref.getReferenceName());
            }
        }
        return e.getText().replaceAll("\\s+", "");
    }

    private static boolean isReassigned(PsiLocalVariable local) {
        PsiElement block = PsiTreeUtil.getParentOfType(local, PsiCodeBlock.class);
        if (block == null) return true;
        for (var reference : ReferencesSearch.search(local, new LocalSearchScope(block)).findAll()) {
            if (reference.getElement() instanceof PsiExpression use && PsiUtil.isAccessedForWriting(use)) return true;
        }
        return false;
    }

    // ---- whether a check guards a use

    private static boolean isGuarded(PsiElement use, List<PsiElement> checks) {
        for (PsiElement check : checks) {
            if (dominates(check, use)) return true;
        }
        return false;
    }

    /** Whether {@code check} structurally dominates {@code use} (see the class comment). */
    private static boolean dominates(PsiElement check, PsiElement use) {
        PsiElement child = use;
        PsiElement parent = use.getParent();
        while (parent != null && !(child instanceof PsiMember) && !(child instanceof PsiFile)) {
            if (parent instanceof PsiCodeBlock && child instanceof PsiStatement) {
                for (PsiElement sibling = child.getPrevSibling(); sibling != null; sibling = sibling.getPrevSibling()) {
                    if (sibling instanceof PsiStatement statement && PsiTreeUtil.getParentOfType(check,
                            PsiStatement.class, PsiLambdaExpression.class, PsiClass.class) == statement) {
                        return true;
                    }
                }
            } else if (parent instanceof PsiIfStatement statement) {
                if (child != statement.getCondition() && within(check, statement.getCondition())) return true;
            } else if (parent instanceof PsiWhileStatement statement) {
                if (child == statement.getBody() && within(check, statement.getCondition())) return true;
            } else if (parent instanceof PsiForStatement statement) {
                if ((child == statement.getBody() || child == statement.getUpdate())
                        && within(check, statement.getCondition())) {
                    return true;
                }
            } else if (parent instanceof PsiConditionalExpression conditional) {
                if (child != conditional.getCondition() && within(check, conditional.getCondition())) return true;
            } else if (parent instanceof PsiPolyadicExpression polyadic && isShortCircuit(polyadic.getOperationTokenType())) {
                for (PsiExpression operand : polyadic.getOperands()) {
                    if (operand == child) break;
                    if (within(check, operand)) return true;
                }
            } else if (parent instanceof PsiSwitchBlock block) {
                if (child == block.getBody() && within(check, block.getBody())
                        && PsiTreeUtil.getParentOfType(check, PsiCaseLabelElementList.class) instanceof PsiCaseLabelElementList list
                        && PsiTreeUtil.getParentOfType(list, PsiSwitchBlock.class) == block) {
                    return true;
                }
            }
            child = parent;
            parent = parent.getParent();
        }
        return false;
    }

    private static boolean isShortCircuit(IElementType operation) {
        return operation == JavaTokenType.ANDAND || operation == JavaTokenType.OROR;
    }

    private static boolean within(PsiElement check, @Nullable PsiElement region) {
        return region != null && PsiTreeUtil.isAncestor(region, check, false);
    }
}
