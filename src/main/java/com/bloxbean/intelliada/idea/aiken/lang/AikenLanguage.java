package com.bloxbean.intelliada.idea.aiken.lang;

import com.intellij.lang.Language;

public class AikenLanguage extends Language {
    public static final AikenLanguage INSTANCE = new AikenLanguage();

    protected AikenLanguage() {
        super("Aiken");
    }
}
