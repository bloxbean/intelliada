// This is a generated file. Not intended for manual editing.
package com.bloxbean.intelliada.idea.language.helios.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface HeliosEnumStatement extends PsiElement {

  @NotNull
  List<HeliosEnumMember> getEnumMemberList();

  @NotNull
  HeliosIdentifier getIdentifier();

  @Nullable
  HeliosImplDefinition getImplDefinition();

}
