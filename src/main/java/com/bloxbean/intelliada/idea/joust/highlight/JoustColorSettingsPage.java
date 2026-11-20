package com.bloxbean.intelliada.idea.joust.highlight;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/** Settings | Editor | Color Scheme | JOUST (.jsc): the semantic-token colours of ADR-004 D2. */
public final class JoustColorSettingsPage implements ColorSettingsPage {

    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("State field", JoustHighlighterColors.STATE_FIELD),
            new AttributesDescriptor("Parameter", JoustHighlighterColors.PARAMETER),
            new AttributesDescriptor("Local variable", JoustHighlighterColors.LOCAL_VARIABLE),
            new AttributesDescriptor("Constant", JoustHighlighterColors.CONSTANT),
            new AttributesDescriptor("Function declaration", JoustHighlighterColors.FUNCTION_DECLARATION),
            new AttributesDescriptor("Function call", JoustHighlighterColors.FUNCTION_CALL),
            new AttributesDescriptor("Method", JoustHighlighterColors.METHOD),
            new AttributesDescriptor("std function", JoustHighlighterColors.STD_FUNCTION),
            new AttributesDescriptor("Type", JoustHighlighterColors.TYPE),
            new AttributesDescriptor("Contract or record", JoustHighlighterColors.CLASS),
            new AttributesDescriptor("Enum", JoustHighlighterColors.ENUM),
            new AttributesDescriptor("Enum member", JoustHighlighterColors.ENUM_MEMBER),
            new AttributesDescriptor("Namespace", JoustHighlighterColors.NAMESPACE),
            new AttributesDescriptor("Keyword (semantic)", JoustHighlighterColors.KEYWORD),
            new AttributesDescriptor("Time value", JoustHighlighterColors.TIME_VALUE),
    };

    private static final Map<String, TextAttributesKey> TAGS = Map.ofEntries(
            Map.entry("field", JoustHighlighterColors.STATE_FIELD), Map.entry("param", JoustHighlighterColors.PARAMETER),
            Map.entry("local", JoustHighlighterColors.LOCAL_VARIABLE), Map.entry("const", JoustHighlighterColors.CONSTANT),
            Map.entry("fdecl", JoustHighlighterColors.FUNCTION_DECLARATION), Map.entry("fcall", JoustHighlighterColors.FUNCTION_CALL),
            Map.entry("method", JoustHighlighterColors.METHOD), Map.entry("std", JoustHighlighterColors.STD_FUNCTION),
            Map.entry("type", JoustHighlighterColors.TYPE), Map.entry("class", JoustHighlighterColors.CLASS),
            Map.entry("enum", JoustHighlighterColors.ENUM), Map.entry("member", JoustHighlighterColors.ENUM_MEMBER),
            Map.entry("ns", JoustHighlighterColors.NAMESPACE), Map.entry("kw", JoustHighlighterColors.KEYWORD),
            Map.entry("time", JoustHighlighterColors.TIME_VALUE));

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new PlainSyntaxHighlighter();
    }

    @Override
    public @NotNull String getDemoText() {
        return "contract <class>Counter</class>(<param>maxDatumBytes</param> = 128) {\n"
                + "  state { <field>count</field>: <type>Int</type>, <field>owner</field>: <type>Key</type> }\n"
                + "  action <fdecl>increment</fdecl>(<param>by</param>: <type>Int</type>) {\n"
                + "    let <local>next</local> = <field>count</field> + <param>by</param>\n"
                + "    require(<param>by</param> > 0, \"increment must be positive\")\n"
                + "    require(<std>now</std>() < <time>deadline</time>, \"in time\")\n"
                + "    return { ...state, <field>count</field>: <local>next</local> }\n"
                + "  }\n"
                + "  view <fdecl>count</fdecl>(): <type>Int</type> = <field>count</field>\n"
                + "}\n";
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return TAGS;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "JOUST (.jsc)";
    }
}
