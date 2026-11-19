package com.bloxbean.intelliada.idea.joust.inspections;

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
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

/**
 * ADR-004 D10's quick fix for byte-array equality: rewrites {@code a == b}, {@code a != b}, {@code a.equals(b)},
 * {@code Objects.equals(a, b)} and {@code Arrays.equals(a, b)} to {@code ByteStringLib.equals(a, b)} (negated for
 * {@code !=}), and imports {@code org.julclang.stdlib.lib.ByteStringLib}. The call uses the simple class name, because
 * JuLC rejects a static call through a qualified class name (JULC0123).
 */
public final class ByteStringLibEqualsFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
        return "Replace with ByteStringLib.equals(a, b)";
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
        PsiExpression left;
        PsiExpression right;
        boolean negate = false;
        if (element instanceof PsiBinaryExpression binary) {
            left = binary.getLOperand();
            right = binary.getROperand();
            negate = binary.getOperationTokenType() == JavaTokenType.NE;
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
                return;
            }
        } else {
            return;
        }
        if (left == null || right == null) return;
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        String text = (negate ? "!" : "") + "ByteStringLib.equals(" + PsiUtil.skipParenthesizedExprDown(left).getText()
                + ", " + PsiUtil.skipParenthesizedExprDown(right).getText() + ")";
        PsiExpression replacement = factory.createExpressionFromText(text, element);
        PsiElement replaced = element.replace(replacement);
        if (replaced.getContainingFile() instanceof PsiJavaFile file) addImport(project, file);
    }

    private static void addImport(Project project, PsiJavaFile file) {
        PsiImportList imports = file.getImportList();
        if (imports == null) return;
        if (imports.findSingleClassImportStatement(JoustStateContracts.BYTE_STRING_LIB) != null) return;
        if (imports.findOnDemandImportStatement("org.julclang.stdlib.lib") != null) return;
        PsiClass cls = JavaPsiFacade.getInstance(project).findClass(JoustStateContracts.BYTE_STRING_LIB, file.getResolveScope());
        if (cls != null && JavaCodeStyleManager.getInstance(project).addImport(file, cls)) return;
        // The library is not resolvable here: add the import by text, so JuLC still finds the simple name
        PsiJavaFile parsed = (PsiJavaFile) PsiFileFactory.getInstance(project).createFileFromText(
                "_Import.java", JavaLanguage.INSTANCE, "import " + JoustStateContracts.BYTE_STRING_LIB + ";");
        PsiImportList parsedImports = parsed.getImportList();
        if (parsedImports != null && parsedImports.getImportStatements().length == 1) {
            imports.add(parsedImports.getImportStatements()[0]);
        }
    }
}
