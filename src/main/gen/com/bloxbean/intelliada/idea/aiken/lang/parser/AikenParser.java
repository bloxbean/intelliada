// This is a generated file. Not intended for manual editing.
package com.bloxbean.intelliada.idea.aiken.lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.bloxbean.intelliada.idea.aiken.lang.psi.AikenTypes.*;
import static com.bloxbean.intelliada.idea.aiken.lang.psi.impl.AikenParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class AikenParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, null);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return aikenFile(b, l + 1);
  }

  /* ********************************************************** */
  // topLevelDefinition*
  static boolean aikenFile(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "aikenFile")) return false;
    while (true) {
      int c = current_position_(b);
      if (!topLevelDefinition(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "aikenFile", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // CONST IDENTIFIER EQ variableValue
  public static boolean constantStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constantStatement")) return false;
    if (!nextTokenIs(b, CONST)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, CONST, IDENTIFIER, EQ);
    r = r && variableValue(b, l + 1);
    exit_section_(b, m, CONSTANT_STATEMENT, r);
    return r;
  }

  /* ********************************************************** */
  // constructorIdentifier typeIdentifier [COMMA]
  static boolean constructorElement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructorElement")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = constructorIdentifier(b, l + 1);
    r = r && typeIdentifier(b, l + 1);
    r = r && constructorElement_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [COMMA]
  private static boolean constructorElement_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructorElement_2")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // IDENTIFIER COLON
  public static boolean constructorIdentifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructorIdentifier")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, IDENTIFIER, COLON);
    exit_section_(b, m, CONSTRUCTOR_IDENTIFIER, r);
    return r;
  }

  /* ********************************************************** */
  // UPPER_IDENTIFIER
  public static boolean constructorName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructorName")) return false;
    if (!nextTokenIs(b, UPPER_IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, UPPER_IDENTIFIER);
    exit_section_(b, m, CONSTRUCTOR_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // [IDENTIFIER DOT] IDENTIFIER LPAREN [functionCallParam*] RPAREN [PIPE functionCall]
  public static boolean functionCall(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionCall")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = functionCall_0(b, l + 1);
    r = r && consumeTokens(b, 0, IDENTIFIER, LPAREN);
    r = r && functionCall_3(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    r = r && functionCall_5(b, l + 1);
    exit_section_(b, m, FUNCTION_CALL, r);
    return r;
  }

  // [IDENTIFIER DOT]
  private static boolean functionCall_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionCall_0")) return false;
    parseTokens(b, 0, IDENTIFIER, DOT);
    return true;
  }

  // [functionCallParam*]
  private static boolean functionCall_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionCall_3")) return false;
    functionCall_3_0(b, l + 1);
    return true;
  }

  // functionCallParam*
  private static boolean functionCall_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionCall_3_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!functionCallParam(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "functionCall_3_0", c)) break;
    }
    return true;
  }

  // [PIPE functionCall]
  private static boolean functionCall_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionCall_5")) return false;
    functionCall_5_0(b, l + 1);
    return true;
  }

  // PIPE functionCall
  private static boolean functionCall_5_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionCall_5_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PIPE);
    r = r && functionCall(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ((IDENTIFIER COLON variableValue) | variableValue)[COMMA]
  public static boolean functionCallParam(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionCallParam")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FUNCTION_CALL_PARAM, "<function call param>");
    r = functionCallParam_0(b, l + 1);
    r = r && functionCallParam_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (IDENTIFIER COLON variableValue) | variableValue
  private static boolean functionCallParam_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionCallParam_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = functionCallParam_0_0(b, l + 1);
    if (!r) r = variableValue(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER COLON variableValue
  private static boolean functionCallParam_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionCallParam_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, IDENTIFIER, COLON);
    r = r && variableValue(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [COMMA]
  private static boolean functionCallParam_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionCallParam_1")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // [PUB] FN IDENTIFIER LPAREN [(constructorElement)*] RPAREN [functionStatementType] LBRACE [innerDefinition*] RBRACE
  public static boolean functionStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionStatement")) return false;
    if (!nextTokenIs(b, "<function statement>", FN, PUB)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FUNCTION_STATEMENT, "<function statement>");
    r = functionStatement_0(b, l + 1);
    r = r && consumeTokens(b, 0, FN, IDENTIFIER, LPAREN);
    r = r && functionStatement_4(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    r = r && functionStatement_6(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && functionStatement_8(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [PUB]
  private static boolean functionStatement_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionStatement_0")) return false;
    consumeToken(b, PUB);
    return true;
  }

  // [(constructorElement)*]
  private static boolean functionStatement_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionStatement_4")) return false;
    functionStatement_4_0(b, l + 1);
    return true;
  }

  // (constructorElement)*
  private static boolean functionStatement_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionStatement_4_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!functionStatement_4_0_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "functionStatement_4_0", c)) break;
    }
    return true;
  }

  // (constructorElement)
  private static boolean functionStatement_4_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionStatement_4_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = constructorElement(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [functionStatementType]
  private static boolean functionStatement_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionStatement_6")) return false;
    functionStatementType(b, l + 1);
    return true;
  }

  // [innerDefinition*]
  private static boolean functionStatement_8(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionStatement_8")) return false;
    functionStatement_8_0(b, l + 1);
    return true;
  }

  // innerDefinition*
  private static boolean functionStatement_8_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionStatement_8_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!innerDefinition(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "functionStatement_8_0", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // ARROW typeIdentifier
  public static boolean functionStatementType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "functionStatementType")) return false;
    if (!nextTokenIs(b, ARROW)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ARROW);
    r = r && typeIdentifier(b, l + 1);
    exit_section_(b, m, FUNCTION_STATEMENT_TYPE, r);
    return r;
  }

  /* ********************************************************** */
  // USE importStatementElement
  public static boolean importStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "importStatement")) return false;
    if (!nextTokenIs(b, USE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, USE);
    r = r && importStatementElement(b, l + 1);
    exit_section_(b, m, IMPORT_STATEMENT, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER ('/' IDENTIFIER [importStatementTypes])*
  public static boolean importStatementElement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "importStatementElement")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && importStatementElement_1(b, l + 1);
    exit_section_(b, m, IMPORT_STATEMENT_ELEMENT, r);
    return r;
  }

  // ('/' IDENTIFIER [importStatementTypes])*
  private static boolean importStatementElement_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "importStatementElement_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!importStatementElement_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "importStatementElement_1", c)) break;
    }
    return true;
  }

  // '/' IDENTIFIER [importStatementTypes]
  private static boolean importStatementElement_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "importStatementElement_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, DIV, IDENTIFIER);
    r = r && importStatementElement_1_0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [importStatementTypes]
  private static boolean importStatementElement_1_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "importStatementElement_1_0_2")) return false;
    importStatementTypes(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // DOT LBRACE ((UPPER_IDENTIFIER|IDENTIFIER) [COMMA])* RBRACE
  public static boolean importStatementTypes(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "importStatementTypes")) return false;
    if (!nextTokenIs(b, DOT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, DOT, LBRACE);
    r = r && importStatementTypes_2(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, IMPORT_STATEMENT_TYPES, r);
    return r;
  }

  // ((UPPER_IDENTIFIER|IDENTIFIER) [COMMA])*
  private static boolean importStatementTypes_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "importStatementTypes_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!importStatementTypes_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "importStatementTypes_2", c)) break;
    }
    return true;
  }

  // (UPPER_IDENTIFIER|IDENTIFIER) [COMMA]
  private static boolean importStatementTypes_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "importStatementTypes_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = importStatementTypes_2_0_0(b, l + 1);
    r = r && importStatementTypes_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // UPPER_IDENTIFIER|IDENTIFIER
  private static boolean importStatementTypes_2_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "importStatementTypes_2_0_0")) return false;
    boolean r;
    r = consumeToken(b, UPPER_IDENTIFIER);
    if (!r) r = consumeToken(b, IDENTIFIER);
    return r;
  }

  // [COMMA]
  private static boolean importStatementTypes_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "importStatementTypes_2_0_1")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // COMMENT
  //     | variableStatement
  //     | constantStatement
  //     | functionCall
  static boolean innerDefinition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "innerDefinition")) return false;
    boolean r;
    r = consumeToken(b, COMMENT);
    if (!r) r = variableStatement(b, l + 1);
    if (!r) r = constantStatement(b, l + 1);
    if (!r) r = functionCall(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // importStatement
  //     | typeStatement
  //     | validatorStatement
  //     | functionStatement
  //     | COMMENT
  //     | ([PUB] constantStatement)
  static boolean topLevelDefinition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "topLevelDefinition")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = importStatement(b, l + 1);
    if (!r) r = typeStatement(b, l + 1);
    if (!r) r = validatorStatement(b, l + 1);
    if (!r) r = functionStatement(b, l + 1);
    if (!r) r = consumeToken(b, COMMENT);
    if (!r) r = topLevelDefinition_5(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [PUB] constantStatement
  private static boolean topLevelDefinition_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "topLevelDefinition_5")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = topLevelDefinition_5_0(b, l + 1);
    r = r && constantStatement(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [PUB]
  private static boolean topLevelDefinition_5_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "topLevelDefinition_5_0")) return false;
    consumeToken(b, PUB);
    return true;
  }

  /* ********************************************************** */
  // [IDENTIFIER DOT] UPPER_IDENTIFIER [LPAREN typeIdentifierInnerType [typeIdentifierInnerType*] RPAREN]
  public static boolean typeIdentifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeIdentifier")) return false;
    if (!nextTokenIs(b, "<type identifier>", IDENTIFIER, UPPER_IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TYPE_IDENTIFIER, "<type identifier>");
    r = typeIdentifier_0(b, l + 1);
    r = r && consumeToken(b, UPPER_IDENTIFIER);
    r = r && typeIdentifier_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [IDENTIFIER DOT]
  private static boolean typeIdentifier_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeIdentifier_0")) return false;
    parseTokens(b, 0, IDENTIFIER, DOT);
    return true;
  }

  // [LPAREN typeIdentifierInnerType [typeIdentifierInnerType*] RPAREN]
  private static boolean typeIdentifier_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeIdentifier_2")) return false;
    typeIdentifier_2_0(b, l + 1);
    return true;
  }

  // LPAREN typeIdentifierInnerType [typeIdentifierInnerType*] RPAREN
  private static boolean typeIdentifier_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeIdentifier_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && typeIdentifierInnerType(b, l + 1);
    r = r && typeIdentifier_2_0_2(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // [typeIdentifierInnerType*]
  private static boolean typeIdentifier_2_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeIdentifier_2_0_2")) return false;
    typeIdentifier_2_0_2_0(b, l + 1);
    return true;
  }

  // typeIdentifierInnerType*
  private static boolean typeIdentifier_2_0_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeIdentifier_2_0_2_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!typeIdentifierInnerType(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "typeIdentifier_2_0_2_0", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // typeIdentifier [COMMA]
  public static boolean typeIdentifierInnerType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeIdentifierInnerType")) return false;
    if (!nextTokenIs(b, "<type identifier inner type>", IDENTIFIER, UPPER_IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TYPE_IDENTIFIER_INNER_TYPE, "<type identifier inner type>");
    r = typeIdentifier(b, l + 1);
    r = r && typeIdentifierInnerType_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [COMMA]
  private static boolean typeIdentifierInnerType_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeIdentifierInnerType_1")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // UPPER_IDENTIFIER
  public static boolean typeName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeName")) return false;
    if (!nextTokenIs(b, UPPER_IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, UPPER_IDENTIFIER);
    exit_section_(b, m, TYPE_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // [PUB] TYPE typeName ('<' IDENTIFIER '>')? LBRACE (constructorElement | (typeStatementConstructor | typeStatementConstructorNoArg)*) RBRACE
  public static boolean typeStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeStatement")) return false;
    if (!nextTokenIs(b, "<type statement>", PUB, TYPE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TYPE_STATEMENT, "<type statement>");
    r = typeStatement_0(b, l + 1);
    r = r && consumeToken(b, TYPE);
    r = r && typeName(b, l + 1);
    r = r && typeStatement_3(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && typeStatement_5(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [PUB]
  private static boolean typeStatement_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeStatement_0")) return false;
    consumeToken(b, PUB);
    return true;
  }

  // ('<' IDENTIFIER '>')?
  private static boolean typeStatement_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeStatement_3")) return false;
    typeStatement_3_0(b, l + 1);
    return true;
  }

  // '<' IDENTIFIER '>'
  private static boolean typeStatement_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeStatement_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, LT, IDENTIFIER, GT);
    exit_section_(b, m, null, r);
    return r;
  }

  // constructorElement | (typeStatementConstructor | typeStatementConstructorNoArg)*
  private static boolean typeStatement_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeStatement_5")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = constructorElement(b, l + 1);
    if (!r) r = typeStatement_5_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (typeStatementConstructor | typeStatementConstructorNoArg)*
  private static boolean typeStatement_5_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeStatement_5_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!typeStatement_5_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "typeStatement_5_1", c)) break;
    }
    return true;
  }

  // typeStatementConstructor | typeStatementConstructorNoArg
  private static boolean typeStatement_5_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeStatement_5_1_0")) return false;
    boolean r;
    r = typeStatementConstructor(b, l + 1);
    if (!r) r = typeStatementConstructorNoArg(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // constructorName (LBRACE|LPAREN) (constructorElement | IDENTIFIER)* (RBRACE|RPAREN)
  public static boolean typeStatementConstructor(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeStatementConstructor")) return false;
    if (!nextTokenIs(b, UPPER_IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = constructorName(b, l + 1);
    r = r && typeStatementConstructor_1(b, l + 1);
    r = r && typeStatementConstructor_2(b, l + 1);
    r = r && typeStatementConstructor_3(b, l + 1);
    exit_section_(b, m, TYPE_STATEMENT_CONSTRUCTOR, r);
    return r;
  }

  // LBRACE|LPAREN
  private static boolean typeStatementConstructor_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeStatementConstructor_1")) return false;
    boolean r;
    r = consumeToken(b, LBRACE);
    if (!r) r = consumeToken(b, LPAREN);
    return r;
  }

  // (constructorElement | IDENTIFIER)*
  private static boolean typeStatementConstructor_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeStatementConstructor_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!typeStatementConstructor_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "typeStatementConstructor_2", c)) break;
    }
    return true;
  }

  // constructorElement | IDENTIFIER
  private static boolean typeStatementConstructor_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeStatementConstructor_2_0")) return false;
    boolean r;
    r = constructorElement(b, l + 1);
    if (!r) r = consumeToken(b, IDENTIFIER);
    return r;
  }

  // RBRACE|RPAREN
  private static boolean typeStatementConstructor_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeStatementConstructor_3")) return false;
    boolean r;
    r = consumeToken(b, RBRACE);
    if (!r) r = consumeToken(b, RPAREN);
    return r;
  }

  /* ********************************************************** */
  // constructorName
  public static boolean typeStatementConstructorNoArg(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "typeStatementConstructorNoArg")) return false;
    if (!nextTokenIs(b, UPPER_IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = constructorName(b, l + 1);
    exit_section_(b, m, TYPE_STATEMENT_CONSTRUCTOR_NO_ARG, r);
    return r;
  }

  /* ********************************************************** */
  // VALIDATOR IDENTIFIER [LPAREN [(constructorElement)*] RPAREN] LBRACE [innerDefinition*] RBRACE
  public static boolean validatorStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "validatorStatement")) return false;
    if (!nextTokenIs(b, VALIDATOR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, VALIDATOR, IDENTIFIER);
    r = r && validatorStatement_2(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && validatorStatement_4(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, VALIDATOR_STATEMENT, r);
    return r;
  }

  // [LPAREN [(constructorElement)*] RPAREN]
  private static boolean validatorStatement_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "validatorStatement_2")) return false;
    validatorStatement_2_0(b, l + 1);
    return true;
  }

  // LPAREN [(constructorElement)*] RPAREN
  private static boolean validatorStatement_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "validatorStatement_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && validatorStatement_2_0_1(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // [(constructorElement)*]
  private static boolean validatorStatement_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "validatorStatement_2_0_1")) return false;
    validatorStatement_2_0_1_0(b, l + 1);
    return true;
  }

  // (constructorElement)*
  private static boolean validatorStatement_2_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "validatorStatement_2_0_1_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!validatorStatement_2_0_1_0_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "validatorStatement_2_0_1_0", c)) break;
    }
    return true;
  }

  // (constructorElement)
  private static boolean validatorStatement_2_0_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "validatorStatement_2_0_1_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = constructorElement(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [innerDefinition*]
  private static boolean validatorStatement_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "validatorStatement_4")) return false;
    validatorStatement_4_0(b, l + 1);
    return true;
  }

  // innerDefinition*
  private static boolean validatorStatement_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "validatorStatement_4_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!innerDefinition(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "validatorStatement_4_0", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // LET IDENTIFIER EQ variableValue
  public static boolean variableStatement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "variableStatement")) return false;
    if (!nextTokenIs(b, LET)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, LET, IDENTIFIER, EQ);
    r = r && variableValue(b, l + 1);
    exit_section_(b, m, VARIABLE_STATEMENT, r);
    return r;
  }

  /* ********************************************************** */
  // functionCall | UPPER_IDENTIFIER | typeIdentifier | IDENTIFIER | NUMBER | STRING_CONTENT
  public static boolean variableValue(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "variableValue")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VARIABLE_VALUE, "<variable value>");
    r = functionCall(b, l + 1);
    if (!r) r = consumeToken(b, UPPER_IDENTIFIER);
    if (!r) r = typeIdentifier(b, l + 1);
    if (!r) r = consumeToken(b, IDENTIFIER);
    if (!r) r = consumeToken(b, NUMBER);
    if (!r) r = consumeToken(b, STRING_CONTENT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

}
