package com.bloxbean.intelliada.idea.aiken.index;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Information about an Aiken symbol for indexing and completion
 */
public class AikenSymbolInfo {
    private final String name;
    private final AikenSymbolType type;
    private final String filePath;
    private final boolean isPublic;
    private final String signature;

    public AikenSymbolInfo(@NotNull String name, 
                           @NotNull AikenSymbolType type, 
                           @NotNull String filePath, 
                           boolean isPublic, 
                           @Nullable String signature) {
        this.name = name;
        this.type = type;
        this.filePath = filePath;
        this.isPublic = isPublic;
        this.signature = signature;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public AikenSymbolType getType() {
        return type;
    }

    @NotNull
    public String getFilePath() {
        return filePath;
    }

    public boolean isPublic() {
        return isPublic;
    }

    @Nullable
    public String getSignature() {
        return signature;
    }

    @Override
    public String toString() {
        return "AikenSymbolInfo{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", filePath='" + filePath + '\'' +
                ", isPublic=" + isPublic +
                ", signature='" + signature + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AikenSymbolInfo that = (AikenSymbolInfo) o;

        if (isPublic != that.isPublic) return false;
        if (!name.equals(that.name)) return false;
        if (type != that.type) return false;
        if (!filePath.equals(that.filePath)) return false;
        return signature != null ? signature.equals(that.signature) : that.signature == null;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + filePath.hashCode();
        result = 31 * result + (isPublic ? 1 : 0);
        result = 31 * result + (signature != null ? signature.hashCode() : 0);
        return result;
    }
}