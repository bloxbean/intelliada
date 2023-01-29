package com.bloxbean.intelliada.idea.aiken.filetypes;

import com.bloxbean.intelliada.idea.aiken.common.AikenIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AikenTomlFileType implements FileType {
    public static final AikenTomlFileType INSTANCE = new AikenTomlFileType();

    @NotNull
    @Override
    public String getName() {
        return "Aiken Project File";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Aiken Project File";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "toml";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return AikenIcons.FILE;
    }

    @Override
    public boolean isBinary() {
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public @Nullable String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
        return null;
    }
}
