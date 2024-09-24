package com.bloxbean.intelliada.idea.aiken.lang.psi;

import com.bloxbean.intelliada.idea.aiken.lang.AikenLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AikenTokenType extends IElementType {
    public AikenTokenType(@NonNls @NotNull String debugName) {
        super(debugName, AikenLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "AikenTokeType." + super.toString();
    }
}
