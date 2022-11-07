package com.bloxbean.intelliada.idea.language.helios;

import com.intellij.lang.Language;

public class HeliosLanguage extends Language {
    public static final HeliosLanguage INSTANCE = new HeliosLanguage();

    private HeliosLanguage() {
        super("Helios");
    }
}
