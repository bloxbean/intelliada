package com.bloxbean.intelliada.idea.aiken.lang.psi;

import com.bloxbean.intelliada.idea.aiken.lang.AikenLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AikenElementType extends IElementType {
    public AikenElementType(@NotNull @NonNls String debugName) {
        super(debugName, AikenLanguage.INSTANCE);
    }
}
