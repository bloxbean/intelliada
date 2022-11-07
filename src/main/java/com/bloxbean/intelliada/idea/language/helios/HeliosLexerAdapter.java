package com.bloxbean.intelliada.idea.language.helios;

import com.intellij.lexer.FlexAdapter;

public class HeliosLexerAdapter extends FlexAdapter {

    public HeliosLexerAdapter() {
        super(new _HeliosLexer(null));
    }
}
