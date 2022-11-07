package com.bloxbean.intelliada.idea.language.helios;

import com.bloxbean.intelliada.idea.common.CardanoIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class HeliosFileType extends LanguageFileType {

    public static final HeliosFileType INSTANCE = new HeliosFileType();

    private HeliosFileType() {
        super(HeliosLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "Helios File";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Helios language file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "hl";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return CardanoIcons.HELIOS_FILE_ICON;
    }

}
