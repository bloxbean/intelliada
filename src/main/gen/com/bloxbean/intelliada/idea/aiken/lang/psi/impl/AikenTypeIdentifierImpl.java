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

public class AikenTypeIdentifierImpl extends ASTWrapperPsiElement implements AikenTypeIdentifier {

  public AikenTypeIdentifierImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AikenVisitor visitor) {
    visitor.visitTypeIdentifier(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AikenVisitor) accept((AikenVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<AikenTypeIdentifierInnerType> getTypeIdentifierInnerTypeList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AikenTypeIdentifierInnerType.class);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

  @Override
  @NotNull
  public PsiElement getUpperIdentifier() {
    return findNotNullChildByType(UPPER_IDENTIFIER);
  }

}
