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
            createTextAttributesKey("Keyword", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey IDENTIFIER =
            createTextAttributesKey("Keyword", DefaultLanguageHighlighterColors.CLASS_NAME);

//
//    DOC_COMMENT(AikenBundle.messagePointer("settings.Aiken.color.comment.doc"), DefaultLanguageHighlighterColors.DOC_COMMENT),
//    MODULE_COMMENT(AikenBundle.messagePointer("settings.Aiken.color.comment.mod_doc"), DefaultLanguageHighlighterColors.LINE_COMMENT),
//
//    KEYWORD(AikenBundle.messagePointer("settings.Aiken.color.keyword"), DefaultLanguageHighlighterColors.KEYWORD),
//
//    STRING(AikenBundle.messagePointer("settings.Aiken.color.string"), DefaultLanguageHighlighterColors.STRING),
//
//    TYPE_IDENTIFIER(AikenBundle.messagePointer("settings.Aiken.color.identifier.type"), DefaultLanguageHighlighterColors.CLASS_NAME),
//    ;
//
//    val textAttributesKey = TextAttributesKey.createTextAttributesKey("run.Aiken.$name", default)
//    val attributesDescriptor = AttributesDescriptor(humanName, textAttributesKey)
//    val testSeverity: HighlightSeverity = HighlightSeverity(name, HighlightSeverity.INFORMATION.myVal)
}
