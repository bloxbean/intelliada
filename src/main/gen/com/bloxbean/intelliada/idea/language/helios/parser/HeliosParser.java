// This is a generated file. Not intended for manual editing.
package com.bloxbean.intelliada.idea.language.helios.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.bloxbean.intelliada.idea.language.helios.psi.HeliosTypes.*;
import static com.bloxbean.intelliada.idea.language.helios.psi.impl.HeliosParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class HeliosParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, EXTENDS_SETS_);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return Program(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ASSIGN_EXPR, BINARY_EXPR, CALL_EXPR, FUNC_LITERAL_EXPR,
      FUNC_TYPE_EXPR, IF_ELSE_EXPR, LIST_LITERAL_EXPR, LIST_TYPE_EXPR,
      LITERAL_EXPR, MAP_LITERAL_EXPR, MAP_TYPE_EXPR, MEMBER_EXPR,
      NON_FUNC_TYPE_EXPR, OPTION_TYPE_EXPR, PARENS_EXPR, PRIMITIVE_LITERAL_EXPR,
      PRINT_EXPR, STRUCT_LITERAL_EXPR, SWITCH_EXPR, TYPE_EXPR,
      TYPE_PATH_EXPR, TYPE_REF_EXPR, UNARY_EXPR, VALUE_EXPR,
      VALUE_PATH_EXPR, VALUE_REF_EXPR),
  };

  /* ********************************************************** */
  // '+' | '-' | '*' | '/' | '%' | '==' | '!=' | '<' | '>' | '<=' | '>=' | '||' | '&&'
  public static boolean BinaryOp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "BinaryOp")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BINARY_OP, "<binary op>");
    r = consumeToken(b, "+");
    if (!r) r = consumeToken(b, "-");
    if (!r) r = consumeToken(b, "*");
    if (!r) r = consumeToken(b, "/");
    if (!r) r = consumeToken(b, "%");
    if (!r) r = consumeToken(b, "==");
    if (!r) r = consumeToken(b, "!=");
    if (!r) r = consumeToken(b, "<");
    if (!r) r = consumeToken(b, ">");
    if (!r) r = consumeToken(b, "<=");
    if (!r) r = consumeToken(b, ">=");
    if (!r) r = consumeToken(b, "||");
    if (!r) r = consumeToken(b, "&&");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // 'true' | 'false'
  public static boolean BoolLiteral(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "BoolLiteral")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BOOL_LITERAL, "<bool literal>");
    r = consumeToken(b, "true");
    if (!r) r = consumeToken(b, "false");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '#' 'regexp:[0-9a-f]*'
  public static boolean ByteArrayLiteral(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ByteArrayLiteral")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BYTE_ARRAY_LITERAL, "<byte array literal>");
    r = consumeToken(b, "#");
    r = r && consumeToken(b, "regexp:[0-9a-f]*");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // 'regexp://.*\n' | 'regexp:/\*(.*|\n)\*/'
  public static boolean Comment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Comment")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, COMMENT, "<comment>");
    r = consumeToken(b, LINE_COMMENT);
    if (!r) r = consumeToken(b, "regexp:/\\*(.*|\\n)\\*/");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // 'const' Identifier [':' TypeExpr] '=' ValueExpr
  public static boolean ConstStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ConstStatement")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CONST_STATEMENT, "<const statement>");
    r = consumeToken(b, "const");
    r = r && Identifier(b, l + 1);
    r = r && ConstStatement_2(b, l + 1);
    r = r && consumeToken(b, "=");
    r = r && ValueExpr(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [':' TypeExpr]
  private static boolean ConstStatement_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ConstStatement_2")) return false;
    ConstStatement_2_0(b, l + 1);
    return true;
  }

  // ':' TypeExpr
  private static boolean ConstStatement_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ConstStatement_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ":");
    r = r && TypeExpr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // DataField (DataField)*
  public static boolean DataDefinition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "DataDefinition")) return false;
    if (!nextTokenIs(b, T_WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = DataField(b, l + 1);
    r = r && DataDefinition_1(b, l + 1);
    exit_section_(b, m, DATA_DEFINITION, r);
    return r;
  }

  // (DataField)*
  private static boolean DataDefinition_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "DataDefinition_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!DataDefinition_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "DataDefinition_1", c)) break;
    }
    return true;
  }

  // (DataField)
  private static boolean DataDefinition_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "DataDefinition_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = DataField(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // NameTypePair
  public static boolean DataField(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "DataField")) return false;
    if (!nextTokenIs(b, T_WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = NameTypePair(b, l + 1);
    exit_section_(b, m, DATA_FIELD, r);
    return r;
  }

  /* ********************************************************** */
  // Word ['{' DataDefinition '}']
  public static boolean EnumMember(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "EnumMember")) return false;
    if (!nextTokenIs(b, T_WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = Word(b, l + 1);
    r = r && EnumMember_1(b, l + 1);
    exit_section_(b, m, ENUM_MEMBER, r);
    return r;
  }

  // ['{' DataDefinition '}']
  private static boolean EnumMember_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "EnumMember_1")) return false;
    EnumMember_1_0(b, l + 1);
    return true;
  }

  // '{' DataDefinition '}'
  private static boolean EnumMember_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "EnumMember_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "{");
    r = r && DataDefinition(b, l + 1);
    r = r && consumeToken(b, "}");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'enum' Identifier '{' EnumMember (EnumMember)* [ImplDefinition] '}'
  public static boolean EnumStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "EnumStatement")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ENUM_STATEMENT, "<enum statement>");
    r = consumeToken(b, "enum");
    r = r && Identifier(b, l + 1);
    r = r && consumeToken(b, "{");
    r = r && EnumMember(b, l + 1);
    r = r && EnumStatement_4(b, l + 1);
    r = r && EnumStatement_5(b, l + 1);
    r = r && consumeToken(b, "}");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (EnumMember)*
  private static boolean EnumStatement_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "EnumStatement_4")) return false;
    while (true) {
      int c = current_position_(b);
      if (!EnumStatement_4_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "EnumStatement_4", c)) break;
    }
    return true;
  }

  // (EnumMember)
  private static boolean EnumStatement_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "EnumStatement_4_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = EnumMember(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [ImplDefinition]
  private static boolean EnumStatement_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "EnumStatement_5")) return false;
    ImplDefinition(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // NameTypePair
  public static boolean FuncArg(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncArg")) return false;
    if (!nextTokenIs(b, T_WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = NameTypePair(b, l + 1);
    exit_section_(b, m, FUNC_ARG, r);
    return r;
  }

  /* ********************************************************** */
  // '(' [FuncArg (',' FuncArc)*] ')' '->' TypeExpr '{' ValueExpr '}'
  public static boolean FuncLiteralExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncLiteralExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FUNC_LITERAL_EXPR, "<func literal expr>");
    r = consumeToken(b, "(");
    r = r && FuncLiteralExpr_1(b, l + 1);
    r = r && consumeToken(b, ")");
    r = r && consumeToken(b, "->");
    r = r && TypeExpr(b, l + 1);
    r = r && consumeToken(b, "{");
    r = r && ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, "}");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [FuncArg (',' FuncArc)*]
  private static boolean FuncLiteralExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncLiteralExpr_1")) return false;
    FuncLiteralExpr_1_0(b, l + 1);
    return true;
  }

  // FuncArg (',' FuncArc)*
  private static boolean FuncLiteralExpr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncLiteralExpr_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = FuncArg(b, l + 1);
    r = r && FuncLiteralExpr_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (',' FuncArc)*
  private static boolean FuncLiteralExpr_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncLiteralExpr_1_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!FuncLiteralExpr_1_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "FuncLiteralExpr_1_0_1", c)) break;
    }
    return true;
  }

  // ',' FuncArc
  private static boolean FuncLiteralExpr_1_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncLiteralExpr_1_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && consumeToken(b, FUNCARC);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // FUNC Identifier '(' [('self' | FuncArg) (',' FuncArg)*] ')' '->' TypeExpr '{' ValueExpr '}'
  public static boolean FuncStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncStatement")) return false;
    if (!nextTokenIs(b, FUNC)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FUNC);
    r = r && Identifier(b, l + 1);
    r = r && consumeToken(b, "(");
    r = r && FuncStatement_3(b, l + 1);
    r = r && consumeToken(b, ")");
    r = r && consumeToken(b, "->");
    r = r && TypeExpr(b, l + 1);
    r = r && consumeToken(b, "{");
    r = r && ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, "}");
    exit_section_(b, m, FUNC_STATEMENT, r);
    return r;
  }

  // [('self' | FuncArg) (',' FuncArg)*]
  private static boolean FuncStatement_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncStatement_3")) return false;
    FuncStatement_3_0(b, l + 1);
    return true;
  }

  // ('self' | FuncArg) (',' FuncArg)*
  private static boolean FuncStatement_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncStatement_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = FuncStatement_3_0_0(b, l + 1);
    r = r && FuncStatement_3_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // 'self' | FuncArg
  private static boolean FuncStatement_3_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncStatement_3_0_0")) return false;
    boolean r;
    r = consumeToken(b, "self");
    if (!r) r = FuncArg(b, l + 1);
    return r;
  }

  // (',' FuncArg)*
  private static boolean FuncStatement_3_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncStatement_3_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!FuncStatement_3_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "FuncStatement_3_0_1", c)) break;
    }
    return true;
  }

  // ',' FuncArg
  private static boolean FuncStatement_3_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncStatement_3_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && FuncArg(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '(' [TypeExpr (',' TypeExpr)*] ')' '->' TypeExpr
  public static boolean FuncTypeExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncTypeExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FUNC_TYPE_EXPR, "<func type expr>");
    r = consumeToken(b, "(");
    r = r && FuncTypeExpr_1(b, l + 1);
    r = r && consumeToken(b, ")");
    r = r && consumeToken(b, "->");
    r = r && TypeExpr(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [TypeExpr (',' TypeExpr)*]
  private static boolean FuncTypeExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncTypeExpr_1")) return false;
    FuncTypeExpr_1_0(b, l + 1);
    return true;
  }

  // TypeExpr (',' TypeExpr)*
  private static boolean FuncTypeExpr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncTypeExpr_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = TypeExpr(b, l + 1);
    r = r && FuncTypeExpr_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (',' TypeExpr)*
  private static boolean FuncTypeExpr_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncTypeExpr_1_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!FuncTypeExpr_1_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "FuncTypeExpr_1_0_1", c)) break;
    }
    return true;
  }

  // ',' TypeExpr
  private static boolean FuncTypeExpr_1_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FuncTypeExpr_1_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && TypeExpr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // Word
  public static boolean Identifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Identifier")) return false;
    if (!nextTokenIs(b, T_WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = Word(b, l + 1);
    exit_section_(b, m, IDENTIFIER, r);
    return r;
  }

  /* ********************************************************** */
  // ImplMember (ImplMember)*
  public static boolean ImplDefinition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ImplDefinition")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, IMPL_DEFINITION, "<impl definition>");
    r = ImplMember(b, l + 1);
    r = r && ImplDefinition_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (ImplMember)*
  private static boolean ImplDefinition_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ImplDefinition_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!ImplDefinition_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "ImplDefinition_1", c)) break;
    }
    return true;
  }

  // (ImplMember)
  private static boolean ImplDefinition_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ImplDefinition_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ImplMember(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ConstStatement | FuncStatement
  public static boolean ImplMember(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ImplMember")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, IMPL_MEMBER, "<impl member>");
    r = ConstStatement(b, l + 1);
    if (!r) r = FuncStatement(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // Word ['as' Word]
  public static boolean ImportField(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ImportField")) return false;
    if (!nextTokenIs(b, T_WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = Word(b, l + 1);
    r = r && ImportField_1(b, l + 1);
    exit_section_(b, m, IMPORT_FIELD, r);
    return r;
  }

  // ['as' Word]
  private static boolean ImportField_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ImportField_1")) return false;
    ImportField_1_0(b, l + 1);
    return true;
  }

  // 'as' Word
  private static boolean ImportField_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ImportField_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "as");
    r = r && Word(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'import' '{' ImportField (',' ImportField)* '}' 'from' ModuleName
  public static boolean ImportStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ImportStatement")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, IMPORT_STATEMENT, "<import statement>");
    r = consumeToken(b, "import");
    r = r && consumeToken(b, "{");
    r = r && ImportField(b, l + 1);
    r = r && ImportStatement_3(b, l + 1);
    r = r && consumeToken(b, "}");
    r = r && consumeToken(b, "from");
    r = r && ModuleName(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (',' ImportField)*
  private static boolean ImportStatement_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ImportStatement_3")) return false;
    while (true) {
      int c = current_position_(b);
      if (!ImportStatement_3_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "ImportStatement_3", c)) break;
    }
    return true;
  }

  // ',' ImportField
  private static boolean ImportStatement_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ImportStatement_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && ImportField(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'regexp:[0-9]+' | 'regexp:0b[0-1]+' | 'regexp:0o[0-7]+' | 'regexp:0x[0-9a-f]+'
  public static boolean IntLiteral(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IntLiteral")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, INT_LITERAL, "<int literal>");
    r = consumeToken(b, "regexp:[0-9]+");
    if (!r) r = consumeToken(b, "regexp:0b[0-1]+");
    if (!r) r = consumeToken(b, "regexp:0o[0-7]+");
    if (!r) r = consumeToken(b, "regexp:0x[0-9a-f]+");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '[]' TypeExpr '{' [ValueExpr] (',' ValueExpr)* '}'
  public static boolean ListLiteralExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ListLiteralExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LIST_LITERAL_EXPR, "<list literal expr>");
    r = consumeToken(b, "[]");
    r = r && TypeExpr(b, l + 1);
    r = r && consumeToken(b, "{");
    r = r && ListLiteralExpr_3(b, l + 1);
    r = r && ListLiteralExpr_4(b, l + 1);
    r = r && consumeToken(b, "}");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [ValueExpr]
  private static boolean ListLiteralExpr_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ListLiteralExpr_3")) return false;
    ValueExpr(b, l + 1, -1);
    return true;
  }

  // (',' ValueExpr)*
  private static boolean ListLiteralExpr_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ListLiteralExpr_4")) return false;
    while (true) {
      int c = current_position_(b);
      if (!ListLiteralExpr_4_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "ListLiteralExpr_4", c)) break;
    }
    return true;
  }

  // ',' ValueExpr
  private static boolean ListLiteralExpr_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ListLiteralExpr_4_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && ValueExpr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '[' ']' NonFuncTypeExpr
  public static boolean ListTypeExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ListTypeExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LIST_TYPE_EXPR, "<list type expr>");
    r = consumeToken(b, "[");
    r = r && consumeToken(b, "]");
    r = r && NonFuncTypeExpr(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // 'Map' '[' TypeExpr ']' TypeExpr '{' [ValueExpr ':' ValueExpr] (',' ValueExpr ':' ValueExpr)* '}'
  public static boolean MapLiteralExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MapLiteralExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MAP_LITERAL_EXPR, "<map literal expr>");
    r = consumeToken(b, "Map");
    r = r && consumeToken(b, "[");
    r = r && TypeExpr(b, l + 1);
    r = r && consumeToken(b, "]");
    r = r && TypeExpr(b, l + 1);
    r = r && consumeToken(b, "{");
    r = r && MapLiteralExpr_6(b, l + 1);
    r = r && MapLiteralExpr_7(b, l + 1);
    r = r && consumeToken(b, "}");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [ValueExpr ':' ValueExpr]
  private static boolean MapLiteralExpr_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MapLiteralExpr_6")) return false;
    MapLiteralExpr_6_0(b, l + 1);
    return true;
  }

  // ValueExpr ':' ValueExpr
  private static boolean MapLiteralExpr_6_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MapLiteralExpr_6_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, ":");
    r = r && ValueExpr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (',' ValueExpr ':' ValueExpr)*
  private static boolean MapLiteralExpr_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MapLiteralExpr_7")) return false;
    while (true) {
      int c = current_position_(b);
      if (!MapLiteralExpr_7_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "MapLiteralExpr_7", c)) break;
    }
    return true;
  }

  // ',' ValueExpr ':' ValueExpr
  private static boolean MapLiteralExpr_7_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MapLiteralExpr_7_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, ":");
    r = r && ValueExpr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // 'Map' '[' NonFuncTypeExpr ']' NonFuncTypeExpr
  public static boolean MapTypeExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MapTypeExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MAP_TYPE_EXPR, "<map type expr>");
    r = consumeToken(b, "Map");
    r = r && consumeToken(b, "[");
    r = r && NonFuncTypeExpr(b, l + 1);
    r = r && consumeToken(b, "]");
    r = r && NonFuncTypeExpr(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // ValueExpr '.' CallExpr
  static boolean MethodExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MethodExpr")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, ".");
    r = r && ValueExpr(b, l + 1, 4);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // Word
  public static boolean ModuleName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ModuleName")) return false;
    if (!nextTokenIs(b, T_WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = Word(b, l + 1);
    exit_section_(b, m, MODULE_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // Word ':' TypeExpr
  public static boolean NameTypePair(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "NameTypePair")) return false;
    if (!nextTokenIs(b, T_WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = Word(b, l + 1);
    r = r && consumeToken(b, ":");
    r = r && TypeExpr(b, l + 1);
    exit_section_(b, m, NAME_TYPE_PAIR, r);
    return r;
  }

  /* ********************************************************** */
  // TypeRefExpr | TypePathExpr | ListTypeExpr | MapTypeExpr | OptionTypeExpr
  public static boolean NonFuncTypeExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "NonFuncTypeExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, NON_FUNC_TYPE_EXPR, "<non func type expr>");
    r = TypeRefExpr(b, l + 1);
    if (!r) r = TypePathExpr(b, l + 1);
    if (!r) r = ListTypeExpr(b, l + 1);
    if (!r) r = MapTypeExpr(b, l + 1);
    if (!r) r = OptionTypeExpr(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // OPTION '[' NonFuncTypeExpr ']'
  public static boolean OptionTypeExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "OptionTypeExpr")) return false;
    if (!nextTokenIs(b, OPTION)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, OPTION);
    r = r && consumeToken(b, "[");
    r = r && NonFuncTypeExpr(b, l + 1);
    r = r && consumeToken(b, "]");
    exit_section_(b, m, OPTION_TYPE_EXPR, r);
    return r;
  }

  /* ********************************************************** */
  // IntLiteral | BoolLiteral | StringLiteral | ByteArrayLiteral
  public static boolean PrimitiveLiteral(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "PrimitiveLiteral")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PRIMITIVE_LITERAL, "<primitive literal>");
    r = IntLiteral(b, l + 1);
    if (!r) r = BoolLiteral(b, l + 1);
    if (!r) r = StringLiteral(b, l + 1);
    if (!r) r = ByteArrayLiteral(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // PrimitiveLiteral
  public static boolean PrimitiveLiteralExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "PrimitiveLiteralExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PRIMITIVE_LITERAL_EXPR, "<primitive literal expr>");
    r = PrimitiveLiteral(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // ProgramType Statement (Statement)*
  static boolean Program(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Program")) return false;
    if (!nextTokenIs(b, PROGRAM_TYPES_CODE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ProgramType(b, l + 1);
    r = r && Statement(b, l + 1);
    r = r && Program_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (Statement)*
  private static boolean Program_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Program_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!Program_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "Program_2", c)) break;
    }
    return true;
  }

  // (Statement)
  private static boolean Program_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Program_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = Statement(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // program_types_code Word
  public static boolean ProgramType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ProgramType")) return false;
    if (!nextTokenIs(b, PROGRAM_TYPES_CODE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PROGRAM_TYPES_CODE);
    r = r && Word(b, l + 1);
    exit_section_(b, m, PROGRAM_TYPE, r);
    return r;
  }

  /* ********************************************************** */
  // ImportStatement | ConstStatement | StructStatement | FuncStatement | EnumStatement
  public static boolean Statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Statement")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STATEMENT, "<statement>");
    r = ImportStatement(b, l + 1);
    if (!r) r = ConstStatement(b, l + 1);
    if (!r) r = StructStatement(b, l + 1);
    if (!r) r = FuncStatement(b, l + 1);
    if (!r) r = EnumStatement(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '"' StringLiteralChar* '"'
  public static boolean StringLiteral(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StringLiteral")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STRING_LITERAL, "<string literal>");
    r = consumeToken(b, "\"");
    r = r && StringLiteral_1(b, l + 1);
    r = r && consumeToken(b, "\"");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // StringLiteralChar*
  private static boolean StringLiteral_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StringLiteral_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!StringLiteralChar(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "StringLiteral_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '\\' | '\n' | '\t' | '\"' | 'regexp:[^\\]'
  public static boolean StringLiteralChar(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StringLiteralChar")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STRING_LITERAL_CHAR, "<string literal char>");
    r = consumeToken(b, "\\\\");
    if (!r) r = consumeToken(b, "\\n");
    if (!r) r = consumeToken(b, "\\t");
    if (!r) r = consumeToken(b, "\\\"");
    if (!r) r = consumeToken(b, "regexp:[^\\\\]");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // (TypePathExpr | TypeRefExpr) ['{' StructLiteralField (',' StructLiteralField)* '}']
  public static boolean StructLiteralExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StructLiteralExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, STRUCT_LITERAL_EXPR, "<struct literal expr>");
    r = StructLiteralExpr_0(b, l + 1);
    r = r && StructLiteralExpr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // TypePathExpr | TypeRefExpr
  private static boolean StructLiteralExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StructLiteralExpr_0")) return false;
    boolean r;
    r = TypePathExpr(b, l + 1);
    if (!r) r = TypeRefExpr(b, l + 1);
    return r;
  }

  // ['{' StructLiteralField (',' StructLiteralField)* '}']
  private static boolean StructLiteralExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StructLiteralExpr_1")) return false;
    StructLiteralExpr_1_0(b, l + 1);
    return true;
  }

  // '{' StructLiteralField (',' StructLiteralField)* '}'
  private static boolean StructLiteralExpr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StructLiteralExpr_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "{");
    r = r && StructLiteralField(b, l + 1);
    r = r && StructLiteralExpr_1_0_2(b, l + 1);
    r = r && consumeToken(b, "}");
    exit_section_(b, m, null, r);
    return r;
  }

  // (',' StructLiteralField)*
  private static boolean StructLiteralExpr_1_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StructLiteralExpr_1_0_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!StructLiteralExpr_1_0_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "StructLiteralExpr_1_0_2", c)) break;
    }
    return true;
  }

  // ',' StructLiteralField
  private static boolean StructLiteralExpr_1_0_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StructLiteralExpr_1_0_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ",");
    r = r && StructLiteralField(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // [Word ':'] ValueExpr
  public static boolean StructLiteralField(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StructLiteralField")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STRUCT_LITERAL_FIELD, "<struct literal field>");
    r = StructLiteralField_0(b, l + 1);
    r = r && ValueExpr(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [Word ':']
  private static boolean StructLiteralField_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StructLiteralField_0")) return false;
    StructLiteralField_0_0(b, l + 1);
    return true;
  }

  // Word ':'
  private static boolean StructLiteralField_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StructLiteralField_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = Word(b, l + 1);
    r = r && consumeToken(b, ":");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // STRUCT Word '{' DataDefinition [ImplDefinition] '}'
  public static boolean StructStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StructStatement")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, STRUCT_STATEMENT, "<struct statement>");
    r = consumeToken(b, STRUCT);
    r = r && Word(b, l + 1);
    r = r && consumeToken(b, "{");
    p = r; // pin = 3
    r = r && report_error_(b, DataDefinition(b, l + 1));
    r = p && report_error_(b, StructStatement_4(b, l + 1)) && r;
    r = p && consumeToken(b, "}") && r;
    exit_section_(b, l, m, r, p, HeliosParser::statement_recover);
    return r || p;
  }

  // [ImplDefinition]
  private static boolean StructStatement_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "StructStatement_4")) return false;
    ImplDefinition(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // (Word | (Identifier ':' Word)) '=>' (ValueExpr | ('{' ValueExpr '}'))
  public static boolean SwitchCase(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SwitchCase")) return false;
    if (!nextTokenIs(b, T_WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = SwitchCase_0(b, l + 1);
    r = r && consumeToken(b, "=>");
    r = r && SwitchCase_2(b, l + 1);
    exit_section_(b, m, SWITCH_CASE, r);
    return r;
  }

  // Word | (Identifier ':' Word)
  private static boolean SwitchCase_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SwitchCase_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = Word(b, l + 1);
    if (!r) r = SwitchCase_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // Identifier ':' Word
  private static boolean SwitchCase_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SwitchCase_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = Identifier(b, l + 1);
    r = r && consumeToken(b, ":");
    r = r && Word(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ValueExpr | ('{' ValueExpr '}')
  private static boolean SwitchCase_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SwitchCase_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ValueExpr(b, l + 1, -1);
    if (!r) r = SwitchCase_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '{' ValueExpr '}'
  private static boolean SwitchCase_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SwitchCase_2_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "{");
    r = r && ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, "}");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ELSE '=>' (ValueExpr | ('{' ValueExpr '}'))
  public static boolean SwitchDefault(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SwitchDefault")) return false;
    if (!nextTokenIs(b, ELSE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ELSE);
    r = r && consumeToken(b, "=>");
    r = r && SwitchDefault_2(b, l + 1);
    exit_section_(b, m, SWITCH_DEFAULT, r);
    return r;
  }

  // ValueExpr | ('{' ValueExpr '}')
  private static boolean SwitchDefault_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SwitchDefault_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ValueExpr(b, l + 1, -1);
    if (!r) r = SwitchDefault_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '{' ValueExpr '}'
  private static boolean SwitchDefault_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SwitchDefault_2_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "{");
    r = r && ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, "}");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // NonFuncTypeExpr | FuncTypeExpr
  public static boolean TypeExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "TypeExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, TYPE_EXPR, "<type expr>");
    r = NonFuncTypeExpr(b, l + 1);
    if (!r) r = FuncTypeExpr(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // NonFuncTypeExpr '::' Word
  public static boolean TypePathExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "TypePathExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TYPE_PATH_EXPR, "<type path expr>");
    r = NonFuncTypeExpr(b, l + 1);
    r = r && consumeToken(b, "::");
    r = r && Word(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // Identifier
  public static boolean TypeRefExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "TypeRefExpr")) return false;
    if (!nextTokenIs(b, T_WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = Identifier(b, l + 1);
    exit_section_(b, m, TYPE_REF_EXPR, r);
    return r;
  }

  /* ********************************************************** */
  // '-' | '+' | '!'
  public static boolean UnaryOp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "UnaryOp")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, UNARY_OP, "<unary op>");
    r = consumeToken(b, "-");
    if (!r) r = consumeToken(b, "+");
    if (!r) r = consumeToken(b, "!");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // !('}')
  static boolean UntilBraceRecover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "UntilBraceRecover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !UntilBraceRecover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ('}')
  private static boolean UntilBraceRecover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "UntilBraceRecover_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "}");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // !(';')
  static boolean UntilSemicolonRecover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "UntilSemicolonRecover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !UntilSemicolonRecover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (';')
  private static boolean UntilSemicolonRecover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "UntilSemicolonRecover_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ";");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // t_word
  public static boolean Word(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Word")) return false;
    if (!nextTokenIs(b, T_WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, T_WORD);
    exit_section_(b, m, WORD, r);
    return r;
  }

  /* ********************************************************** */
  // !(  "}" | "\r\n")
  static boolean statement_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !statement_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // "}" | "\r\n"
  private static boolean statement_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_recover_0")) return false;
    boolean r;
    r = consumeToken(b, "}");
    if (!r) r = consumeToken(b, "\r\n");
    return r;
  }

  /* ********************************************************** */
  // Expression root: ValueExpr
  // Operator priority table:
  // 0: BINARY(BinaryExpr)
  // 1: ATOM(AssignExpr)
  // 2: ATOM(PrintExpr)
  // 3: PREFIX(UnaryExpr)
  // 4: PREFIX(ParensExpr)
  // 5: POSTFIX(CallExpr)
  // 6: POSTFIX(MemberExpr)
  // 7: ATOM(IfElseExpr)
  // 8: POSTFIX(SwitchExpr)
  // 9: ATOM(ValueRefExpr)
  // 10: POSTFIX(ValuePathExpr)
  // 11: ATOM(LiteralExpr)
  public static boolean ValueExpr(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "ValueExpr")) return false;
    addVariant(b, "<value expr>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<value expr>");
    r = AssignExpr(b, l + 1);
    if (!r) r = PrintExpr(b, l + 1);
    if (!r) r = UnaryExpr(b, l + 1);
    if (!r) r = ParensExpr(b, l + 1);
    if (!r) r = IfElseExpr(b, l + 1);
    if (!r) r = ValueRefExpr(b, l + 1);
    if (!r) r = LiteralExpr(b, l + 1);
    p = r;
    r = r && ValueExpr_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean ValueExpr_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "ValueExpr_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 0 && BinaryOp(b, l + 1)) {
        r = ValueExpr(b, l, 0);
        exit_section_(b, l, m, BINARY_EXPR, r, true, null);
      }
      else if (g < 5 && CallExpr_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, CALL_EXPR, r, true, null);
      }
      else if (g < 6 && MemberExpr_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, MEMBER_EXPR, r, true, null);
      }
      else if (g < 8 && SwitchExpr_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, SWITCH_EXPR, r, true, null);
      }
      else if (g < 10 && leftMarkerIs(b, NON_FUNC_TYPE_EXPR) && ValuePathExpr_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, VALUE_PATH_EXPR, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  // Identifier [':' TypeExpr] '=' ValueExpr ';' ValueExpr
  public static boolean AssignExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "AssignExpr")) return false;
    if (!nextTokenIsSmart(b, T_WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = Identifier(b, l + 1);
    r = r && AssignExpr_1(b, l + 1);
    r = r && consumeToken(b, "=");
    r = r && ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, ";");
    r = r && ValueExpr(b, l + 1, -1);
    exit_section_(b, m, ASSIGN_EXPR, r);
    return r;
  }

  // [':' TypeExpr]
  private static boolean AssignExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "AssignExpr_1")) return false;
    AssignExpr_1_0(b, l + 1);
    return true;
  }

  // ':' TypeExpr
  private static boolean AssignExpr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "AssignExpr_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, ":");
    r = r && TypeExpr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // 'print' '(' ValueExpr ')' ';' ValueExpr
  public static boolean PrintExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "PrintExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PRINT_EXPR, "<print expr>");
    r = consumeTokenSmart(b, "print");
    r = r && consumeToken(b, "(");
    r = r && ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, ")");
    r = r && consumeToken(b, ";");
    r = r && ValueExpr(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  public static boolean UnaryExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "UnaryExpr")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = UnaryOp(b, l + 1);
    p = r;
    r = p && ValueExpr(b, l, 3);
    exit_section_(b, l, m, UNARY_EXPR, r, p, null);
    return r || p;
  }

  public static boolean ParensExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ParensExpr")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, "(");
    p = r;
    r = p && ValueExpr(b, l, 4);
    r = p && report_error_(b, consumeToken(b, ")")) && r;
    exit_section_(b, l, m, PARENS_EXPR, r, p, null);
    return r || p;
  }

  // '(' [ValueExpr (',' ValueExpr)*] ')'
  private static boolean CallExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "CallExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, "(");
    r = r && CallExpr_0_1(b, l + 1);
    r = r && consumeToken(b, ")");
    exit_section_(b, m, null, r);
    return r;
  }

  // [ValueExpr (',' ValueExpr)*]
  private static boolean CallExpr_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "CallExpr_0_1")) return false;
    CallExpr_0_1_0(b, l + 1);
    return true;
  }

  // ValueExpr (',' ValueExpr)*
  private static boolean CallExpr_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "CallExpr_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ValueExpr(b, l + 1, -1);
    r = r && CallExpr_0_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (',' ValueExpr)*
  private static boolean CallExpr_0_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "CallExpr_0_1_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!CallExpr_0_1_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "CallExpr_0_1_0_1", c)) break;
    }
    return true;
  }

  // ',' ValueExpr
  private static boolean CallExpr_0_1_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "CallExpr_0_1_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, ",");
    r = r && ValueExpr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '.' Word
  private static boolean MemberExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MemberExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, ".");
    r = r && Word(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IF '(' ValueExpr ')' '{' ValueExpr '}' (ELSE IF '(' ValueExpr ')' '{' ValueExpr '}')* ELSE '{' ValueExpr '}'
  public static boolean IfElseExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IfElseExpr")) return false;
    if (!nextTokenIsSmart(b, IF)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, IF);
    r = r && consumeToken(b, "(");
    r = r && ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, ")");
    r = r && consumeToken(b, "{");
    r = r && ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, "}");
    r = r && IfElseExpr_7(b, l + 1);
    r = r && consumeToken(b, ELSE);
    r = r && consumeToken(b, "{");
    r = r && ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, "}");
    exit_section_(b, m, IF_ELSE_EXPR, r);
    return r;
  }

  // (ELSE IF '(' ValueExpr ')' '{' ValueExpr '}')*
  private static boolean IfElseExpr_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IfElseExpr_7")) return false;
    while (true) {
      int c = current_position_(b);
      if (!IfElseExpr_7_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "IfElseExpr_7", c)) break;
    }
    return true;
  }

  // ELSE IF '(' ValueExpr ')' '{' ValueExpr '}'
  private static boolean IfElseExpr_7_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IfElseExpr_7_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokensSmart(b, 0, ELSE, IF);
    r = r && consumeToken(b, "(");
    r = r && ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, ")");
    r = r && consumeToken(b, "{");
    r = r && ValueExpr(b, l + 1, -1);
    r = r && consumeToken(b, "}");
    exit_section_(b, m, null, r);
    return r;
  }

  // '.' SWITCH '{' SwitchCase (',' SwitchCase)* [SwitchDefault] '}'
  private static boolean SwitchExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SwitchExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, ".");
    r = r && consumeToken(b, SWITCH);
    r = r && consumeToken(b, "{");
    r = r && SwitchCase(b, l + 1);
    r = r && SwitchExpr_0_4(b, l + 1);
    r = r && SwitchExpr_0_5(b, l + 1);
    r = r && consumeToken(b, "}");
    exit_section_(b, m, null, r);
    return r;
  }

  // (',' SwitchCase)*
  private static boolean SwitchExpr_0_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SwitchExpr_0_4")) return false;
    while (true) {
      int c = current_position_(b);
      if (!SwitchExpr_0_4_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "SwitchExpr_0_4", c)) break;
    }
    return true;
  }

  // ',' SwitchCase
  private static boolean SwitchExpr_0_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SwitchExpr_0_4_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, ",");
    r = r && SwitchCase(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [SwitchDefault]
  private static boolean SwitchExpr_0_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SwitchExpr_0_5")) return false;
    SwitchDefault(b, l + 1);
    return true;
  }

  // Identifier
  public static boolean ValueRefExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ValueRefExpr")) return false;
    if (!nextTokenIsSmart(b, T_WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = Identifier(b, l + 1);
    exit_section_(b, m, VALUE_REF_EXPR, r);
    return r;
  }

  // '::' Word
  private static boolean ValuePathExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ValuePathExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, "::");
    r = r && Word(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // PrimitiveLiteralExpr | StructLiteralExpr | ListLiteralExpr | MapLiteralExpr | FuncLiteralExpr
  public static boolean LiteralExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "LiteralExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, LITERAL_EXPR, "<literal expr>");
    r = PrimitiveLiteralExpr(b, l + 1);
    if (!r) r = StructLiteralExpr(b, l + 1);
    if (!r) r = ListLiteralExpr(b, l + 1);
    if (!r) r = MapLiteralExpr(b, l + 1);
    if (!r) r = FuncLiteralExpr(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

}
