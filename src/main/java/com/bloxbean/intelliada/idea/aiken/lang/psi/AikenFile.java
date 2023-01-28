package com.bloxbean.intelliada.idea.aiken.lang.psi;

import com.bloxbean.intelliada.idea.aiken.lang.AikenFileType;
import com.bloxbean.intelliada.idea.aiken.lang.AikenLanguage;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public class AikenFile extends PsiFileBase {
    public AikenFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, AikenLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return AikenFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Aiken File";
    }
}
