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

public class AikenFunctionStatementImpl extends ASTWrapperPsiElement implements AikenFunctionStatement {

  public AikenFunctionStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AikenVisitor visitor) {
    visitor.visitFunctionStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AikenVisitor) accept((AikenVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<AikenConstantStatement> getConstantStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AikenConstantStatement.class);
  }

  @Override
  @NotNull
  public List<AikenConstructorIdentifier> getConstructorIdentifierList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AikenConstructorIdentifier.class);
  }

  @Override
  @NotNull
  public List<AikenFunctionCall> getFunctionCallList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AikenFunctionCall.class);
  }

  @Override
  @Nullable
  public AikenFunctionStatementType getFunctionStatementType() {
    return findChildByClass(AikenFunctionStatementType.class);
  }

  @Override
  @NotNull
  public List<AikenTypeIdentifier> getTypeIdentifierList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AikenTypeIdentifier.class);
  }

  @Override
  @NotNull
  public List<AikenVariableStatement> getVariableStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AikenVariableStatement.class);
  }

  @Override
  @NotNull
  public PsiElement getIdentifier() {
    return findNotNullChildByType(IDENTIFIER);
  }

}
