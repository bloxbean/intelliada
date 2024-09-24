package com.bloxbean.intelliada.idea.aiken.lang;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.bloxbean.intelliada.idea.aiken.lang.psi.AikenTypes.*;

%%

%{
  public _AikenLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _AikenLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

IDENTIFIER=[_a-z][_0-9a-z]*
UPPER_IDENTIFIER=[A-Z][_0-9a-zA-Z]*
COMMENT="//"([^\n]*)?
DOC_COMMENT="///"([^\n]*)?
MODULE_COMMENT="////"([^\n]*)?
NUMBER=[0-9]*
STRING_CONTENT=\"([^\\\\\\\"]|\\\\[^efnrt\\\"\\\\])+\"

%%
<YYINITIAL> {
  {WHITE_SPACE}           { return WHITE_SPACE; }

  "if"                   { return IF; }
  "else"                   { return ELSE; }
  "when"                   { return WHEN; }
  "is"                   { return IS; }
  "pub"                   { return PUB; }
  "fn"                    { return FN; }
  "use"                   { return USE; }
  "let"                   { return LET; }
  "pub"                   { return PUB; }
  "type"                  { return TYPE; }
  "opaque"                { return OPAQUE; }
  "const"                 { return CONST; }
  "todo"                  { return TODO; }
  "expect"                { return EXPECT; }
  "check"                 { return CHECK; }
  "test"                  { return TEST; }
  "trace"                 { return TRACE; }
  "fail"                  { return FAIL; }
  "validator"             { return VALIDATOR; }
  "and"                   { return AND; }
  "or"                    { return OR; }
  "as"                    { return AS; }
  "via"                   { return VIA; }


  "{"                     { return LBRACE; }
  "}"                     { return RBRACE; }
  "["                     { return LBRACK; }
  "]"                     { return RBRACK; }
  "("                     { return LPAREN; }
  ")"                     { return RPAREN; }
  ":"                     { return COLON; }
  ","                     { return COMMA; }
  "="                     { return EQ; }
  "=="                    { return EQEQ; }
  "!"                     { return BANG; }
  "+"                     { return PLUS; }
  "-"                     { return MINUS; }
  "||"                    { return OR; }
  "&&"                    { return AND; }
  "<"                     { return LT; }
  ">"                     { return GT; }
  "*"                     { return MUL; }
  "/"                     { return DIV; }
  "//"                    { return DIVDIV; }
  "."                     { return DOT; }
  ".."                    { return DOTDOT; }
  "=>"                    { return FAT_ARROW; }
  "->"                    { return ARROW; }
  "\""                    { return QUOTE; }
  "|>"                    { return PIPE; }

  {IDENTIFIER}            { return IDENTIFIER; }
  {UPPER_IDENTIFIER}      { return UPPER_IDENTIFIER; }
  {COMMENT}               { return COMMENT; }
  {DOC_COMMENT}           { return DOC_COMMENT; }
  {MODULE_COMMENT}        { return MODULE_COMMENT; }
  {NUMBER}                { return NUMBER; }
  {STRING_CONTENT}        { return STRING_CONTENT; }

}

[^] { return BAD_CHARACTER; }
