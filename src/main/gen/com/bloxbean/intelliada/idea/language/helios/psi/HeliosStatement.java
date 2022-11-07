// This is a generated file. Not intended for manual editing.
package com.bloxbean.intelliada.idea.language.helios.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface HeliosStatement extends PsiElement {

  @Nullable
  HeliosConstStatement getConstStatement();

  @Nullable
  HeliosEnumStatement getEnumStatement();

  @Nullable
  HeliosFuncStatement getFuncStatement();

  @Nullable
  HeliosImportStatement getImportStatement();

  @Nullable
  HeliosStructStatement getStructStatement();

}
