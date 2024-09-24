// This is a generated file. Not intended for manual editing.
package com.bloxbean.intelliada.idea.aiken.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface AikenValidatorStatement extends PsiElement {

  @NotNull
  List<AikenConstantStatement> getConstantStatementList();

  @NotNull
  List<AikenConstructorIdentifier> getConstructorIdentifierList();

  @NotNull
  List<AikenFunctionCall> getFunctionCallList();

  @NotNull
  List<AikenTypeIdentifier> getTypeIdentifierList();

  @NotNull
  List<AikenVariableStatement> getVariableStatementList();

  @NotNull
  PsiElement getIdentifier();

}
