package com.bloxbean.intelliada.idea.joust;

import com.bloxbean.intelliada.idea.joust.textmate.JoustTextMateBundleProvider;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider;

import java.util.HashSet;
import java.util.Set;

/** ADR-004 M1: a .jsc file is a TextMate file whose tokens get the bundled grammar's scopes (D2, F10, F12). */
public class JoustTextMatePlatformTest extends BasePlatformTestCase {

    public void testJscGetsTextMateTokenAttributes() {
        assertTrue("IntelliAda's bundle provider is registered through the optional TextMate dependency",
                TextMateBundleProvider.Companion.getEP_NAME().getExtensionList().stream().anyMatch(p -> p instanceof JoustTextMateBundleProvider));
        TextMateService.getInstance().reloadEnabledBundles();
        PlatformTestUtil.waitWithEventsDispatching("the .jsc grammar registers",
                () -> TextMateService.getInstance().getLanguageDescriptorByExtension("jsc") != null, 30);
        myFixture.configureByText("Counter.jsc", "jsc 1\npackage app\n\n// a comment\ncontract Counter {\n  state { count: Int }\n"
                + "  view label(): String = \"text\"\n}\n");
        assertEquals("a TextMate file (TextMate's flat PSI, no IntelliAda language)", "textmate",
                myFixture.getFile().getLanguage().getID().toLowerCase());
        EditorHighlighter h = ((EditorEx) myFixture.getEditor()).getHighlighter();
        Set<String> keys = new HashSet<>();
        for (HighlighterIterator it = h.createIterator(0); !it.atEnd(); it.advance()) {
            for (var k : it.getTextAttributesKeys()) keys.add(k.getExternalName());
        }
        assertTrue("TextMate scopes give comment, string and keyword attributes: " + keys,
                keys.stream().anyMatch(k -> k.toLowerCase().contains("comment"))
                        && keys.stream().anyMatch(k -> k.toLowerCase().contains("string"))
                        && keys.stream().anyMatch(k -> k.toLowerCase().contains("keyword")));
    }
}
