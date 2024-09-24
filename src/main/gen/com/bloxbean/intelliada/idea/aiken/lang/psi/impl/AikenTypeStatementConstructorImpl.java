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

public class AikenTypeStatementConstructorImpl extends ASTWrapperPsiElement implements AikenTypeStatementConstructor {

  public AikenTypeStatementConstructorImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AikenVisitor visitor) {
    visitor.visitTypeStatementConstructor(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AikenVisitor) accept((AikenVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<AikenConstructorIdentifier> getConstructorIdentifierList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AikenConstructorIdentifier.class);
  }

  @Override
  @NotNull
  public AikenConstructorName getConstructorName() {
    return findNotNullChildByClass(AikenConstructorName.class);
  }

  @Override
  @NotNull
  public List<AikenTypeIdentifier> getTypeIdentifierList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AikenTypeIdentifier.class);
  }

}
