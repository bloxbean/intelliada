package com.bloxbean.intelliada.idea.language.helios.psi;

import com.bloxbean.intelliada.idea.language.helios.HeliosFileType;
import com.bloxbean.intelliada.idea.language.helios.HeliosLanguage;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public class HeliosFile extends PsiFileBase {
    public HeliosFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, HeliosLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return HeliosFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Helios File";
    }
}
