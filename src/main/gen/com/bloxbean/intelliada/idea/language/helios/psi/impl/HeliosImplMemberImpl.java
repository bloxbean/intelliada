// This is a generated file. Not intended for manual editing.
package com.bloxbean.intelliada.idea.language.helios.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.bloxbean.intelliada.idea.language.helios.psi.HeliosTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.bloxbean.intelliada.idea.language.helios.psi.*;

public class HeliosImplMemberImpl extends ASTWrapperPsiElement implements HeliosImplMember {

  public HeliosImplMemberImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull HeliosVisitor visitor) {
    visitor.visitImplMember(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof HeliosVisitor) accept((HeliosVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public HeliosConstStatement getConstStatement() {
    return findChildByClass(HeliosConstStatement.class);
  }

  @Override
  @Nullable
  public HeliosFuncStatement getFuncStatement() {
    return findChildByClass(HeliosFuncStatement.class);
  }

}
