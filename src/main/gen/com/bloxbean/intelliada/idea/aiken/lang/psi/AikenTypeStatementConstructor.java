// This is a generated file. Not intended for manual editing.
package com.bloxbean.intelliada.idea.aiken.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface AikenTypeStatementConstructor extends PsiElement {

  @NotNull
  List<AikenConstructorIdentifier> getConstructorIdentifierList();

  @NotNull
  AikenConstructorName getConstructorName();

  @NotNull
  List<AikenTypeIdentifier> getTypeIdentifierList();

}
