package com.bloxbean.intelliada.idea.joust.highlight;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/** ADR-004 D2: text attribute keys for JOUST's semantic tokens (F16), so themes can style them. */
public final class JoustHighlighterColors {
    private JoustHighlighterColors() {
    }

    public static final TextAttributesKey NAMESPACE = createTextAttributesKey("JOUST_NAMESPACE", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey TYPE = createTextAttributesKey("JOUST_TYPE", DefaultLanguageHighlighterColors.CLASS_REFERENCE);
    public static final TextAttributesKey CLASS = createTextAttributesKey("JOUST_CLASS", DefaultLanguageHighlighterColors.CLASS_NAME);
    public static final TextAttributesKey ENUM = createTextAttributesKey("JOUST_ENUM", DefaultLanguageHighlighterColors.CLASS_NAME);
    public static final TextAttributesKey ENUM_MEMBER = createTextAttributesKey("JOUST_ENUM_MEMBER", DefaultLanguageHighlighterColors.STATIC_FIELD);
    public static final TextAttributesKey STATE_FIELD = createTextAttributesKey("JOUST_STATE_FIELD", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey PARAMETER = createTextAttributesKey("JOUST_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER);
    public static final TextAttributesKey LOCAL_VARIABLE = createTextAttributesKey("JOUST_LOCAL_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
    public static final TextAttributesKey CONSTANT = createTextAttributesKey("JOUST_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT);
    public static final TextAttributesKey FUNCTION_DECLARATION = createTextAttributesKey("JOUST_FUNCTION_DECLARATION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
    public static final TextAttributesKey FUNCTION_CALL = createTextAttributesKey("JOUST_FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL);
    public static final TextAttributesKey METHOD = createTextAttributesKey("JOUST_METHOD", DefaultLanguageHighlighterColors.INSTANCE_METHOD);
    public static final TextAttributesKey STD_FUNCTION = createTextAttributesKey("JOUST_STD_FUNCTION", DefaultLanguageHighlighterColors.STATIC_METHOD);
    public static final TextAttributesKey KEYWORD = createTextAttributesKey("JOUST_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    /** ADR-0018 D11's time values (the {@code timeValue} modifier). */
    public static final TextAttributesKey TIME_VALUE = createTextAttributesKey("JOUST_TIME_VALUE", DefaultLanguageHighlighterColors.METADATA);
}
