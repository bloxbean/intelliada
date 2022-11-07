package com.bloxbean.intelliada.idea.language.helios.psi;

import com.bloxbean.intelliada.idea.language.helios.HeliosLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class HeliosElementType extends IElementType {
    public HeliosElementType(@NotNull @NonNls String debugName) {
        super(debugName, HeliosLanguage.INSTANCE);
    }
}
