package com.bloxbean.intelliada.idea.joust.inspections;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Where ADR-004 D10's inspections apply, and the julc-annotator guard's predicate (D10, F4).
 * <ul>
 *   <li>{@link #isInStateContract}: an element inside a class (at any depth) annotated
 *       {@code org.julclang.state.api.StateContract}, resolved by qualified name. An annotation that does not resolve
 *       (the JOUST API is not on the module's classpath) does not count: the inspections are advice and stay silent.</li>
 *   <li>{@link #referencesStateContract}: a Java file that names {@code @StateContract} at all (resolved, or by its
 *       qualified name, or by its simple name with a matching import), so the bundled julc {@code SubsetValidator}
 *       never runs on it. This side errs towards skipping.</li>
 * </ul>
 */
public final class JoustStateContracts {
    private JoustStateContracts() {
    }

    public static final String STATE_CONTRACT = "org.julclang.state.api.StateContract";
    public static final String STATE_API_PACKAGE = "org.julclang.state.api";
    public static final String MERKLE_MAP = "org.julclang.state.api.MerkleMap";
    public static final String MAP_ENTRY = "org.julclang.state.api.MapEntry";
    public static final String BYTE_STRING_LIB = "org.julclang.stdlib.lib.ByteStringLib";
    public static final String INTERVAL_LIB = "org.julclang.stdlib.lib.IntervalLib";
    public static final String JULC_MAP = "org.julclang.core.types.JulcMap";
    public static final String FINITE = "org.julclang.ledger.IntervalBoundType.Finite";

    /** The boxes of the fixed-width numbers (D10: "their boxes"); a superset of SubsetLint, which has no box rule. */
    public static final Set<String> FIXED_WIDTH_BOXES = Set.of(
            "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte", "java.lang.Character",
            "java.lang.Float", "java.lang.Double");

    private static final String SIMPLE_NAME = "StateContract";

    /** Cheap pre-filter for a visitor: does any class in the file carry an annotation named StateContract? */
    public static boolean mayContainStateContract(PsiFile file) {
        if (!(file instanceof PsiJavaFile)) return false;
        for (PsiAnnotation annotation : PsiTreeUtil.findChildrenOfType(file, PsiAnnotation.class)) {
            PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
            if (ref != null && SIMPLE_NAME.equals(ref.getReferenceName())) return true;
        }
        return false;
    }

    /** Whether {@code element} sits in a class annotated {@code @StateContract} (resolved), at any nesting depth. */
    public static boolean isInStateContract(PsiElement element) {
        PsiClass cls = element instanceof PsiClass c ? c : PsiTreeUtil.getParentOfType(element, PsiClass.class);
        while (cls != null) {
            if (isStateContractClass(cls)) return true;
            cls = PsiTreeUtil.getParentOfType(cls, PsiClass.class);
        }
        return false;
    }

    public static boolean isStateContractClass(PsiClass cls) {
        PsiModifierList modifiers = cls.getModifierList();
        if (modifiers == null) return false;
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
            if (ref == null || !SIMPLE_NAME.equals(ref.getReferenceName())) continue;
            if (ref.resolve() instanceof PsiClass target && STATE_CONTRACT.equals(target.getQualifiedName())) return true;
        }
        return false;
    }

    /**
     * The julc-annotator guard (D10): true when the Java file references {@code @StateContract}: an import of it, or an
     * annotation that resolves to it, is written with its qualified name, or is written {@code @StateContract} under an
     * on-demand import of (or inside) its package. A PSI check, not a text search.
     */
    public static boolean referencesStateContract(PsiFile file) {
        if (!(file instanceof PsiJavaFile javaFile)) return false;
        boolean packageVisible = STATE_API_PACKAGE.equals(javaFile.getPackageName());
        PsiImportList imports = javaFile.getImportList();
        if (imports != null) {
            for (PsiImportStatement statement : imports.getImportStatements()) {
                String name = statement.getQualifiedName();
                if (STATE_CONTRACT.equals(name)) return true;
                if (statement.isOnDemand() && STATE_API_PACKAGE.equals(name)) packageVisible = true;
            }
        }
        for (PsiAnnotation annotation : PsiTreeUtil.findChildrenOfType(javaFile, PsiAnnotation.class)) {
            PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
            if (ref == null || !SIMPLE_NAME.equals(ref.getReferenceName())) continue;
            if (ref.resolve() instanceof PsiClass target && STATE_CONTRACT.equals(target.getQualifiedName())) return true;
            if (STATE_CONTRACT.equals(ref.getQualifiedName()) || STATE_CONTRACT.equals(ref.getText().replace(" ", ""))) {
                return true;
            }
            if (!ref.isQualified() && packageVisible) return true;
        }
        return false;
    }

    // ---- types

    /** The fixed-width primitive types of SubsetLint's FIXED_WIDTH: int, long, short, char, byte, float, double. */
    public static boolean isFixedWidth(@Nullable PsiType type) {
        return type instanceof PsiPrimitiveType p && !PsiTypes.booleanType().equals(p) && !PsiTypes.voidType().equals(p)
                && !PsiTypes.nullType().equals(p);
    }

    public static boolean isFixedWidthBox(@Nullable PsiType type) {
        return type instanceof PsiClassType ct && FIXED_WIDTH_BOXES.contains(ct.rawType().getCanonicalText());
    }

    public static boolean isByteArray(@Nullable PsiType type) {
        return type instanceof PsiArrayType a && PsiTypes.byteType().equals(a.getComponentType());
    }

    /** The qualified name of a class type's class, or null. */
    public static @Nullable String className(@Nullable PsiType type) {
        if (!(type instanceof PsiClassType ct)) return null;
        PsiClass cls = ct.resolve();
        return cls != null ? cls.getQualifiedName() : null;
    }
}
