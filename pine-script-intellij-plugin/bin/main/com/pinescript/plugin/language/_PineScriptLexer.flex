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

%{
  public void reset(CharSequence buffer, int start, int end, int initialState) {
    this.zzBuffer = buffer.toString().toCharArray();
    this.zzCurrentPos = this.zzMarkedPos = this.zzStartRead = start;
    this.zzEndRead = end;
    this.zzAtEOF = false;
    this.zzAtBOL = true;
    yybegin(initialState);
  }

  public int getTokenStart() {
    return zzStartRead;
  }

  public int getTokenEnd() {
    return zzMarkedPos;
  }
%}

WHITE_SPACE=[\ \n\t\f]
COMMENT="//"[^\r\n]*
STRING=(\"([^\"\\]|\\.)*\"|\'([^\'\\]|\\.)*\')
NUMBER=[0-9]+(\.[0-9]*)?
COLOR=#[0-9a-fA-F]{6}
IDENTIFIER=[a-zA-Z_][a-zA-Z0-9_]*

KEYWORD=("if"|"else"|"for"|"to"|"while"|"var"|"varip"|"import"|"export"|"switch"|"case"|"default"|"continue"|"break"|"return"|"type"|"enum"|"function"|"method"|"strategy"|"indicator"|"library"|"true"|"false"|"na"|"series"|"simple"|"const"|"input")

OPERATOR=("=="|"!="|"<="|">="|"&&"|"||"|"->"|":="|[+\-*/%=!<>?:])
DOT="."
COMMA=","
SEMICOLON=";"
LPAREN="("
RPAREN=")"
LBRACKET="["
RBRACKET="]"
LBRACE="{"
RBRACE="}"

%%

<YYINITIAL> {
  {COMMENT}                                { return PineScriptTokenTypes.COMMENT; }
  {STRING}                                 { return PineScriptTokenTypes.STRING; }
  {NUMBER}                                 { return PineScriptTokenTypes.NUMBER; }
  {COLOR}                                  { return PineScriptTokenTypes.BUILT_IN_VARIABLE; }
  {KEYWORD}                                { return PineScriptTokenTypes.KEYWORD; }
  
  {IDENTIFIER}"("                          { return PineScriptTokenTypes.FUNCTION; }
  
  "math"|"array"|"matrix"|"str"|"color"|"table"|"chart"|"strategy"|"syminfo"|"ta"|"request"|"ticker" { return PineScriptTokenTypes.NAMESPACE; }
  "int"|"float"|"bool"|"string"|"color"|"label"|"line"|"box"|"table" { return PineScriptTokenTypes.TYPE; }
  
  {OPERATOR}                               { return PineScriptTokenTypes.OPERATOR; }
  {DOT}                                   { return PineScriptTokenTypes.DOT; }
  {COMMA}                                 { return PineScriptTokenTypes.COMMA; }
  {SEMICOLON}                             { return PineScriptTokenTypes.SEMICOLON; }
  {LPAREN}                                { return PineScriptTokenTypes.LPAREN; }
  {RPAREN}                                { return PineScriptTokenTypes.RPAREN; }
  {LBRACKET}                              { return PineScriptTokenTypes.LBRACKET; }
  {RBRACKET}                              { return PineScriptTokenTypes.RBRACKET; }
  {LBRACE}                                { return PineScriptTokenTypes.LBRACE; }
  {RBRACE}                                { return PineScriptTokenTypes.RBRACE; }
  
  {IDENTIFIER}                             { return PineScriptTokenTypes.IDENTIFIER; }
  {WHITE_SPACE}+                           { return TokenType.WHITE_SPACE; }
}

[^]                                        { return TokenType.BAD_CHARACTER; } 