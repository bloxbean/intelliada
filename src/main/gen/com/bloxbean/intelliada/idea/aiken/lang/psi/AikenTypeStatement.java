// This is a generated file. Not intended for manual editing.
package com.bloxbean.intelliada.idea.aiken.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface AikenTypeStatement extends PsiElement {

  @Nullable
  AikenConstructorIdentifier getConstructorIdentifier();

  @Nullable
  AikenTypeIdentifier getTypeIdentifier();

  @NotNull
  AikenTypeName getTypeName();

  @NotNull
  List<AikenTypeStatementConstructor> getTypeStatementConstructorList();

  @NotNull
  List<AikenTypeStatementConstructorNoArg> getTypeStatementConstructorNoArgList();

  @Nullable
  PsiElement getIdentifier();

}
