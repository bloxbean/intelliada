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

public class AikenFunctionCallImpl extends ASTWrapperPsiElement implements AikenFunctionCall {

  public AikenFunctionCallImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AikenVisitor visitor) {
    visitor.visitFunctionCall(this);
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
  @NotNull
  public List<AikenFunctionCallParam> getFunctionCallParamList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AikenFunctionCallParam.class);
  }

}
