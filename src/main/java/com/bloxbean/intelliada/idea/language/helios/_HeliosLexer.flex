package com.bloxbean.intelliada.idea.language.helios;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.bloxbean.intelliada.idea.language.helios.psi.HeliosTypes.*;

%%

%{
  public _HeliosLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _HeliosLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

NL=\r\n
EOF=\Z
SPACE=[ \t\n\x0B\f\r]+
PROGRAM_TYPES_CODE=(testing|minting|spending|staking|module)
LINE_COMMENT="//".*\n
BLOCK_COMMENT="/"\*(.|\n)*\*"/"
I_LITERAL=[0-9]+ | 0b[0-1]+ | 0o[0-7]+ | 0x[0-9a-f]+
B_ARRAYLITERAL=# "/"[0-9a-f]*"/"
T_WORD=[a-zA-Z_][0-9a-zA-Z_]*

%%
<YYINITIAL> {
  {WHITE_SPACE}             { return WHITE_SPACE; }

  "if"                      { return IF; }
  "else"                    { return ELSE; }
  "func"                    { return FUNC; }
 // "ListLiteralExpr"         { return LISTLITERALEXPR; }
  "FuncArc"                 { return FUNCARC; }
  "Option"                  { return OPTION; }

  {NL}                      { return NL; }
  {EOF}                     { return EOF; }
  {SPACE}                   { return SPACE; }
  {PROGRAM_TYPES_CODE}      { return PROGRAM_TYPES_CODE; }
  {LINE_COMMENT}            { return LINE_COMMENT; }
  {BLOCK_COMMENT}           { return BLOCK_COMMENT; }
  {I_LITERAL}               { return I_LITERAL; }
  {B_ARRAYLITERAL}          { return B_ARRAYLITERAL; }
  {T_WORD}                  { return T_WORD; }

}

[^] { return BAD_CHARACTER; }
