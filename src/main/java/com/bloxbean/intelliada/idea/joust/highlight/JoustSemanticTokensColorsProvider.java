package com.bloxbean.intelliada.idea.joust.highlight;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensColorsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ADR-004 D2: maps JOUST's semantic token types ({@code namespace, type, enum, enumMember, property, parameter, variable,
 * function, method, class, keyword}) and modifiers ({@code declaration, readonly, defaultLibrary, timeValue}) to
 * IntelliAda's keys (F16). {@code timeValue} wins over the type, then {@code defaultLibrary} for functions.
 */
public final class JoustSemanticTokensColorsProvider implements SemanticTokensColorsProvider {

    @Override
    public @Nullable TextAttributesKey getTextAttributesKey(@NotNull String tokenType, @NotNull List<String> tokenModifiers,
                                                            @NotNull PsiFile file) {
        return keyFor(tokenType, tokenModifiers);
    }

    public static TextAttributesKey keyFor(String type, List<String> mods) {
        if (mods.contains("timeValue")) return JoustHighlighterColors.TIME_VALUE;
        boolean decl = mods.contains("declaration");
        boolean std = mods.contains("defaultLibrary");
        return switch (type) {
            case "namespace" -> JoustHighlighterColors.NAMESPACE;
            case "type" -> JoustHighlighterColors.TYPE;
            case "class" -> JoustHighlighterColors.CLASS;
            case "enum" -> JoustHighlighterColors.ENUM;
            case "enumMember" -> JoustHighlighterColors.ENUM_MEMBER;
            case "property" -> JoustHighlighterColors.STATE_FIELD;
            case "parameter" -> JoustHighlighterColors.PARAMETER;
            case "variable" -> mods.contains("readonly") ? JoustHighlighterColors.CONSTANT : JoustHighlighterColors.LOCAL_VARIABLE;
            case "function" -> std ? JoustHighlighterColors.STD_FUNCTION
                    : decl ? JoustHighlighterColors.FUNCTION_DECLARATION : JoustHighlighterColors.FUNCTION_CALL;
            case "method" -> std ? JoustHighlighterColors.STD_FUNCTION
                    : decl ? JoustHighlighterColors.FUNCTION_DECLARATION : JoustHighlighterColors.METHOD;
            case "keyword" -> JoustHighlighterColors.KEYWORD;
            default -> null;
        };
    }
}
