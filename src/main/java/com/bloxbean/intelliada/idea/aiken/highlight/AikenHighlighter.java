package com.bloxbean.intelliada.idea.aiken.highlight;

import com.bloxbean.intelliada.idea.aiken.lang.AikenLexerAdapter;
import com.bloxbean.intelliada.idea.aiken.color.AikenColors;
import com.bloxbean.intelliada.idea.aiken.lang.psi.AikenTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class AikenHighlighter extends SyntaxHighlighterBase {
    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

    private static final TokenSet KEYWORDS = TokenSet.create(
        AikenTypes.IF,
        AikenTypes.ELSE,
        AikenTypes.WHEN,
        AikenTypes.IS,
        AikenTypes.FN,
        AikenTypes.USE,
        AikenTypes.LET,
        AikenTypes.PUB,
        AikenTypes.TYPE,
        AikenTypes.OPAQUE,
        AikenTypes.CONST,
        AikenTypes.TODO,
        AikenTypes.ASSERT,
        AikenTypes.CHECK,
        AikenTypes.TEST,
        AikenTypes.ERROR
    );

    private static final Map<IElementType, TextAttributesKey> keys = new HashMap<>();

    static {
//        fillMap(keys, OPERATIONS, TEALSyntaxColors.FUNCTION);
        fillMap(keys, KEYWORDS, AikenColors.KEYWORD);
        keys.put(AikenTypes.COMMENT, AikenColors.COMMENT);
        keys.put(AikenTypes.DOC_COMMENT, AikenColors.DOC_COMMENT);
        keys.put(AikenTypes.MODULE_COMMENT, AikenColors.MODULE_COMMENT);
        keys.put(AikenTypes.STRING_CONTENT, AikenColors.STRING);
        keys.put(AikenTypes.UPPER_IDENTIFIER, AikenColors.IDENTIFIER);
        keys.put(AikenTypes.TYPE_IDENTIFIER, AikenColors.IDENTIFIER);
    }

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new AikenLexerAdapter();
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        TextAttributesKey tak = keys.get(tokenType);
        return tak != null ? pack(keys.get(tokenType)) : EMPTY_KEYS;
    }
}
