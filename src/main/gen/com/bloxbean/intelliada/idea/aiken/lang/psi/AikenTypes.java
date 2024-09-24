// This is a generated file. Not intended for manual editing.
package com.bloxbean.intelliada.idea.aiken.lang.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.bloxbean.intelliada.idea.aiken.lang.psi.impl.*;

public interface AikenTypes {

  IElementType CONSTANT_STATEMENT = new AikenElementType("CONSTANT_STATEMENT");
  IElementType CONSTRUCTOR_IDENTIFIER = new AikenElementType("CONSTRUCTOR_IDENTIFIER");
  IElementType CONSTRUCTOR_NAME = new AikenElementType("CONSTRUCTOR_NAME");
  IElementType FUNCTION_CALL = new AikenElementType("FUNCTION_CALL");
  IElementType FUNCTION_CALL_PARAM = new AikenElementType("FUNCTION_CALL_PARAM");
  IElementType FUNCTION_STATEMENT = new AikenElementType("FUNCTION_STATEMENT");
  IElementType FUNCTION_STATEMENT_TYPE = new AikenElementType("FUNCTION_STATEMENT_TYPE");
  IElementType IMPORT_STATEMENT = new AikenElementType("IMPORT_STATEMENT");
  IElementType IMPORT_STATEMENT_ELEMENT = new AikenElementType("IMPORT_STATEMENT_ELEMENT");
  IElementType IMPORT_STATEMENT_TYPES = new AikenElementType("IMPORT_STATEMENT_TYPES");
  IElementType TYPE_IDENTIFIER = new AikenElementType("TYPE_IDENTIFIER");
  IElementType TYPE_IDENTIFIER_INNER_TYPE = new AikenElementType("TYPE_IDENTIFIER_INNER_TYPE");
  IElementType TYPE_NAME = new AikenElementType("TYPE_NAME");
  IElementType TYPE_STATEMENT = new AikenElementType("TYPE_STATEMENT");
  IElementType TYPE_STATEMENT_CONSTRUCTOR = new AikenElementType("TYPE_STATEMENT_CONSTRUCTOR");
  IElementType TYPE_STATEMENT_CONSTRUCTOR_NO_ARG = new AikenElementType("TYPE_STATEMENT_CONSTRUCTOR_NO_ARG");
  IElementType VALIDATOR_STATEMENT = new AikenElementType("VALIDATOR_STATEMENT");
  IElementType VARIABLE_STATEMENT = new AikenElementType("VARIABLE_STATEMENT");
  IElementType VARIABLE_VALUE = new AikenElementType("VARIABLE_VALUE");

