package com.bloxbean.intelliada.idea.language.helios.psi;

import com.bloxbean.intelliada.idea.language.helios.HeliosLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class HeliosTokenType extends IElementType {
    public HeliosTokenType(@NonNls @NotNull String debugName) {
        super(debugName, HeliosLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "HeliosTokeType." + super.toString();
    }
}
