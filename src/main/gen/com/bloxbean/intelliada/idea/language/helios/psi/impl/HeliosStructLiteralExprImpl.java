// This is a generated file. Not intended for manual editing.
package com.bloxbean.intelliada.idea.language.helios.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.bloxbean.intelliada.idea.language.helios.psi.HeliosTypes.*;
import com.bloxbean.intelliada.idea.language.helios.psi.*;

public class HeliosStructLiteralExprImpl extends HeliosValueExprImpl implements HeliosStructLiteralExpr {

  public HeliosStructLiteralExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull HeliosVisitor visitor) {
    visitor.visitStructLiteralExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof HeliosVisitor) accept((HeliosVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<HeliosStructLiteralField> getStructLiteralFieldList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, HeliosStructLiteralField.class);
  }

  @Override
  @NotNull
  public HeliosValueExpr getValueExpr() {
    return findNotNullChildByClass(HeliosValueExpr.class);
  }

}
