package com.bloxbean.intelliada.idea.aiken.highlight;

import com.bloxbean.intelliada.idea.aiken.lang.AikenLanguage;
import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.lang.Language;
import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.NotNull;

public class ParseErrorHighlightFilter extends HighlightErrorFilter {

    @Override
    public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
        Language language = element.getLanguage();
        if (language != AikenLanguage.INSTANCE)
            return true;
        else
            return false;
    }
}
