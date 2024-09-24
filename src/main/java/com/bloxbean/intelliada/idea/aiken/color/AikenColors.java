package com.bloxbean.intelliada.idea.aiken.color;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class AikenColors {
    public static final TextAttributesKey COMMENT =
            createTextAttributesKey("Comment//Comment", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey DOC_COMMENT =
            createTextAttributesKey("Comment//Documentation", DefaultLanguageHighlighterColors.DOC_COMMENT);
    public static final TextAttributesKey MODULE_COMMENT =
            createTextAttributesKey("Comment//Module Documentation", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey KEYWORD =
            createTextAttributesKey("Keyword", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey STRING =
            createTextAttributesKey("String", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey IDENTIFIER =
            createTextAttributesKey("Identifier//Type", DefaultLanguageHighlighterColors.CLASS_NAME);
    public static final TextAttributesKey TYPE_NAME =
            createTextAttributesKey("TypeName", DefaultLanguageHighlighterColors.CLASS_NAME);
}