  IElementType AND = new AikenTokenType("&&");
  IElementType ARROW = new AikenTokenType("->");
  IElementType AS = new AikenTokenType("as");
  IElementType BANG = new AikenTokenType("!");
  IElementType CHECK = new AikenTokenType("check");
  IElementType COLON = new AikenTokenType(":");
  IElementType COMMA = new AikenTokenType(",");
  IElementType COMMENT = new AikenTokenType("COMMENT");
  IElementType CONST = new AikenTokenType("const");
  IElementType DIV = new AikenTokenType("/");
  IElementType DIVDIV = new AikenTokenType("//");
  IElementType DOC_COMMENT = new AikenTokenType("DOC_COMMENT");
  IElementType DOT = new AikenTokenType(".");
  IElementType DOTDOT = new AikenTokenType("..");
  IElementType ELSE = new AikenTokenType("else");
  IElementType EQ = new AikenTokenType("=");
  IElementType EQEQ = new AikenTokenType("==");
  IElementType EXPECT = new AikenTokenType("expect");
  IElementType EXTERNAL = new AikenTokenType("external");
  IElementType FAIL = new AikenTokenType("fail");
  IElementType FAT_ARROW = new AikenTokenType("=>");
  IElementType FN = new AikenTokenType("fn");
  IElementType GT = new AikenTokenType(">");
  IElementType IDENTIFIER = new AikenTokenType("IDENTIFIER");
  IElementType IF = new AikenTokenType("if");
  IElementType IS = new AikenTokenType("is");
  IElementType LBRACE = new AikenTokenType("{");
  IElementType LBRACK = new AikenTokenType("[");
  IElementType LET = new AikenTokenType("let");
  IElementType LPAREN = new AikenTokenType("(");
  IElementType LT = new AikenTokenType("<");
  IElementType MINT = new AikenTokenType("mint");
  IElementType MINUS = new AikenTokenType("-");
  IElementType MODULE_COMMENT = new AikenTokenType("MODULE_COMMENT");
  IElementType MUL = new AikenTokenType("*");
  IElementType NUMBER = new AikenTokenType("NUMBER");
  IElementType OPAQUE = new AikenTokenType("opaque");
  IElementType OR = new AikenTokenType("||");
  IElementType PIPE = new AikenTokenType("|>");
  IElementType PLUS = new AikenTokenType("+");
  IElementType PROPOSE = new AikenTokenType("propose");
  IElementType PUB = new AikenTokenType("pub");
  IElementType PUBLISH = new AikenTokenType("publish");
  IElementType QUOTE = new AikenTokenType("\"");
  IElementType RBRACE = new AikenTokenType("}");
  IElementType RBRACK = new AikenTokenType("]");
  IElementType RPAREN = new AikenTokenType(")");
  IElementType SPEND = new AikenTokenType("spend");
  IElementType STRING_CONTENT = new AikenTokenType("STRING_CONTENT");
  IElementType TEST = new AikenTokenType("test");
  IElementType TODO = new AikenTokenType("todo");
  IElementType TRACE = new AikenTokenType("trace");
  IElementType TYPE = new AikenTokenType("type");
  IElementType UPPER_IDENTIFIER = new AikenTokenType("UPPER_IDENTIFIER");
  IElementType USE = new AikenTokenType("use");
  IElementType VALIDATOR = new AikenTokenType("validator");
  IElementType VIA = new AikenTokenType("via");
  IElementType VOTE = new AikenTokenType("vote");
  IElementType WHEN = new AikenTokenType("when");
  IElementType WITHDRAW = new AikenTokenType("withdraw");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == CONSTANT_STATEMENT) {
        return new AikenConstantStatementImpl(node);
      }
      else if (type == CONSTRUCTOR_IDENTIFIER) {
        return new AikenConstructorIdentifierImpl(node);
      }
      else if (type == CONSTRUCTOR_NAME) {
        return new AikenConstructorNameImpl(node);
      }
      else if (type == FUNCTION_CALL) {
        return new AikenFunctionCallImpl(node);
      }
      else if (type == FUNCTION_CALL_PARAM) {
        return new AikenFunctionCallParamImpl(node);
      }
      else if (type == FUNCTION_STATEMENT) {
        return new AikenFunctionStatementImpl(node);
      }
      else if (type == FUNCTION_STATEMENT_TYPE) {
        return new AikenFunctionStatementTypeImpl(node);
      }
      else if (type == IMPORT_STATEMENT) {
        return new AikenImportStatementImpl(node);
      }
      else if (type == IMPORT_STATEMENT_ELEMENT) {
        return new AikenImportStatementElementImpl(node);
      }
      else if (type == IMPORT_STATEMENT_TYPES) {
        return new AikenImportStatementTypesImpl(node);
      }
      else if (type == TYPE_IDENTIFIER) {
        return new AikenTypeIdentifierImpl(node);
      }
      else if (type == TYPE_IDENTIFIER_INNER_TYPE) {
        return new AikenTypeIdentifierInnerTypeImpl(node);
      }
      else if (type == TYPE_NAME) {
        return new AikenTypeNameImpl(node);
      }
      else if (type == TYPE_STATEMENT) {
        return new AikenTypeStatementImpl(node);
      }
      else if (type == TYPE_STATEMENT_CONSTRUCTOR) {
        return new AikenTypeStatementConstructorImpl(node);
      }
      else if (type == TYPE_STATEMENT_CONSTRUCTOR_NO_ARG) {
        return new AikenTypeStatementConstructorNoArgImpl(node);
      }
      else if (type == VALIDATOR_STATEMENT) {
        return new AikenValidatorStatementImpl(node);
      }
      else if (type == VARIABLE_STATEMENT) {
        return new AikenVariableStatementImpl(node);
      }
      else if (type == VARIABLE_VALUE) {
        return new AikenVariableValueImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
