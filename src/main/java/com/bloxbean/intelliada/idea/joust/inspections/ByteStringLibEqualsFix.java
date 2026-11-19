package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ADR-004 D10's quick fix for byte-array equality: rewrites {@code a == b}, {@code a != b}, {@code a.equals(b)},
 * {@code Objects.equals(a, b)} and {@code Arrays.equals(a, b)} to {@code ByteStringLib.equals(a, b)} (negated for
 * {@code !=}). JuLC's {@code ByteStringLib.equals} takes {@code (byte[], byte[])}, so the fix is offered only when both
 * operands are {@code byte[]} ({@link #fixesFor}).
 * <p>The call uses the simple class name, because JuLC rejects a static call through a qualified class name (JULC0123),
 * and imports {@code org.julclang.stdlib.lib.ByteStringLib} when the name does not resolve yet. When another class named
 * {@code ByteStringLib} is in scope (an import of it, or a nested class), the call is qualified instead, so it still
 * compiles and resolves on the JVM; JuLC then reports JULC0123, which is the honest outcome of that name clash.
 */
public final class ByteStringLibEqualsFix extends PsiUpdateModCommandQuickFix {

    private static final String SIMPLE_NAME = "ByteStringLib";

    @Override
    public @NotNull String getFamilyName() {
        return "Replace with ByteStringLib.equals(a, b)";
    }

    /** The fix for {@code element} when its rewrite would compile (both operands {@code byte[]}), otherwise none. */
    static LocalQuickFix @NotNull [] fixesFor(@NotNull PsiElement element) {
        return byteArrayOperands(element) != null ? new LocalQuickFix[]{new ByteStringLibEqualsFix()} : LocalQuickFix.EMPTY_ARRAY;
    }

    /** The two compared operands of a supported comparison when both are {@code byte[]}, otherwise null. */
    static PsiExpression @Nullable [] byteArrayOperands(@NotNull PsiElement element) {
        PsiExpression left;
        PsiExpression right;
        if (element instanceof PsiBinaryExpression binary) {
            left = binary.getLOperand();
            right = binary.getROperand();
        } else if (element instanceof PsiMethodCallExpression call) {
            PsiExpression[] args = call.getArgumentList().getExpressions();
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (args.length == 2) {
                left = args[0];
                right = args[1];
            } else if (args.length == 1 && qualifier != null) {
                left = qualifier;
                right = args[0];
            } else {
                return null;
            }
        } else {
            return null;
        }
        if (left == null || right == null) return null;
        if (!JoustStateContracts.isByteArray(left.getType()) || !JoustStateContracts.isByteArray(right.getType())) return null;
        return new PsiExpression[]{left, right};
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
        PsiExpression[] operands = byteArrayOperands(element);
        if (operands == null) return;
        boolean negate = element instanceof PsiBinaryExpression binary && binary.getOperationTokenType() == JavaTokenType.NE;
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        // what the simple name means here before the rewrite: nothing (import it), JuLC's class, or another class (qualify)
        PsiClass visible = facade.getResolveHelper().resolveReferencedClass(SIMPLE_NAME, element);
        boolean otherInScope = visible != null && !JoustStateContracts.BYTE_STRING_LIB.equals(visible.getQualifiedName());
        PsiElementFactory factory = facade.getElementFactory();
        String text = (negate ? "!" : "") + (otherInScope ? JoustStateContracts.BYTE_STRING_LIB : SIMPLE_NAME) + ".equals("
                + PsiUtil.skipParenthesizedExprDown(operands[0]).getText() + ", "
                + PsiUtil.skipParenthesizedExprDown(operands[1]).getText() + ")";
        PsiElement replaced = element.replace(factory.createExpressionFromText(text, element));
        if (otherInScope || visible != null) return;
        PsiMethodCallExpression call = PsiTreeUtil.findChildOfType(replaced, PsiMethodCallExpression.class, false);
        if (call == null || !(replaced.getContainingFile() instanceof PsiJavaFile file)) return;
        PsiClass target = facade.findClass(JoustStateContracts.BYTE_STRING_LIB, file.getResolveScope());
        if (target != null) {
            JavaCodeStyleManager.getInstance(project).addImport(file, target);
            if (!resolvesToByteStringLib(call)) qualify(factory, call);
        } else if (!importsAnotherByteStringLib(file)) {
            // The library is not resolvable here: add the import by text, so JuLC still finds the simple name
            addImportByText(project, file);
        } else {
            qualify(factory, call);
        }
    }

    private static boolean resolvesToByteStringLib(PsiMethodCallExpression call) {
        return call.getMethodExpression().getQualifierExpression() instanceof PsiReferenceExpression ref
                && ref.resolve() instanceof PsiClass cls && JoustStateContracts.BYTE_STRING_LIB.equals(cls.getQualifiedName());
    }

    private static void qualify(PsiElementFactory factory, PsiMethodCallExpression call) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier != null) {
            qualifier.replace(factory.createExpressionFromText(JoustStateContracts.BYTE_STRING_LIB, qualifier));
        }
    }

    private static boolean importsAnotherByteStringLib(PsiJavaFile file) {
        PsiImportList imports = file.getImportList();
        if (imports == null) return false;
        for (PsiImportStatement statement : imports.getImportStatements()) {
            String name = statement.getQualifiedName();
            if (!statement.isOnDemand() && name != null && name.endsWith("." + SIMPLE_NAME)
                    && !JoustStateContracts.BYTE_STRING_LIB.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void addImportByText(Project project, PsiJavaFile file) {
        PsiImportList imports = file.getImportList();
        if (imports == null || imports.findSingleClassImportStatement(JoustStateContracts.BYTE_STRING_LIB) != null) return;
        PsiJavaFile parsed = (PsiJavaFile) PsiFileFactory.getInstance(project).createFileFromText(
                "_Import.java", JavaLanguage.INSTANCE, "import " + JoustStateContracts.BYTE_STRING_LIB + ";");
        PsiImportList parsedImports = parsed.getImportList();
        if (parsedImports != null && parsedImports.getImportStatements().length == 1) {
            imports.add(parsedImports.getImportStatements()[0]);
        }
    }
}
