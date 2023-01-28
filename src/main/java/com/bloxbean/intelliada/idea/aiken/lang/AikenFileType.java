package com.bloxbean.intelliada.idea.aiken.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AikenFileType extends LanguageFileType {

    public static final AikenFileType INSTANCE = new AikenFileType();

    private AikenFileType() {
        super(AikenLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "Aiken";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Aiken language file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "ak";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return AikenIcons.FILE;
    }

}
