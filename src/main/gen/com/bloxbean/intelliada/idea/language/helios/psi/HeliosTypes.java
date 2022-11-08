// This is a generated file. Not intended for manual editing.
package com.bloxbean.intelliada.idea.language.helios.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.bloxbean.intelliada.idea.language.helios.psi.impl.*;

public interface HeliosTypes {

  IElementType ASSIGN_EXPR = new HeliosElementType("ASSIGN_EXPR");
  IElementType BINARY_EXPR = new HeliosElementType("BINARY_EXPR");
  IElementType BINARY_OP = new HeliosElementType("BINARY_OP");
  IElementType BOOL_LITERAL = new HeliosElementType("BOOL_LITERAL");
  IElementType BYTE_ARRAY_LITERAL = new HeliosElementType("BYTE_ARRAY_LITERAL");
  IElementType CALL_EXPR = new HeliosElementType("CALL_EXPR");
  IElementType COMMENT = new HeliosElementType("COMMENT");
  IElementType CONST_STATEMENT = new HeliosElementType("CONST_STATEMENT");
  IElementType DATA_DEFINITION = new HeliosElementType("DATA_DEFINITION");
  IElementType DATA_FIELD = new HeliosElementType("DATA_FIELD");
  IElementType ENUM_MEMBER = new HeliosElementType("ENUM_MEMBER");
  IElementType ENUM_STATEMENT = new HeliosElementType("ENUM_STATEMENT");
  IElementType FUNC_ARG = new HeliosElementType("FUNC_ARG");
  IElementType FUNC_LITERAL_EXPR = new HeliosElementType("FUNC_LITERAL_EXPR");
  IElementType FUNC_STATEMENT = new HeliosElementType("FUNC_STATEMENT");
  IElementType FUNC_TYPE_EXPR = new HeliosElementType("FUNC_TYPE_EXPR");
  IElementType IDENTIFIER = new HeliosElementType("IDENTIFIER");
  IElementType IF_ELSE_EXPR = new HeliosElementType("IF_ELSE_EXPR");
  IElementType IMPL_DEFINITION = new HeliosElementType("IMPL_DEFINITION");
  IElementType IMPL_MEMBER = new HeliosElementType("IMPL_MEMBER");
  IElementType IMPORT_FIELD = new HeliosElementType("IMPORT_FIELD");
  IElementType IMPORT_STATEMENT = new HeliosElementType("IMPORT_STATEMENT");
  IElementType INT_LITERAL = new HeliosElementType("INT_LITERAL");
  IElementType LIST_LITERAL_EXPR = new HeliosElementType("LIST_LITERAL_EXPR");
  IElementType LIST_TYPE_EXPR = new HeliosElementType("LIST_TYPE_EXPR");
  IElementType LITERAL_EXPR = new HeliosElementType("LITERAL_EXPR");
  IElementType MAP_LITERAL_EXPR = new HeliosElementType("MAP_LITERAL_EXPR");
  IElementType MAP_TYPE_EXPR = new HeliosElementType("MAP_TYPE_EXPR");
  IElementType MEMBER_EXPR = new HeliosElementType("MEMBER_EXPR");
  IElementType MODULE_NAME = new HeliosElementType("MODULE_NAME");
  IElementType NAME_TYPE_PAIR = new HeliosElementType("NAME_TYPE_PAIR");
  IElementType NON_FUNC_TYPE_EXPR = new HeliosElementType("NON_FUNC_TYPE_EXPR");
  IElementType OPTION_TYPE_EXPR = new HeliosElementType("OPTION_TYPE_EXPR");
  IElementType PARENS_EXPR = new HeliosElementType("PARENS_EXPR");
  IElementType PRIMITIVE_LITERAL = new HeliosElementType("PRIMITIVE_LITERAL");
  IElementType PRIMITIVE_LITERAL_EXPR = new HeliosElementType("PRIMITIVE_LITERAL_EXPR");
  IElementType PRINT_EXPR = new HeliosElementType("PRINT_EXPR");
  IElementType PROGRAM_TYPE = new HeliosElementType("PROGRAM_TYPE");
  IElementType STATEMENT = new HeliosElementType("STATEMENT");
  IElementType STRING_LITERAL = new HeliosElementType("STRING_LITERAL");
  IElementType STRING_LITERAL_CHAR = new HeliosElementType("STRING_LITERAL_CHAR");
  IElementType STRUCT_LITERAL_EXPR = new HeliosElementType("STRUCT_LITERAL_EXPR");
  IElementType STRUCT_LITERAL_FIELD = new HeliosElementType("STRUCT_LITERAL_FIELD");
  IElementType STRUCT_STATEMENT = new HeliosElementType("STRUCT_STATEMENT");
  IElementType SWITCH_CASE = new HeliosElementType("SWITCH_CASE");
  IElementType SWITCH_DEFAULT = new HeliosElementType("SWITCH_DEFAULT");
  IElementType SWITCH_EXPR = new HeliosElementType("SWITCH_EXPR");
  IElementType TYPE_EXPR = new HeliosElementType("TYPE_EXPR");
  IElementType TYPE_PATH_EXPR = new HeliosElementType("TYPE_PATH_EXPR");
  IElementType TYPE_REF_EXPR = new HeliosElementType("TYPE_REF_EXPR");
  IElementType UNARY_EXPR = new HeliosElementType("UNARY_EXPR");
  IElementType UNARY_OP = new HeliosElementType("UNARY_OP");
  IElementType VALUE_EXPR = new HeliosElementType("VALUE_EXPR");
  IElementType VALUE_PATH_EXPR = new HeliosElementType("VALUE_PATH_EXPR");
  IElementType VALUE_REF_EXPR = new HeliosElementType("VALUE_REF_EXPR");
  IElementType WORD = new HeliosElementType("WORD");

