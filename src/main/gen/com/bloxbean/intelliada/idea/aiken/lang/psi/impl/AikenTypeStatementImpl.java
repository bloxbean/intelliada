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

public class AikenTypeStatementImpl extends ASTWrapperPsiElement implements AikenTypeStatement {

  public AikenTypeStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AikenVisitor visitor) {
    visitor.visitTypeStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AikenVisitor) accept((AikenVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public AikenConstructorIdentifier getConstructorIdentifier() {
    return findChildByClass(AikenConstructorIdentifier.class);
  }

  @Override
  @Nullable
  public AikenTypeIdentifier getTypeIdentifier() {
    return findChildByClass(AikenTypeIdentifier.class);
  }

  @Override
  @NotNull
  public AikenTypeName getTypeName() {
    return findNotNullChildByClass(AikenTypeName.class);
  }

  @Override
  @NotNull
  public List<AikenTypeStatementConstructor> getTypeStatementConstructorList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AikenTypeStatementConstructor.class);
  }

  @Override
  @NotNull
  public List<AikenTypeStatementConstructorNoArg> getTypeStatementConstructorNoArgList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AikenTypeStatementConstructorNoArg.class);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

}
