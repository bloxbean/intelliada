// This is a generated file. Not intended for manual editing.
package com.bloxbean.intelliada.idea.aiken.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.bloxbean.intelliada.idea.aiken.lang.psi.AikenTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.bloxbean.intelliada.idea.aiken.lang.psi.*;

public class AikenVariableValueImpl extends ASTWrapperPsiElement implements AikenVariableValue {

  public AikenVariableValueImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AikenVisitor visitor) {
    visitor.visitVariableValue(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AikenVisitor) accept((AikenVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public AikenFunctionCall getFunctionCall() {
    return findChildByClass(AikenFunctionCall.class);
  }

  @Override
  @Nullable
  public AikenTypeIdentifier getTypeIdentifier() {
    return findChildByClass(AikenTypeIdentifier.class);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

  @Override
  @Nullable
  public PsiElement getNumber() {
    return findChildByType(NUMBER);
  }

  @Override
  @Nullable
  public PsiElement getStringContent() {
    return findChildByType(STRING_CONTENT);
  }

  @Override
  @Nullable
  public PsiElement getUpperIdentifier() {
    return findChildByType(UPPER_IDENTIFIER);
  }

}
