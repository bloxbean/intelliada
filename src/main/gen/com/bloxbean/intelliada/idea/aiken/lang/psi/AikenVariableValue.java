// This is a generated file. Not intended for manual editing.
package com.bloxbean.intelliada.idea.aiken.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface AikenVariableValue extends PsiElement {

  @Nullable
  AikenFunctionCall getFunctionCall();

  @Nullable
  AikenTypeIdentifier getTypeIdentifier();

  @Nullable
  PsiElement getIdentifier();

  @Nullable
  PsiElement getNumber();

  @Nullable
  PsiElement getStringContent();

  @Nullable
  PsiElement getUpperIdentifier();

}