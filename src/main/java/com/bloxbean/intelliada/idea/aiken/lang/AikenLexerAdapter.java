package com.bloxbean.intelliada.idea.aiken.lang;

import com.intellij.lexer.FlexAdapter;

public class AikenLexerAdapter extends FlexAdapter {

    public AikenLexerAdapter() {
        super(new _AikenLexer());
    }
}
