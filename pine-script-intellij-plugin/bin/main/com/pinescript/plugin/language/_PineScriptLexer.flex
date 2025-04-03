package com.pinescript.plugin.language;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import com.pinescript.plugin.psi.PineScriptTokenTypes;

%%

%class _PineScriptLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

CRLF=\R
WHITE_SPACE=[\ \n\t\f]

COMMENT="//"[^\r\n]*
FUNCTION_NAME=[a-zA-Z_][a-zA-Z0-9_]*[ \t]*\(
STRING=(\"([^\"\\]|\\.)*\"|\'([^\'\\]|\\.)*\')
NUMBER=[0-9]+(\.[0-9]*)?
COLOR=#[0-9a-fA-F]{6}
IDENTIFIER=[a-zA-Z_][a-zA-Z0-9_]*
OPERATOR=[+\-*/%=!<>]=?|==|!=|<=|>=|&&|\|\||\?|\:|\.|->

KEYWORD=("if"|"else"|"for"|"to"|"while"|"var"|"varip"|"import"|"export"|"switch"|"case"|"default"|"continue"|"break"|"return"|"type"|"enum"|"function"|"method"|"strategy"|"indicator"|"library"|"true"|"false"|"na"|"series"|"simple"|"const"|"input")

%%

<YYINITIAL> {
  {COMMENT}                                { return PineScriptTokenTypes.COMMENT; }
  {STRING}                                 { return PineScriptTokenTypes.STRING; }
  {NUMBER}                                 { return PineScriptTokenTypes.NUMBER; }
  {COLOR}                                  { return PineScriptTokenTypes.BUILT_IN_VARIABLE; } // Treating colors as built-in variables
  {KEYWORD}                                { return PineScriptTokenTypes.KEYWORD; }
  {FUNCTION_NAME}                          { return PineScriptTokenTypes.FUNCTION; }
  "math"|"array"|"matrix"|"str"|"color"|"table"|"chart"|"strategy"|"syminfo"|"ta"|"request"|"ticker" { return PineScriptTokenTypes.NAMESPACE; }
  "int"|"float"|"bool"|"string"|"color"|"label"|"line"|"box"|"table" { return PineScriptTokenTypes.TYPE; }
  {OPERATOR}                               { return PineScriptTokenTypes.OPERATOR; }
  {IDENTIFIER}                             { return PineScriptTokenTypes.IDENTIFIER; }
  {WHITE_SPACE}+                           { return TokenType.WHITE_SPACE; }
}

[^]                                        { return TokenType.BAD_CHARACTER; } 