  IElementType BLOCK_COMMENT = new HeliosTokenType("block_comment");
  IElementType B_ARRAYLITERAL = new HeliosTokenType("b_arrayliteral");
  IElementType ELSE = new HeliosTokenType("else");
  IElementType EOF = new HeliosTokenType("EOF");
  IElementType FUNC = new HeliosTokenType("func");
  IElementType FUNCARC = new HeliosTokenType("FuncArc");
  IElementType IF = new HeliosTokenType("if");
  IElementType I_LITERAL = new HeliosTokenType("i_literal");
  IElementType LINE_COMMENT = new HeliosTokenType("line_comment");
  IElementType NL = new HeliosTokenType("NL");
  IElementType OPTION = new HeliosTokenType("Option");
  IElementType PROGRAM_TYPES_CODE = new HeliosTokenType("program_types_code");
  IElementType STRUCT = new HeliosTokenType("struct");
  IElementType SWITCH = new HeliosTokenType("switch");
  IElementType T_WORD = new HeliosTokenType("t_word");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ASSIGN_EXPR) {
        return new HeliosAssignExprImpl(node);
      }
      else if (type == BINARY_EXPR) {
        return new HeliosBinaryExprImpl(node);
      }
      else if (type == BINARY_OP) {
        return new HeliosBinaryOpImpl(node);
      }
      else if (type == BOOL_LITERAL) {
        return new HeliosBoolLiteralImpl(node);
      }
      else if (type == BYTE_ARRAY_LITERAL) {
        return new HeliosByteArrayLiteralImpl(node);
      }
      else if (type == CALL_EXPR) {
        return new HeliosCallExprImpl(node);
      }
      else if (type == COMMENT) {
        return new HeliosCommentImpl(node);
      }
      else if (type == CONST_STATEMENT) {
        return new HeliosConstStatementImpl(node);
      }
      else if (type == DATA_DEFINITION) {
        return new HeliosDataDefinitionImpl(node);
      }
      else if (type == DATA_FIELD) {
        return new HeliosDataFieldImpl(node);
      }
      else if (type == ENUM_MEMBER) {
        return new HeliosEnumMemberImpl(node);
      }
      else if (type == ENUM_STATEMENT) {
        return new HeliosEnumStatementImpl(node);
      }
      else if (type == FUNC_ARG) {
        return new HeliosFuncArgImpl(node);
      }
      else if (type == FUNC_LITERAL_EXPR) {
        return new HeliosFuncLiteralExprImpl(node);
      }
      else if (type == FUNC_STATEMENT) {
        return new HeliosFuncStatementImpl(node);
      }
      else if (type == FUNC_TYPE_EXPR) {
        return new HeliosFuncTypeExprImpl(node);
      }
      else if (type == IDENTIFIER) {
        return new HeliosIdentifierImpl(node);
      }
      else if (type == IF_ELSE_EXPR) {
        return new HeliosIfElseExprImpl(node);
      }
      else if (type == IMPL_DEFINITION) {
        return new HeliosImplDefinitionImpl(node);
      }
      else if (type == IMPL_MEMBER) {
        return new HeliosImplMemberImpl(node);
      }
      else if (type == IMPORT_FIELD) {
        return new HeliosImportFieldImpl(node);
      }
      else if (type == IMPORT_STATEMENT) {
        return new HeliosImportStatementImpl(node);
      }
      else if (type == INT_LITERAL) {
        return new HeliosIntLiteralImpl(node);
      }
      else if (type == LIST_LITERAL_EXPR) {
        return new HeliosListLiteralExprImpl(node);
      }
      else if (type == LIST_TYPE_EXPR) {
        return new HeliosListTypeExprImpl(node);
      }
      else if (type == MAP_LITERAL_EXPR) {
        return new HeliosMapLiteralExprImpl(node);
      }
      else if (type == MAP_TYPE_EXPR) {
        return new HeliosMapTypeExprImpl(node);
      }
      else if (type == MEMBER_EXPR) {
        return new HeliosMemberExprImpl(node);
      }
      else if (type == MODULE_NAME) {
        return new HeliosModuleNameImpl(node);
      }
      else if (type == NAME_TYPE_PAIR) {
        return new HeliosNameTypePairImpl(node);
      }
      else if (type == OPTION_TYPE_EXPR) {
        return new HeliosOptionTypeExprImpl(node);
      }
      else if (type == PARENS_EXPR) {
        return new HeliosParensExprImpl(node);
      }
      else if (type == PRIMITIVE_LITERAL) {
        return new HeliosPrimitiveLiteralImpl(node);
      }
      else if (type == PRIMITIVE_LITERAL_EXPR) {
        return new HeliosPrimitiveLiteralExprImpl(node);
      }
      else if (type == PRINT_EXPR) {
        return new HeliosPrintExprImpl(node);
      }
      else if (type == PROGRAM_TYPE) {
        return new HeliosProgramTypeImpl(node);
      }
      else if (type == STATEMENT) {
        return new HeliosStatementImpl(node);
      }
      else if (type == STRING_LITERAL) {
        return new HeliosStringLiteralImpl(node);
      }
      else if (type == STRING_LITERAL_CHAR) {
        return new HeliosStringLiteralCharImpl(node);
      }
      else if (type == STRUCT_LITERAL_EXPR) {
        return new HeliosStructLiteralExprImpl(node);
      }
      else if (type == STRUCT_LITERAL_FIELD) {
        return new HeliosStructLiteralFieldImpl(node);
      }
      else if (type == STRUCT_STATEMENT) {
        return new HeliosStructStatementImpl(node);
      }
      else if (type == SWITCH_CASE) {
        return new HeliosSwitchCaseImpl(node);
      }
      else if (type == SWITCH_DEFAULT) {
        return new HeliosSwitchDefaultImpl(node);
      }
      else if (type == SWITCH_EXPR) {
        return new HeliosSwitchExprImpl(node);
      }
      else if (type == TYPE_PATH_EXPR) {
        return new HeliosTypePathExprImpl(node);
      }
      else if (type == TYPE_REF_EXPR) {
        return new HeliosTypeRefExprImpl(node);
      }
      else if (type == UNARY_EXPR) {
        return new HeliosUnaryExprImpl(node);
      }
      else if (type == UNARY_OP) {
        return new HeliosUnaryOpImpl(node);
      }
      else if (type == VALUE_PATH_EXPR) {
        return new HeliosValuePathExprImpl(node);
      }
      else if (type == VALUE_REF_EXPR) {
        return new HeliosValueRefExprImpl(node);
      }
      else if (type == WORD) {
        return new HeliosWordImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
